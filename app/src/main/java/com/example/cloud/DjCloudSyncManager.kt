package com.example.cloud

import android.content.Context
import android.net.Uri
import com.example.model.AudioItem
import com.example.player.AudioPlayerController
import com.example.player.RepeatOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Owns Android's cross-device sync loop independently of the Cloud settings screen.
 * It continues handling device handoffs while the user returns to the music player.
 */
class DjCloudSyncManager(
    context: Context,
    private val player: AudioPlayerController
) {
    private val client = DjCloudClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var worker: Job? = null
    private var pendingTakeover = false
    private var lastHeartbeatAt = 0L
    private var lastDeviceRefreshAt = 0L
    private var lastSessionCheckAt = 0L

    private val _user = MutableStateFlow<DjCloudUser?>(null)
    val user: StateFlow<DjCloudUser?> = _user.asStateFlow()

    private val _devices = MutableStateFlow<List<DjCloudDevice>>(emptyList())
    val devices: StateFlow<List<DjCloudDevice>> = _devices.asStateFlow()

    private val _playback = MutableStateFlow<DjCloudPlayback?>(null)
    val playback: StateFlow<DjCloudPlayback?> = _playback.asStateFlow()

    private val _status = MutableStateFlow("signed_out")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    val deviceId: String get() = client.deviceId

    fun start() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (isActive) {
                try {
                    if (_user.value == null) {
                        _status.value = "connecting"
                        val signedInUser = client.currentUser()
                        if (signedInUser == null) {
                            _status.value = "signed_out"
                            delay(2_500L)
                            continue
                        }
                        _user.value = signedInUser
                        client.registerDevice()
                        lastHeartbeatAt = 0L
                        lastDeviceRefreshAt = 0L
                        lastSessionCheckAt = System.currentTimeMillis()
                        _status.value = "live"
                        _error.value = null
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastSessionCheckAt > 120_000L) {
                        val sessionUser = client.currentUser()
                        if (sessionUser == null) {
                            _user.value = null
                            _devices.value = emptyList()
                            _playback.value = null
                            _status.value = "signed_out"
                            continue
                        }
                        lastSessionCheckAt = now
                    }

                    val state = client.playbackState()
                    _playback.value = state

                    if (now - lastHeartbeatAt >= 15_000L) {
                        client.heartbeat()
                        lastHeartbeatAt = now
                    }
                    if (now - lastDeviceRefreshAt >= 15_000L) {
                        _devices.value = client.listDevices()
                        lastDeviceRefreshAt = now
                    }

                    processHandoffCommands()

                    val isOwner = state?.activeDeviceId == client.deviceId
                    if (pendingTakeover && isOwner) {
                        pendingTakeover = false
                        applyRemotePlayback(state)
                    }

                    if (isOwner) {
                        client.publishPlayback(createSnapshot())
                    }

                    _status.value = "live"
                    _error.value = null
                    delay(1_800L)
                } catch (failure: Exception) {
                    _status.value = "offline"
                    _error.value = failure.message ?: "Could not sync with DJ Cloud."
                    delay(4_000L)
                }
            }
        }
    }

    suspend fun signIn(email: String, password: String) {
        _status.value = "connecting"
        _error.value = null
        val signedIn = client.signIn(email, password)
        _user.value = signedIn
        lastSessionCheckAt = 0L
        client.registerDevice()
        _status.value = "live"
    }

    suspend fun signUp(name: String, email: String, password: String) {
        _status.value = "connecting"
        _error.value = null
        val signedIn = client.signUp(name, email, password)
        _user.value = signedIn
        lastSessionCheckAt = 0L
        client.registerDevice()
        _status.value = "live"
    }

    suspend fun signOut() {
        client.signOut()
        _user.value = null
        _devices.value = emptyList()
        _playback.value = null
        _status.value = "signed_out"
        pendingTakeover = false
    }

    suspend fun refresh() {
        client.registerDevice()
        _playback.value = client.playbackState()
        _devices.value = client.listDevices()
        _error.value = null
    }

    /**
     * Downloads playable online tracks first, then uploads this device's track metadata.
     * The API strips private file/content URIs before storing them in the cloud.
     */
    suspend fun mergeCloudLibrary(localSongs: List<AudioItem>): List<AudioItem> {
        val remoteRows = client.fetchLibrary()
        val remoteTracks = remoteRows.mapNotNull { row ->
            audioItemFrom(row.optJSONObject("track"))
        }
        if (localSongs.isNotEmpty()) client.saveLibrary(localSongs)
        return remoteTracks
    }

    suspend fun uploadLibrary(localSongs: List<AudioItem>) {
        if (localSongs.isNotEmpty()) client.saveLibrary(localSongs)
    }

    suspend fun takeControl() {
        _error.value = null
        _notice.value = "Requesting playback handoff…"
        val claim = client.claimPlayback()
        if (claim.status == "handoff_pending") {
            pendingTakeover = true
            _notice.value = "Waiting for the current device to stop audio…"
            return
        }
        pendingTakeover = false
        val remote = claim.player
        if (remote != null) applyRemotePlayback(remote)
        _playback.value = client.playbackState()
        _devices.value = client.listDevices()
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun stop() {
        worker?.cancel()
        worker = null
    }

    private suspend fun processHandoffCommands() {
        val commands = client.pendingCommands()
        for (command in commands) {
            if (command.optString("command_type") != "pause") continue
            val commandId = command.optString("command_id")
            if (commandId.isBlank()) continue
            // Stop the old output first. The backend grants ownership to the target only
            // after this device acknowledges that audio has stopped.
            player.pause()
            val position = player.currentPositionMs.coerceAtLeast(0L)
            client.acknowledgePause(commandId, position)
            _notice.value = "Playback stopped here and transferred to another device."
        }
    }

    private suspend fun applyRemotePlayback(remote: DjCloudPlayback) {
        val current = audioItemFrom(remote.currentTrack)
        val queue = buildList {
            for (index in 0 until remote.queue.length()) {
                audioItemFrom(remote.queue.optJSONObject(index))?.let(::add)
            }
        }.distinctBy { it.id }

        if (current == null) {
            player.pause()
            _notice.value = if (remote.currentTrack != null) {
                "The current track exists only as a local file on the other device. Choose an online track or import the same file here."
            } else {
                "This phone now controls playback."
            }
            return
        }

        val playbackQueue = if (queue.any { it.id == current.id }) queue else listOf(current) + queue
        player.play(current, playbackQueue)
        delay(250L)
        if (remote.positionMs > 0L) player.seekTo(remote.positionMs)
        if (!remote.isPlaying) player.pause()
        _notice.value = if (remote.isPlaying) {
            "Playback moved to this phone."
        } else {
            "This phone now controls playback. Press Play to resume."
        }
    }

    private fun createSnapshot(): JSONObject {
        val current = player.currentSong
        val queue = JSONArray()
        player.playlist.forEach { song -> queue.put(trackJson(song)) }

        val playbackMode = when {
            player.isShuffle -> "SHUFFLE"
            player.repeatOption == RepeatOption.ONE -> "REPEAT_ONE"
            player.repeatOption == RepeatOption.ALL -> "REPEAT_ALL"
            else -> "NORMAL"
        }

        return JSONObject()
            .put("current_track", current?.let(::trackJson) ?: JSONObject.NULL)
            .put("queue", queue)
            .put("is_playing", player.isPlaying)
            .put("position_ms", player.currentPositionMs.coerceAtLeast(0L))
            .put("duration_ms", player.durationMs.coerceAtLeast(0L))
            .put("playback_mode", playbackMode)
    }

    private fun trackJson(song: AudioItem): JSONObject =
        JSONObject()
            .put("id", song.id)
            .put("title", song.title)
            .put("artist", song.artist)
            .put("album", song.album)
            .put("duration", song.durationMs)
            .put("uri", song.uri.toString())

    private fun audioItemFrom(track: JSONObject?): AudioItem? {
        track ?: return null
        val uriValue = track.optString("uri")
        if (!uriValue.startsWith("https://") && !uriValue.startsWith("http://")) return null
        val id = track.optString("id").ifBlank { uriValue }
        return AudioItem(
            id = id,
            title = track.optString("title", "Unknown Track"),
            artist = track.optString("artist", "Unknown Artist"),
            album = track.optString("album", "DJ Cloud"),
            durationMs = track.optLong("duration", track.optLong("durationMs", 0L)),
            uri = Uri.parse(uriValue),
            sizeBytes = 0L
        )
    }
}
