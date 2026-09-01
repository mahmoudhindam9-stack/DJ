package com.example.player

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import androidx.compose.runtime.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.model.AudioItem
import org.json.JSONArray
import org.json.JSONObject

enum class RepeatOption {
    OFF, ALL, ONE
}

class AudioPlayerController(private val context: Context) {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    var playlist = mutableStateListOf<AudioItem>()
        private set
    var currentSongIndex by mutableStateOf(-1)
        private set
    var currentSong by mutableStateOf<AudioItem?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var currentPositionMs by mutableStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var isShuffle by mutableStateOf(false)
        private set
    var repeatOption by mutableStateOf(RepeatOption.OFF)
        private set
    var volume by mutableStateOf(1f)
        private set

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var lastPersistAt = 0L

    init {
        activeInstance = this
        activePreferredAudioDevice?.let(::setPreferredAudioDevice)

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                persistSession(force = true)
                // Do not couple the critical player callback to notification/service startup.
                // The service/widget is synchronized on explicit player actions instead.
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    persistSession(force = true)
                } else if (playbackState == Player.STATE_ENDED) {
                    handleTrackEnded()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = exoPlayer.currentMediaItemIndex
                if (index in playlist.indices) {
                    currentSongIndex = index
                    currentSong = playlist[index]
                    currentPositionMs = 0L
                    persistSession(force = true)
                    syncNotificationSafely()
                }
            }
        })

        restoreSession()
    }

    private fun syncNotificationSafely() {
        try {
            val existingController = MusicService.instance?.playerController
            if (existingController != null && existingController !== this) return
            val serviceIntent = Intent(context, MusicService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            MusicService.instance?.playerController = this
            MusicService.instance?.updateNotification(
                currentSong?.title ?: "مشغل الموسيقى",
                currentSong?.artist ?: "موسيقى",
                isPlaying
            )
        } catch (_: Throwable) {
            // Audio playback must continue even when notification/service integration is unavailable.
        }
    }

    fun setQueue(songs: List<AudioItem>, startIndex: Int = 0) {
        playlist.clear()
        playlist.addAll(songs)
        if (playlist.isNotEmpty() && startIndex in playlist.indices) {
            currentSongIndex = startIndex
            currentSong = playlist[startIndex]
            currentPositionMs = 0L
            exoPlayer.setMediaItems(playlist.map { MediaItem.fromUri(it.uri) }, startIndex, 0L)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = false
            applyPreferredAudioDevice()
            persistSession(force = true)
            syncNotificationSafely()
        }
    }

    fun playSong(song: AudioItem, fullQueue: List<AudioItem> = playlist) {
        if (fullQueue != playlist) {
            playlist.clear()
            playlist.addAll(fullQueue)
        }
        val targetIndex = playlist.indexOfFirst { it.id == song.id }
        if (targetIndex != -1) {
            currentSongIndex = targetIndex
            currentSong = song
            currentPositionMs = 0L
            exoPlayer.setMediaItems(playlist.map { MediaItem.fromUri(it.uri) }, targetIndex, 0L)
            exoPlayer.prepare()
            applyPreferredAudioDevice()
            exoPlayer.play()
            persistSession(force = true)
            syncNotificationSafely()
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.seekTo(0)
            applyPreferredAudioDevice()
            exoPlayer.play()
        }
        persistSession(force = true)
        syncNotificationSafely()
    }

    fun pause() {
        exoPlayer.pause()
        persistSession(force = true)
        syncNotificationSafely()
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        val nextIndex = if (isShuffle) {
            playlist.indices.filter { it != currentSongIndex }.randomOrNull() ?: currentSongIndex
        } else {
            (currentSongIndex + 1) % playlist.size
        }
        currentSongIndex = nextIndex
        currentSong = playlist.getOrNull(currentSongIndex)
        currentSong?.let {
            exoPlayer.seekTo(currentSongIndex, 0L)
            currentPositionMs = 0L
            applyPreferredAudioDevice()
            exoPlayer.play()
            persistSession(force = true)
            syncNotificationSafely()
        }
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return
        if (currentPositionMs > 3000) {
            exoPlayer.seekTo(0)
            currentPositionMs = 0L
            persistSession(force = true)
            syncNotificationSafely()
            return
        }
        currentSongIndex = if (isShuffle) {
            playlist.indices.filter { it != currentSongIndex }.randomOrNull() ?: currentSongIndex
        } else if (currentSongIndex - 1 < 0) {
            playlist.size - 1
        } else {
            currentSongIndex - 1
        }
        currentSong = playlist.getOrNull(currentSongIndex)
        currentSong?.let {
            exoPlayer.seekTo(currentSongIndex, 0L)
            currentPositionMs = 0L
            applyPreferredAudioDevice()
            exoPlayer.play()
            persistSession(force = true)
            syncNotificationSafely()
        }
    }

    fun seekTo(positionMs: Long) {
        val safe = positionMs.coerceAtLeast(0L)
        exoPlayer.seekTo(safe)
        currentPositionMs = safe
        persistSession(force = true)
    }

    fun toggleShuffle() {
        isShuffle = !isShuffle
        exoPlayer.shuffleModeEnabled = isShuffle
        persistSession(force = true)
    }

    fun toggleRepeat() {
        repeatOption = when (repeatOption) {
            RepeatOption.OFF -> RepeatOption.ALL
            RepeatOption.ALL -> RepeatOption.ONE
            RepeatOption.ONE -> RepeatOption.OFF
        }
        exoPlayer.repeatMode = when (repeatOption) {
            RepeatOption.OFF -> Player.REPEAT_MODE_OFF
            RepeatOption.ALL -> Player.REPEAT_MODE_ALL
            RepeatOption.ONE -> Player.REPEAT_MODE_ONE
        }
        persistSession(force = true)
    }

    fun setVolumeLevel(newVolume: Float) {
        volume = newVolume.coerceIn(0f, 1f)
        exoPlayer.volume = volume
        persistSession(force = true)
    }

    @UnstableApi
    fun setPreferredAudioDevice(device: AudioDeviceInfo?) {
        activePreferredAudioDevice = device
        try {
            exoPlayer.setPreferredAudioDevice(device)
        } catch (_: Throwable) {
        }
    }

    private fun applyPreferredAudioDevice() {
        activePreferredAudioDevice?.let { setPreferredAudioDevice(it) }
    }

    private fun handleTrackEnded() {
        when (repeatOption) {
            RepeatOption.ONE -> {
                exoPlayer.seekTo(0)
                currentPositionMs = 0L
                applyPreferredAudioDevice()
                exoPlayer.play()
                persistSession(force = true)
                syncNotificationSafely()
            }
            RepeatOption.ALL -> playNext()
            RepeatOption.OFF -> if (currentSongIndex < playlist.size - 1) {
                playNext()
            } else {
                isPlaying = false
                persistSession(force = true)
                syncNotificationSafely()
            }
        }
    }

    fun updateProgress() {
        if (exoPlayer.isPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (durationMs <= 0L) durationMs = exoPlayer.duration.coerceAtLeast(0L)
            persistSession()
        }
    }

    private fun persistSession(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistAt < 1000L) return
        lastPersistAt = now

        try {
            val queueJson = JSONArray()
            playlist.forEach { song ->
                queueJson.put(
                    JSONObject().apply {
                        put("id", song.id)
                        put("title", song.title)
                        put("artist", song.artist)
                        put("album", song.album)
                        put("durationMs", song.durationMs)
                        put("uri", song.uri.toString())
                        put("sizeBytes", song.sizeBytes)
                    }
                )
            }

            prefs.edit()
                .putString(KEY_QUEUE, queueJson.toString())
                .putInt(KEY_INDEX, currentSongIndex)
                .putLong(KEY_POSITION, exoPlayer.currentPosition.coerceAtLeast(currentPositionMs))
                .putBoolean(KEY_PLAYING, exoPlayer.isPlaying)
                .putBoolean(KEY_SHUFFLE, isShuffle)
                .putString(KEY_REPEAT, repeatOption.name)
                .putFloat(KEY_VOLUME, volume)
                .putString(KEY_TITLE, currentSong?.title ?: "مشغل الموسيقى")
                .putString(KEY_ARTIST, currentSong?.artist ?: "موسيقى")
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun restoreSession() {
        val queueJson = prefs.getString(KEY_QUEUE, null) ?: return
        try {
            val queue = JSONArray(queueJson)
            if (queue.length() == 0) return

            val restored = ArrayList<AudioItem>(queue.length())
            for (i in 0 until queue.length()) {
                val item = queue.getJSONObject(i)
                restored += AudioItem(
                    id = item.optString("id"),
                    title = item.optString("title", "Unknown Track"),
                    artist = item.optString("artist", "Unknown Artist"),
                    album = item.optString("album", "Unknown Album"),
                    durationMs = item.optLong("durationMs", 0L),
                    uri = android.net.Uri.parse(item.optString("uri")),
                    sizeBytes = item.optLong("sizeBytes", 0L)
                )
            }

            playlist.clear()
            playlist.addAll(restored)

            val savedIndex = prefs.getInt(KEY_INDEX, 0).coerceIn(0, restored.lastIndex)
            val savedPosition = prefs.getLong(KEY_POSITION, 0L).coerceAtLeast(0L)
            val savedPlaying = prefs.getBoolean(KEY_PLAYING, false)
            isShuffle = prefs.getBoolean(KEY_SHUFFLE, false)
            repeatOption = prefs.getString(KEY_REPEAT, RepeatOption.OFF.name)
                ?.let { runCatching { RepeatOption.valueOf(it) }.getOrDefault(RepeatOption.OFF) }
                ?: RepeatOption.OFF
            volume = prefs.getFloat(KEY_VOLUME, 1f).coerceIn(0f, 1f)

            currentSongIndex = savedIndex
            currentSong = restored[savedIndex]
            currentPositionMs = savedPosition
            durationMs = currentSong?.durationMs ?: 0L

            exoPlayer.setMediaItems(restored.map { MediaItem.fromUri(it.uri) }, savedIndex, savedPosition)
            exoPlayer.shuffleModeEnabled = isShuffle
            exoPlayer.repeatMode = when (repeatOption) {
                RepeatOption.OFF -> Player.REPEAT_MODE_OFF
                RepeatOption.ALL -> Player.REPEAT_MODE_ALL
                RepeatOption.ONE -> Player.REPEAT_MODE_ONE
            }
            exoPlayer.volume = volume
            exoPlayer.prepare()
            val canAutoResume = MusicService.instance?.playerController == null || MusicService.instance?.playerController === this
            exoPlayer.playWhenReady = savedPlaying && canAutoResume
            applyPreferredAudioDevice()
        } catch (_: Exception) {
            prefs.edit().clear().apply()
            playlist.clear()
            currentSongIndex = -1
            currentSong = null
            currentPositionMs = 0L
        }
    }

    @OptIn(UnstableApi::class)
    fun release() {
        persistSession(force = true)
        try { exoPlayer.setPreferredAudioDevice(null) } catch (_: Throwable) {}
        if (activeInstance === this) activeInstance = null
        exoPlayer.release()
    }

    companion object {
        @JvmStatic
        fun obtain(context: Context): AudioPlayerController {
            return activeInstance
                ?: MusicService.instance?.playerController
                ?: AudioPlayerController(context.applicationContext)
        }

        private const val PREFS_NAME = "dj_player_session"
        private const val KEY_QUEUE = "queue"
        private const val KEY_INDEX = "index"
        private const val KEY_POSITION = "position"
        private const val KEY_PLAYING = "playing"
        private const val KEY_SHUFFLE = "shuffle"
        private const val KEY_REPEAT = "repeat"
        private const val KEY_VOLUME = "volume"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"

        @JvmStatic
        var activeInstance: AudioPlayerController? = null
            private set

        @JvmStatic
        var activePreferredAudioDevice: AudioDeviceInfo? = null
            private set

        @JvmStatic
        @OptIn(UnstableApi::class)
        fun updateGlobalPreferredAudioDevice(device: AudioDeviceInfo?) {
            activePreferredAudioDevice = device
            activeInstance?.setPreferredAudioDevice(device)
        }
    }
}
