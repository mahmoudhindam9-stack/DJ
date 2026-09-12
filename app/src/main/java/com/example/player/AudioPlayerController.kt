package com.example.player

import android.content.Context
import android.media.AudioDeviceInfo
import androidx.annotation.OptIn
import androidx.compose.runtime.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.model.AudioItem
import org.json.JSONArray
import org.json.JSONObject

enum class RepeatOption { OFF, ALL, ONE }

@OptIn(UnstableApi::class)
class AudioPlayerController(private val context: Context) {
    val fxProcessor = DeckFxAudioProcessor().apply { initContext(context) }
    var crossfadeDurationMs by mutableLongStateOf(2000L)

    private val renderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
            return DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(fxProcessor))
                .build()
        }
    }

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context, renderersFactory).build()

    // --- Auto crossfade support -------------------------------------------------
    // A second, hidden player used only to pre-roll the upcoming track underneath
    // the tail of the current one so the transition between songs overlaps
    // instead of just fading the current track to silence.
    private val previewFxProcessor = DeckFxAudioProcessor().apply { initContext(context) }
    private val previewRenderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
            return DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(previewFxProcessor))
                .build()
        }
    }
    private var previewPlayerInstance: ExoPlayer? = null
    private fun ensurePreviewPlayer(): ExoPlayer {
        return previewPlayerInstance ?: ExoPlayer.Builder(context, previewRenderersFactory).build()
            .apply { volume = 0f }
            .also { previewPlayerInstance = it }
    }
    private var crossfadePreparedIndex: Int = -1
    private var isCrossfading = false
    private var crossfadeStartTimeMs = 0L

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
    private var skipNextFadeIn = false

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var lastPersistAt = 0L

    init {
        activeInstance = this
        activePreferredAudioDevice?.let(::setPreferredAudioDevice)

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (isCrossfading) {
                    if (playing) {
                        try { previewPlayerInstance?.play() } catch (e: Exception) {}
                    } else {
                        try { previewPlayerInstance?.pause() } catch (e: Exception) {}
                    }
                }
                persistSession(force = true)
                syncNotificationSafely()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    persistSession(force = true)
                } else if (playbackState == Player.STATE_ENDED) {
                    handleTrackEnded()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                // Crossfade cancellation is now explicitly handled in playNext/Previous/seekTo
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = exoPlayer.currentMediaItemIndex
                if (index in playlist.indices) {
                    currentSongIndex = index
                    currentSong = playlist[index]
                    currentPositionMs = 0L

                    // Crossfade cancellation is now explicitly handled in playNext/Previous/seekTo

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

            PlaybackNotificationRouter.activate(
                context = context,
                source = "player",
                title = currentSong?.title ?: "مشغل الموسيقى",
                artist = currentSong?.artist ?: "موسيقى",
                isPlaying = isPlaying,
                playPause = { togglePlayPause() },
                next = { playNext() },
                previous = { playPrevious() },
                stop = { pause() }
            )
        } catch (e: Throwable) { android.util.Log.w("AudioPlayerController", "Caught throwable", e) }
    }

    fun setQueue(songs: List<AudioItem>, startIndex: Int = 0) {
        stopCrossfadePreview()
        playlist.clear()
        playlist.addAll(songs)
        val items = songs.map { MediaItem.fromUri(it.uri) }
        exoPlayer.setMediaItems(items, startIndex, 0L)
        exoPlayer.prepare()
        currentSongIndex = startIndex
        currentSong = songs.getOrNull(startIndex)
        persistSession(force = true)
    }

    // --- GLOBAL PAUSE MECHANISM ---
    private fun pauseOthers() {
        // إذا تم تشغيل هذا المشغل، يجب إيقاف الـ DJ Decks
        DJDeckController.activeDecks.forEach { it.pause() }
    }

    fun play(song: AudioItem, newQueue: List<AudioItem>? = null) {
        pauseOthers()
        
        val isDifferentQueue = newQueue != null && (
            newQueue.size != playlist.size ||
            newQueue.withIndex().any { (i, item) -> item.uri != playlist[i].uri }
        )

        if (isDifferentQueue) {
            val q = newQueue!!
            val startIndex = q.indexOfFirst { it.uri == song.uri }.takeIf { it >= 0 } ?: 0
            setQueue(q, startIndex)
            applyPreferredAudioDevice()
            exoPlayer.play()
        } else {
            val idx = playlist.indexOfFirst { it.uri == song.uri }
            if (idx >= 0) {
                currentSongIndex = idx
                currentSong = playlist[idx]
                exoPlayer.seekTo(idx, 0L)
                currentPositionMs = 0L
                applyPreferredAudioDevice()
                exoPlayer.play()
            } else {
                val q = newQueue ?: listOf(song)
                val startIndex = q.indexOfFirst { it.uri == song.uri }.takeIf { it >= 0 } ?: 0
                setQueue(q, startIndex)
                applyPreferredAudioDevice()
                exoPlayer.play()
            }
        }
        persistSession(force = true)
        syncNotificationSafely()
    }

    fun pause() {
        exoPlayer.pause()
        try { previewPlayerInstance?.pause() } catch (e: Exception) { android.util.Log.w("AudioPlayerController", "Caught exception", e) }
        persistSession(force = true)
        syncNotificationSafely()
    }

    fun togglePlayPause() {
        if (isPlaying) {
            pause()
        } else {
            pauseOthers()
            applyPreferredAudioDevice()
            exoPlayer.play()
        }
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        
        isCrossfading = false
        stopCrossfadePreview()
        skipNextFadeIn = false

        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNext()
        } else {
            exoPlayer.seekToDefaultPosition(0)
        }
        
        pauseOthers()
        currentPositionMs = 0L
        applyPreferredAudioDevice()
        exoPlayer.play()
        persistSession(force = true)
        syncNotificationSafely()
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return
        
        isCrossfading = false
        stopCrossfadePreview()
        skipNextFadeIn = false

        if (currentPositionMs > 3000L) {
            seekTo(0L)
            return
        }
        
        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPrevious()
        } else {
            exoPlayer.seekToDefaultPosition(0)
        }
        
        pauseOthers()
        currentPositionMs = 0L
        applyPreferredAudioDevice()
        exoPlayer.play()
        persistSession(force = true)
        syncNotificationSafely()
    }

    fun seekTo(positionMs: Long) {
        isCrossfading = false
        stopCrossfadePreview()
        skipNextFadeIn = false

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
        } catch (e: Throwable) { android.util.Log.w("AudioPlayerController", "Caught throwable", e) }
        try {
            previewPlayerInstance?.setPreferredAudioDevice(device)
        } catch (e: Throwable) { android.util.Log.w("AudioPlayerController", "Caught throwable", e) }
    }

    private fun applyPreferredAudioDevice() {
        activePreferredAudioDevice?.let { setPreferredAudioDevice(it) }
    }

    private fun handleTrackEnded() {
        // ExoPlayer handles RepeatOption.ALL and RepeatOption.ONE internally.
        // STATE_ENDED is only reached when RepeatOption.OFF and the playlist ends.
        isPlaying = false
        exoPlayer.seekToDefaultPosition(0)
        persistSession(force = true)
        syncNotificationSafely()
    }

    fun updateProgress() {
        if (exoPlayer.isPlaying || durationMs == 0L || isCrossfading) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val realDuration = exoPlayer.duration
            if (realDuration > 0L) durationMs = realDuration

            if (crossfadeDurationMs > 0L) {
                if (isCrossfading) {
                    val elapsed = android.os.SystemClock.elapsedRealtime() - crossfadeStartTimeMs
                    if (elapsed >= crossfadeDurationMs) {
                        isCrossfading = false
                        stopCrossfadePreview()
                        skipNextFadeIn = true
                        try { exoPlayer.volume = volume } catch (e: Exception) {}
                    } else {
                        val fraction = 1f - (elapsed.toFloat() / crossfadeDurationMs.toFloat()).coerceIn(0f, 1f)
                        val outVol = kotlin.math.sin(fraction * (kotlin.math.PI / 2)).toFloat()
                        val inVol = kotlin.math.cos(fraction * (kotlin.math.PI / 2)).toFloat()
                        try { previewPlayerInstance?.volume = outVol * volume } catch (e: Exception) {}
                        try { exoPlayer.volume = inVol * volume } catch (e: Exception) {}
                    }
                } else if (exoPlayer.isPlaying) {
                    val remaining = durationMs - currentPositionMs
                    if (remaining in 1 until crossfadeDurationMs && exoPlayer.hasNextMediaItem()) {
                        startCrossfade()
                    } else if (!skipNextFadeIn && currentPositionMs < crossfadeDurationMs) {
                        // Manual skip/seek landed near the start of a track: fade it in
                        val fraction = (currentPositionMs.toFloat() / crossfadeDurationMs.toFloat()).coerceIn(0f, 1f)
                        val targetVol = kotlin.math.sin(fraction * (kotlin.math.PI / 2)).toFloat()
                        try { exoPlayer.volume = targetVol * volume } catch (e: Exception) {}
                    } else {
                        if (currentPositionMs >= crossfadeDurationMs) {
                            skipNextFadeIn = false
                        }
                        // Pre-buffer crossfade track for smooth transition
                        if (remaining in crossfadeDurationMs..(crossfadeDurationMs + 5000) && exoPlayer.hasNextMediaItem()) {
                            val currentIndex = exoPlayer.currentMediaItemIndex
                            if (crossfadePreparedIndex != currentIndex) {
                                preBufferCrossfade(currentIndex, durationMs - crossfadeDurationMs)
                            }
                        }
                        try { exoPlayer.volume = volume } catch (e: Exception) {}
                    }
                }
            } else {
                if (isCrossfading) {
                    isCrossfading = false
                    stopCrossfadePreview()
                }
                if (exoPlayer.isPlaying) {
                    try { exoPlayer.volume = volume } catch (e: Exception) {}
                }
            }
            persistSession()
        }
    }

    private fun preBufferCrossfade(index: Int, positionMs: Long) {
        crossfadePreparedIndex = index
        try {
            val preview = ensurePreviewPlayer()
            val song = playlist.getOrNull(index) ?: return
            preview.setMediaItem(androidx.media3.common.MediaItem.fromUri(song.uri))
            preview.seekTo(positionMs)
            preview.volume = 0f
            preview.prepare()
        } catch (e: Exception) {
            crossfadePreparedIndex = -1
        }
    }

    private fun startCrossfade() {
        isCrossfading = true
        crossfadeStartTimeMs = android.os.SystemClock.elapsedRealtime()
        skipNextFadeIn = true

        try {
            val preview = ensurePreviewPlayer()
            val currentIndex = exoPlayer.currentMediaItemIndex
            if (crossfadePreparedIndex != currentIndex) {
                val currentSong = playlist.getOrNull(currentIndex)
                if (currentSong != null) {
                    preview.setMediaItem(androidx.media3.common.MediaItem.fromUri(currentSong.uri))
                    preview.seekTo(exoPlayer.currentPosition)
                    preview.prepare()
                }
            }
            preview.volume = volume
            applyPreferredAudioDevice()
            preview.play()

            exoPlayer.volume = 0f
            exoPlayer.seekToNext()
        } catch (e: Exception) {
            android.util.Log.w("AudioPlayerController", "Caught exception", e)
            isCrossfading = false
        }
        crossfadePreparedIndex = -1
    }

    private fun stopCrossfadePreview() {
        crossfadePreparedIndex = -1
        try {
            previewPlayerInstance?.stop()
            previewPlayerInstance?.clearMediaItems()
        } catch (e: Exception) { android.util.Log.w("AudioPlayerController", "Caught exception", e) }
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
                .putLong("crossfade", crossfadeDurationMs)
                .putString(KEY_TITLE, currentSong?.title ?: "مشغل الموسيقى")
                .putString(KEY_ARTIST, currentSong?.artist ?: "موسيقى")
                .apply()
        } catch (e: Exception) { android.util.Log.w("AudioPlayerController", "Caught exception", e) }
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
            crossfadeDurationMs = prefs.getLong("crossfade", 2000L).coerceIn(0L, 10000L)

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
            if (savedPlaying && canAutoResume) {
                pauseOthers()
                exoPlayer.playWhenReady = true
            }

            applyPreferredAudioDevice()
            syncNotificationSafely()
        } catch (e: Exception) { android.util.Log.w("AudioPlayerController", "Caught exception", e); prefs.edit().clear().apply()
            playlist.clear()
            currentSongIndex = -1
            currentSong = null
            currentPositionMs = 0L }
    }

    var activityCount = 0
    var serviceCount = 0

    fun checkRelease() {
        if (activityCount <= 0 && serviceCount <= 0) {
            release()
        }
    }

    fun release() {
        persistSession(force = true)
        PlaybackNotificationRouter.clear("player")
        try { exoPlayer.setPreferredAudioDevice(null) } catch (e: Throwable) { android.util.Log.w("AudioPlayerController", "Caught throwable", e) }
        if (activeInstance === this) activeInstance = null
        exoPlayer.release()
        try { previewPlayerInstance?.release() } catch (e: Throwable) { android.util.Log.w("AudioPlayerController", "Caught throwable", e) }
        previewPlayerInstance = null
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
