package com.example.player

import android.content.Context
import android.media.AudioDeviceInfo
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
    val eqController = EqualizerController(context) { syncEq() }
    var crossfadeDurationMs by mutableLongStateOf(2000L)

    private fun syncEq() {
        val levels = eqController.bands.map { it.currentLevelDb.toFloat() }.toFloatArray()
        fxProcessor.setEqLevels(levels, eqController.isEnabled)
        previewFxProcessor.setEqLevels(levels, eqController.isEnabled)
    }

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
    private var crossfadePreviewIndex: Int = -1

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

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = exoPlayer.currentMediaItemIndex
                if (index in playlist.indices) {
                    currentSongIndex = index
                    currentSong = playlist[index]
                    currentPositionMs = 0L

                    // If this transition is the tail end of an automatic crossfade,
                    // the hidden preview player has already been playing this same
                    // track (from position 0) for roughly `crossfadeDurationMs`.
                    // Capture how far it got *before* we tear it down, so the main
                    // player can pick up from there instead of restarting the song
                    // from 0 and undoing the crossfade the user just heard.
                    val wasAutoCrossfade = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                        crossfadePreviewIndex == index
                    val resumePositionMs = if (wasAutoCrossfade) {
                        (previewPlayerInstance?.currentPosition ?: 0L).coerceAtLeast(0L)
                    } else 0L

                    // The hidden preview player's job ends the moment the main player
                    // itself arrives at that same track (whether it got there via our
                    // auto crossfade or a manual skip/seek).
                    stopCrossfadePreview()
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        // We just completed (or are completing) an automatic
                        // end-of-track crossfade: the volume ramp already handled
                        // the transition, so snap straight to full volume instead
                        // of re-running the manual-skip fade-in below.
                        try { exoPlayer.volume = volume } catch (e: Exception) { android.util.Log.w("AudioPlayerController", "Caught exception", e) }
                        if (wasAutoCrossfade && resumePositionMs > 0L) {
                            // Jump the main player to where the preview left off so
                            // the track continues seamlessly instead of restarting.
                            try { exoPlayer.seekTo(index, resumePositionMs) } catch (e: Exception) { android.util.Log.w("AudioPlayerController", "Caught exception", e) }
                            currentPositionMs = resumePositionMs
                        }
                        skipNextFadeIn = true
                    }
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
        if (isShuffle) {
            currentSongIndex = playlist.indices.random()
        } else {
            currentSongIndex = if (currentSongIndex < playlist.size - 1) currentSongIndex + 1 else 0
        }
        
        currentSong = playlist.getOrNull(currentSongIndex)
        currentSong?.let {
            pauseOthers()
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
        if (currentPositionMs > 3000L) {
            seekTo(0L)
            return
        }
        currentSongIndex = if (currentSongIndex > 0) currentSongIndex - 1 else 0
        currentSong = playlist.getOrNull(currentSongIndex)
        currentSong?.let {
            pauseOthers()
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
        } catch (e: Throwable) { android.util.Log.w("AudioPlayerController", "Caught throwable", e) }
        try {
            previewPlayerInstance?.setPreferredAudioDevice(device)
        } catch (e: Throwable) { android.util.Log.w("AudioPlayerController", "Caught throwable", e) }
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
        if (exoPlayer.isPlaying || durationMs == 0L) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val realDuration = exoPlayer.duration
            if (realDuration > 0L) durationMs = realDuration

            if (crossfadeDurationMs > 0L && exoPlayer.isPlaying) {
                val remaining = durationMs - currentPositionMs
                if (remaining in 1 until crossfadeDurationMs && exoPlayer.hasNextMediaItem()) {
                    // Automatic end-of-track crossfade: overlap the tail of the current
                    // song with the start of the next one instead of just fading to silence.
                    val nextIndex = exoPlayer.nextMediaItemIndex
                    if (crossfadePreviewIndex != nextIndex) {
                        startCrossfadePreview(nextIndex)
                    } else {
                        previewPlayerInstance?.let { if (!it.isPlaying) it.play() }
                    }
                    // Equal-power crossfade curve: fraction goes 1 -> 0 as the window elapses.
                    val fraction = (remaining.toFloat() / crossfadeDurationMs.toFloat()).coerceIn(0f, 1f)
                    val outVol = kotlin.math.sin(fraction * (kotlin.math.PI / 2)).toFloat()
                    val inVol = kotlin.math.cos(fraction * (kotlin.math.PI / 2)).toFloat()
                    try { exoPlayer.volume = outVol * volume } catch (e: Exception) {}
                    try { previewPlayerInstance?.volume = inVol * volume } catch (e: Exception) {}
                } else if (!skipNextFadeIn && currentPositionMs < crossfadeDurationMs) {
                    // Manual skip/seek landed near the start of a track: fade it in
                    // smoothly instead of jumping straight to full volume.
                    if (crossfadePreviewIndex != -1) stopCrossfadePreview()
                    val fraction = (currentPositionMs.toFloat() / crossfadeDurationMs.toFloat()).coerceIn(0f, 1f)
                    val targetVol = kotlin.math.sin(fraction * (kotlin.math.PI / 2)).toFloat()
                    try { exoPlayer.volume = targetVol * volume } catch (e: Exception) {}
                } else {
                    if (crossfadePreviewIndex != -1) stopCrossfadePreview()
                    skipNextFadeIn = false
                    try { exoPlayer.volume = volume } catch (e: Exception) {}
                }
            } else if (exoPlayer.isPlaying) {
                if (crossfadePreviewIndex != -1) stopCrossfadePreview()
                try { exoPlayer.volume = volume } catch (e: Exception) {}
            }

            persistSession()
        }
    }

    /** Starts playing `nextIndex` from the playlist underneath the current track, muted,
     * ready to be faded in as part of an automatic crossfade. */
    private fun startCrossfadePreview(nextIndex: Int) {
        val nextSong = playlist.getOrNull(nextIndex) ?: return
        crossfadePreviewIndex = nextIndex
        try {
            val preview = ensurePreviewPlayer()
            preview.setMediaItem(MediaItem.fromUri(nextSong.uri))
            preview.volume = 0f
            applyPreferredAudioDevice()
            preview.prepare()
            preview.play()
        } catch (e: Exception) { android.util.Log.w("AudioPlayerController", "Caught exception", e); crossfadePreviewIndex = -1 }
    }

    private fun stopCrossfadePreview() {
        if (crossfadePreviewIndex == -1) return
        crossfadePreviewIndex = -1
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
