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
    private var activeCrossfadeDurationMs = 2000L
    private var pendingStopPreview = false
    private var isHandoverFading = false
    private var handoverStartTimeMs = 0L
    private val handoverDurationMs = 300L

    var playlist = mutableStateListOf<AudioItem>()
        private set
    var baseQueue = mutableListOf<AudioItem>()
        private set
    var shuffleState by mutableStateOf(ShuffleState())
        private set
    var currentSongIndex by mutableStateOf(-1)
        private set
    var currentSong by mutableStateOf<AudioItem?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
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
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    persistSession(force = true)
                } else if (playbackState == Player.STATE_ENDED) {
                    handleTrackEnded()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                isPlaying = false
                isBuffering = false
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                // Crossfade cancellation is now explicitly handled in playNext/Previous/seekTo
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val currentUri = mediaItem?.localConfiguration?.uri
                val index = if (currentUri != null) {
                    playlist.indexOfFirst { it.uri == currentUri }.takeIf { it >= 0 } ?: exoPlayer.currentMediaItemIndex
                } else {
                    exoPlayer.currentMediaItemIndex
                }
                if (index in playlist.indices) {
                    currentSongIndex = index
                    currentSong = playlist[index]
                    currentPositionMs = 0L
                    if (isShuffle) {
                        shuffleState = shuffleState.copy(currentIndex = index)
                    }

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
                title = currentSong?.title ?: "Music Player",
                artist = currentSong?.artist ?: "Music",
                isPlaying = isPlaying,
                playPause = { togglePlayPause() },
                next = { playNext() },
                previous = { playPrevious() },
                stop = { pause() }
            )
        } catch (e: Exception) { android.util.Log.w("AudioPlayerController", "Caught throwable", e) }
    }

    fun setQueue(songs: List<AudioItem>, startIndex: Int = 0) {
        stopCrossfadePreview()
        baseQueue.clear()
        baseQueue.addAll(songs)

        if (isShuffle) {
            val selected = songs.getOrNull(startIndex)
            shuffleState = ShuffleManager.startNewCycle(songs, preferredFirstSong = selected)
            playlist.clear()
            playlist.addAll(shuffleState.currentOrder)
            val activeIndex = shuffleState.currentIndex.coerceAtLeast(0)
            currentSongIndex = activeIndex
            currentSong = shuffleState.currentSong
            val items = shuffleState.currentOrder.map { MediaItem.fromUri(it.uri) }
            exoPlayer.setMediaItems(items, activeIndex, 0L)
            exoPlayer.repeatMode = when (repeatOption) {
                RepeatOption.OFF -> Player.REPEAT_MODE_OFF
                RepeatOption.ALL -> Player.REPEAT_MODE_OFF
                RepeatOption.ONE -> Player.REPEAT_MODE_ONE
            }
            exoPlayer.prepare()
        } else {
            playlist.clear()
            playlist.addAll(songs)
            val items = songs.map { MediaItem.fromUri(it.uri) }
            exoPlayer.setMediaItems(items, startIndex, 0L)
            exoPlayer.repeatMode = when (repeatOption) {
                RepeatOption.OFF -> Player.REPEAT_MODE_OFF
                RepeatOption.ALL -> Player.REPEAT_MODE_ALL
                RepeatOption.ONE -> Player.REPEAT_MODE_ONE
            }
            exoPlayer.prepare()
            currentSongIndex = startIndex
            currentSong = songs.getOrNull(startIndex)
        }
        persistSession(force = true)
    }

    fun startShuffle(songs: List<AudioItem>) {
        if (songs.isEmpty()) return
        pauseOthers()
        stopCrossfadePreview()
        isShuffle = true
        baseQueue.clear()
        baseQueue.addAll(songs)

        shuffleState = ShuffleManager.startNewCycle(songs, preferredFirstSong = null)
        playlist.clear()
        playlist.addAll(shuffleState.currentOrder)
        currentSongIndex = 0
        currentSong = shuffleState.currentSong
        currentPositionMs = 0L

        val items = shuffleState.currentOrder.map { MediaItem.fromUri(it.uri) }
        exoPlayer.setMediaItems(items, 0, 0L)
        exoPlayer.repeatMode = when (repeatOption) {
            RepeatOption.OFF -> Player.REPEAT_MODE_OFF
            RepeatOption.ALL -> Player.REPEAT_MODE_OFF
            RepeatOption.ONE -> Player.REPEAT_MODE_ONE
        }
        exoPlayer.prepare()
        applyPreferredAudioDevice()
        exoPlayer.play()
        persistSession(force = true)
        syncNotificationSafely()
    }

    // --- GLOBAL PAUSE MECHANISM ---
    private fun pauseOthers() {
        // If this player starts, stop the DJ Decks
        DJDeckController.activeDecks.forEach { it.pause() }
    }

    fun playRadio(song: AudioItem, mediaItem: MediaItem) {
        pauseOthers()
        stopCrossfadePreview()
        
        baseQueue.clear()
        baseQueue.add(song)
        playlist.clear()
        playlist.add(song)
        currentSongIndex = 0
        currentSong = song
        
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        applyPreferredAudioDevice()
        exoPlayer.play()
        
        skipNextFadeIn = true
        persistSession(force = true)
        syncNotificationSafely()
    }

    fun play(song: AudioItem, newQueue: List<AudioItem>? = null) {
        pauseOthers()

        val isDifferentQueue = newQueue != null && (
            newQueue.size != baseQueue.size ||
            newQueue.withIndex().any { (i, item) -> item.uri != baseQueue.getOrNull(i)?.uri }
        )

        if (isDifferentQueue) {
            val q = newQueue!!
            baseQueue.clear()
            baseQueue.addAll(q)
            if (isShuffle) {
                shuffleState = ShuffleManager.startNewCycle(q, preferredFirstSong = song)
                playlist.clear()
                playlist.addAll(shuffleState.currentOrder)
                currentSongIndex = 0
                currentSong = shuffleState.currentSong
                val items = shuffleState.currentOrder.map { MediaItem.fromUri(it.uri) }
                exoPlayer.setMediaItems(items, 0, 0L)
                exoPlayer.repeatMode = when (repeatOption) {
                    RepeatOption.OFF -> Player.REPEAT_MODE_OFF
                    RepeatOption.ALL -> Player.REPEAT_MODE_OFF
                    RepeatOption.ONE -> Player.REPEAT_MODE_ONE
                }
                exoPlayer.prepare()
                applyPreferredAudioDevice()
                exoPlayer.play()
            } else {
                val startIndex = q.indexOfFirst { it.uri == song.uri }.takeIf { it >= 0 } ?: 0
                setQueue(q, startIndex)
                applyPreferredAudioDevice()
                exoPlayer.play()
            }
        } else {
            if (isShuffle) {
                val idx = shuffleState.currentOrder.indexOfFirst { it.uri == song.uri }
                if (idx >= 0) {
                    shuffleState = ShuffleManager.jumpToIndex(shuffleState, idx)
                    currentSongIndex = idx
                    currentSong = shuffleState.currentSong
                    exoPlayer.seekTo(idx, 0L)
                    currentPositionMs = 0L
                    applyPreferredAudioDevice()
                    exoPlayer.play()
                } else {
                    val pool = (baseQueue + listOf(song)).distinctBy { it.id.ifEmpty { it.uri.toString() } }
                    baseQueue.clear()
                    baseQueue.addAll(pool)
                    shuffleState = ShuffleManager.startNewCycle(pool, preferredFirstSong = song)
                    playlist.clear()
                    playlist.addAll(shuffleState.currentOrder)
                    currentSongIndex = 0
                    currentSong = shuffleState.currentSong
                    val items = shuffleState.currentOrder.map { MediaItem.fromUri(it.uri) }
                    exoPlayer.setMediaItems(items, 0, 0L)
                    exoPlayer.prepare()
                    applyPreferredAudioDevice()
                    exoPlayer.play()
                }
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

    private fun seekOrLoadMedia(target: AudioItem?, expectedList: List<AudioItem>, expectedIndex: Int, positionMs: Long = 0L) {
        if (target == null) return
        val matchIdx = (0 until exoPlayer.mediaItemCount).firstOrNull { idx ->
            val item = exoPlayer.getMediaItemAt(idx)
            val uri = item.localConfiguration?.uri
            val id = item.mediaId
            uri == target.uri || (target.id.isNotEmpty() && id == target.id)
        }
        if (matchIdx != null) {
            exoPlayer.seekTo(matchIdx, positionMs)
        } else {
            val items = expectedList.map {
                MediaItem.Builder()
                    .setUri(it.uri)
                    .setMediaId(it.id.ifEmpty { it.uri.toString() })
                    .build()
            }
            val safeIdx = expectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            exoPlayer.setMediaItems(items, safeIdx, positionMs)
            exoPlayer.prepare()
        }
    }

    fun playNext() {
        if (playlist.isEmpty()) return

        isCrossfading = false
        stopCrossfadePreview()
        skipNextFadeIn = false

        if (isShuffle) {
            val pool = if (baseQueue.isNotEmpty()) baseQueue else shuffleState.currentOrder
            val oldCycle = shuffleState.cycleNumber
            shuffleState = ShuffleManager.next(shuffleState, pool)
            if (shuffleState.cycleNumber != oldCycle) {
                playlist.clear()
                playlist.addAll(shuffleState.currentOrder)
            }
            currentSongIndex = shuffleState.currentIndex
            currentSong = shuffleState.currentSong
            seekOrLoadMedia(currentSong, shuffleState.currentOrder, currentSongIndex)
        } else {
            val nextIdx = (currentSongIndex + 1).takeIf { it < playlist.size } ?: 0
            currentSongIndex = nextIdx
            currentSong = playlist.getOrNull(nextIdx)
            seekOrLoadMedia(currentSong, playlist.toList(), nextIdx)
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

        if (isShuffle) {
            if (shuffleState.hasPrevious) {
                shuffleState = ShuffleManager.previous(shuffleState)
                currentSongIndex = shuffleState.currentIndex
                currentSong = shuffleState.currentSong
                seekOrLoadMedia(currentSong, shuffleState.currentOrder, currentSongIndex)
            } else {
                seekTo(0L)
            }
        } else {
            val prevIdx = (currentSongIndex - 1).takeIf { it >= 0 } ?: (playlist.size - 1)
            currentSongIndex = prevIdx
            currentSong = playlist.getOrNull(prevIdx)
            seekOrLoadMedia(currentSong, playlist.toList(), prevIdx)
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

    private fun syncShuffleQueuePreservingCurrent(
        currentMediaId: String?,
        currentPosition: Long,
        wasPlaying: Boolean
    ) {
        val expectedSongs = if (isShuffle) shuffleState.currentOrder else baseQueue.toList()
        val items = expectedSongs.map {
            MediaItem.Builder()
                .setUri(it.uri)
                .setMediaId(it.id.ifEmpty { it.uri.toString() })
                .build()
        }

        if (items.isEmpty()) return

        val currentIndex = if (isShuffle) {
            currentSongIndex
        } else {
            val curSong = currentSong
            expectedSongs.indexOfFirst {
                val key = it.id.ifEmpty { it.uri.toString() }
                val curKey = curSong?.let { s -> s.id.ifEmpty { s.uri.toString() } }
                key == curKey || it.uri == curSong?.uri
            }.coerceAtLeast(0)
        }

        if (currentIndex < 0 || currentIndex >= items.size) return

        // Only update the queue when necessary.
        val playerItems = (0 until exoPlayer.mediaItemCount).map { idx ->
            val mi = exoPlayer.getMediaItemAt(idx)
            mi.mediaId.ifBlank { mi.localConfiguration?.uri?.toString().orEmpty() }
        }
        val expectedIds = items.map { it.mediaId }
        val isQueueMatch = playerItems == expectedIds

        if (isQueueMatch) {
            return
        }

        val safePosition = currentPosition.coerceAtLeast(0L)
        exoPlayer.setMediaItems(
            items,
            currentIndex,
            safePosition
        )
        exoPlayer.prepare()

        if (wasPlaying) {
            exoPlayer.play()
        }
    }

    fun toggleShuffle() {
        val wasPlaying = exoPlayer.isPlaying
        val currentMediaId = exoPlayer.currentMediaItem?.mediaId
            ?: exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
            ?: currentSong?.let { it.id.ifEmpty { it.uri.toString() } }
        val currentPosition = exoPlayer.currentPosition

        val newShuffleState = !isShuffle
        isShuffle = newShuffleState

        if (!newShuffleState) {
            // Shuffle OFF:
            // Keep the current player state and current track.
            val pool = if (baseQueue.isNotEmpty()) baseQueue.toList() else playlist.toList()
            val curSong = currentSong
            val targetIdx = pool.indexOfFirst {
                val key = it.id.ifEmpty { it.uri.toString() }
                val curKey = curSong?.let { s -> s.id.ifEmpty { s.uri.toString() } }
                key == curKey || it.uri == curSong?.uri
            }.coerceAtLeast(0)

            playlist.clear()
            playlist.addAll(pool)
            currentSongIndex = targetIdx
            if (curSong != null) {
                currentSong = curSong
            }

            exoPlayer.repeatMode = when (repeatOption) {
                RepeatOption.OFF -> Player.REPEAT_MODE_OFF
                RepeatOption.ALL -> Player.REPEAT_MODE_ALL
                RepeatOption.ONE -> Player.REPEAT_MODE_ONE
            }
            persistSession(force = true)
            syncNotificationSafely()

            syncShuffleQueuePreservingCurrent(
                currentMediaId = currentMediaId,
                currentPosition = currentPosition,
                wasPlaying = wasPlaying
            )
            return
        }

        // Shuffle ON:
        // Do NOT create a new cycle if a valid current non-repeating
        // shuffle cycle already exists.
        val pool = if (baseQueue.isNotEmpty()) baseQueue.toList() else playlist.toList()
        if (baseQueue.isEmpty() && pool.isNotEmpty()) {
            baseQueue.addAll(pool)
        }

        val currentState = shuffleState
        val poolKeys = pool.map { it.id.ifEmpty { it.uri.toString() } }.toSet()
        val cycleKeys = currentState.currentOrder.map { it.id.ifEmpty { it.uri.toString() } }.toSet()
        val isCompatible = poolKeys.isNotEmpty() && poolKeys == cycleKeys

        val validState = currentState.currentOrder.isNotEmpty() &&
            !currentState.isCycleExhausted &&
            isCompatible

        val curSong = currentSong ?: pool.firstOrNull {
            val itKey = it.id.ifEmpty { it.uri.toString() }
            itKey == currentMediaId || it.uri.toString() == currentMediaId
        }

        if (!validState) {
            if (curSong == null && pool.isEmpty()) return

            shuffleState = ShuffleManager.startNewCycle(
                pool = pool,
                preferredFirstSong = curSong,
                cycleNumber = if (currentState.currentOrder.isNotEmpty()) currentState.cycleNumber + 1 else 1,
                lastCompletedSongId = currentState.lastCompletedSongId
                    ?: currentState.currentOrder.lastOrNull()?.let { it.id.ifEmpty { it.uri.toString() } }
            )
        } else if (curSong != null) {
            val songIdxInCycle = currentState.currentOrder.indexOfFirst {
                val key = it.id.ifEmpty { it.uri.toString() }
                val curKey = curSong.id.ifEmpty { curSong.uri.toString() }
                key == curKey || it.uri == curSong.uri
            }
            if (songIdxInCycle >= 0 && songIdxInCycle != currentState.currentIndex) {
                shuffleState = currentState.copy(currentIndex = songIdxInCycle)
            }
        }

        playlist.clear()
        playlist.addAll(shuffleState.currentOrder)
        currentSongIndex = shuffleState.currentIndex.coerceIn(0, (shuffleState.currentOrder.size - 1).coerceAtLeast(0))
        if (curSong != null) {
            currentSong = curSong
        }

        exoPlayer.repeatMode = when (repeatOption) {
            RepeatOption.OFF -> Player.REPEAT_MODE_OFF
            RepeatOption.ALL -> Player.REPEAT_MODE_OFF
            RepeatOption.ONE -> Player.REPEAT_MODE_ONE
        }
        persistSession(force = true)
        syncNotificationSafely()

        // Synchronize queue ONLY if required.
        // Preserve the currently playing track and position.
        syncShuffleQueuePreservingCurrent(
            currentMediaId = currentMediaId,
            currentPosition = currentPosition,
            wasPlaying = wasPlaying
        )
    }

    fun toggleRepeat() {
        repeatOption = when (repeatOption) {
            RepeatOption.OFF -> RepeatOption.ALL
            RepeatOption.ALL -> RepeatOption.ONE
            RepeatOption.ONE -> RepeatOption.OFF
        }
        exoPlayer.repeatMode = when (repeatOption) {
            RepeatOption.OFF -> Player.REPEAT_MODE_OFF
            RepeatOption.ALL -> if (isShuffle) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
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
        } catch (e: Exception) { android.util.Log.w("AudioPlayerController", "Caught throwable", e) }
        try {
            previewPlayerInstance?.setPreferredAudioDevice(device)
        } catch (e: Exception) { android.util.Log.w("AudioPlayerController", "Caught throwable", e) }
    }

    private fun applyPreferredAudioDevice() {
        activePreferredAudioDevice?.let { setPreferredAudioDevice(it) }
    }

    private fun handleTrackEnded() {
        if (repeatOption == RepeatOption.ONE) {
            return
        }
        if (isShuffle) {
            val pool = if (baseQueue.isNotEmpty()) baseQueue else shuffleState.currentOrder
            val oldCycle = shuffleState.cycleNumber
            shuffleState = ShuffleManager.next(shuffleState, pool)
            if (shuffleState.cycleNumber != oldCycle) {
                playlist.clear()
                playlist.addAll(shuffleState.currentOrder)
            }
            currentSongIndex = shuffleState.currentIndex
            currentSong = shuffleState.currentSong
            currentPositionMs = 0L
            seekOrLoadMedia(currentSong, shuffleState.currentOrder, currentSongIndex)
            applyPreferredAudioDevice()
            exoPlayer.play()
            persistSession(force = true)
            syncNotificationSafely()
        } else {
            if (repeatOption == RepeatOption.ALL) {
                val nextIdx = (currentSongIndex + 1).takeIf { it < playlist.size } ?: 0
                currentSongIndex = nextIdx
                currentSong = playlist.getOrNull(nextIdx)
                seekOrLoadMedia(currentSong, playlist.toList(), nextIdx)
                exoPlayer.play()
            } else {
                if (currentSongIndex + 1 < playlist.size) {
                    val nextIdx = currentSongIndex + 1
                    currentSongIndex = nextIdx
                    currentSong = playlist.getOrNull(nextIdx)
                    seekOrLoadMedia(currentSong, playlist.toList(), nextIdx)
                    exoPlayer.play()
                } else {
                    isPlaying = false
                    exoPlayer.seekToDefaultPosition(0)
                }
            }
            persistSession(force = true)
            syncNotificationSafely()
        }
    }

    fun onLibraryUpdated(newLibrary: List<AudioItem>) {
        if (newLibrary.isEmpty()) return

        val validIds = newLibrary.map { it.id.ifEmpty { it.uri.toString() } }.toSet()
        val remainingBase = baseQueue.filter { (it.id.ifEmpty { it.uri.toString() }) in validIds }
        val baseKeys = remainingBase.map { it.id.ifEmpty { it.uri.toString() } }.toSet()
        val addedBase = newLibrary.filter { (it.id.ifEmpty { it.uri.toString() }) !in baseKeys }
        baseQueue.clear()
        baseQueue.addAll(remainingBase + addedBase)

        if (isShuffle) {
            val wasPlaying = exoPlayer.isPlaying
            val curPos = exoPlayer.currentPosition
            shuffleState = ShuffleManager.onLibraryChanged(shuffleState, newLibrary)
            playlist.clear()
            playlist.addAll(shuffleState.currentOrder)
            currentSongIndex = shuffleState.currentIndex
            currentSong = shuffleState.currentSong

            if (shuffleState.currentOrder.isNotEmpty()) {
                val targetIdx = shuffleState.currentIndex.coerceIn(0, shuffleState.currentOrder.lastIndex)
                val items = shuffleState.currentOrder.map { MediaItem.fromUri(it.uri) }
                exoPlayer.setMediaItems(items, targetIdx, curPos)
                if (wasPlaying) exoPlayer.play()
            }
        } else {
            val curSong = currentSong
            val wasPlaying = exoPlayer.isPlaying
            val curPos = exoPlayer.currentPosition
            val remainingPlaylist = playlist.filter { (it.id.ifEmpty { it.uri.toString() }) in validIds }
            val pKeys = remainingPlaylist.map { it.id.ifEmpty { it.uri.toString() } }.toSet()
            val addedPlaylist = newLibrary.filter { (it.id.ifEmpty { it.uri.toString() }) !in pKeys }
            playlist.clear()
            playlist.addAll(remainingPlaylist + addedPlaylist)
            val newIdx = playlist.indexOfFirst { it.id == curSong?.id }.coerceAtLeast(0)
            currentSongIndex = newIdx
            currentSong = playlist.getOrNull(newIdx)
            if (playlist.isNotEmpty()) {
                val items = playlist.map { MediaItem.fromUri(it.uri) }
                exoPlayer.setMediaItems(items, newIdx, curPos)
                if (wasPlaying) exoPlayer.play()
            }
        }
        persistSession(force = true)
    }

    private fun mediaItemFromSong(song: AudioItem): MediaItem {
        return MediaItem.Builder()
            .setUri(song.uri)
            .setMediaId(song.id.ifEmpty { song.uri.toString() })
            .build()
    }

    private fun resolveNextShuffleMediaItem(): MediaItem? {
        val nextSong = resolveNextShuffleSong() ?: return null
        return mediaItemFromSong(nextSong)
    }

    private fun resolveNextShuffleSong(): AudioItem? {
        val state = shuffleState
        if (state.currentOrder.isEmpty()) return null

        return if (state.currentIndex < state.currentOrder.lastIndex) {
            state.currentOrder[state.currentIndex + 1]
        } else {
            val lastSongId = state.currentOrder.lastOrNull()?.let { it.id.ifEmpty { it.uri.toString() } }
            val pool = if (baseQueue.isNotEmpty()) baseQueue.toList() else state.currentOrder.toList()
            val nextCycleOrder = ShuffleManager.generateCycleOrder(
                pool = pool,
                lastCompletedSongId = lastSongId,
                previousOrder = state.currentOrder
            )
            nextCycleOrder.firstOrNull()
        }
    }

    fun updateProgress() {
        if (exoPlayer.isPlaying || durationMs == 0L || isCrossfading || pendingStopPreview || isHandoverFading) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val realDuration = exoPlayer.duration
            if (realDuration > 0L) durationMs = realDuration

            if (isHandoverFading) {
                val elapsed = android.os.SystemClock.elapsedRealtime() - handoverStartTimeMs
                if (elapsed >= handoverDurationMs) {
                    stopCrossfadePreview()
                    isHandoverFading = false
                    try { exoPlayer.volume = volume } catch (e: Exception) {}
                } else {
                    val progress = (elapsed.toFloat() / handoverDurationMs.toFloat()).coerceIn(0f, 1f)
                    val previewVol = volume * (1f - progress)
                    val mainVol = volume * progress
                    try { previewPlayerInstance?.volume = previewVol } catch (e: Exception) {}
                    try { exoPlayer.volume = mainVol } catch (e: Exception) {}
                }
            }

            if (pendingStopPreview) {
                if (exoPlayer.playbackState == Player.STATE_READY && exoPlayer.isPlaying) {
                    isHandoverFading = true
                    handoverStartTimeMs = android.os.SystemClock.elapsedRealtime()
                    pendingStopPreview = false
                }
            }

            if (crossfadeDurationMs > 0L) {
                if (isCrossfading) {
                    val elapsed = android.os.SystemClock.elapsedRealtime() - crossfadeStartTimeMs
                    if (elapsed >= activeCrossfadeDurationMs) {
                        completeCrossfade()
                    } else {
                        val progress = (elapsed.toFloat() / activeCrossfadeDurationMs.toFloat()).coerceIn(0f, 1f)
                        val outVol = volume * (1f - progress)
                        val inVol = volume * progress
                        try { previewPlayerInstance?.volume = inVol } catch (e: Exception) {}
                        try { exoPlayer.volume = outVol } catch (e: Exception) {}
                    }
                } else if (exoPlayer.isPlaying && !exoPlayer.isCurrentMediaItemLive && currentSong?.album != "Live Radio") {
                    val remaining = durationMs - currentPositionMs

                    val currentMediaId = exoPlayer.currentMediaItem?.mediaId
                    val nextSong: AudioItem? = when {
                        isShuffle -> {
                            resolveNextShuffleSong()
                        }
                        currentSongIndex + 1 < playlist.size -> {
                            playlist[currentSongIndex + 1]
                        }
                        repeatOption == RepeatOption.ALL && playlist.isNotEmpty() -> {
                            playlist.first()
                        }
                        else -> null
                    }
                    val nextMediaItem = nextSong?.let { mediaItemFromSong(it) }

                    if (repeatOption != RepeatOption.ONE && remaining in 1 until crossfadeDurationMs && nextMediaItem != null) {
                        startCrossfade(nextMediaItem, remaining)
                    } else if (!skipNextFadeIn && currentPositionMs < crossfadeDurationMs) {
                        val fraction = (currentPositionMs.toFloat() / crossfadeDurationMs.toFloat()).coerceIn(0f, 1f)
                        val targetVol = kotlin.math.sin(fraction * (kotlin.math.PI / 2)).toFloat()
                        try { exoPlayer.volume = targetVol * volume } catch (e: Exception) {}
                    } else {
                        if (currentPositionMs >= crossfadeDurationMs) {
                            skipNextFadeIn = false
                        }
                        if (repeatOption != RepeatOption.ONE && remaining in crossfadeDurationMs..(crossfadeDurationMs + 5000) && nextMediaItem != null) {
                            val currentIndex = exoPlayer.currentMediaItemIndex
                            if (crossfadePreparedIndex != currentIndex) {
                                preBufferCrossfade(nextMediaItem)
                            }
                        }
                        try { exoPlayer.volume = volume } catch (e: Exception) {}
                    }
                }
            } else {
                if (isCrossfading) {
                    completeCrossfade()
                }
                if (exoPlayer.isPlaying) {
                    try { exoPlayer.volume = volume } catch (e: Exception) {}
                }
            }
            persistSession()
        }
    }

    private fun preBufferCrossfade(nextItem: MediaItem) {
        crossfadePreparedIndex = exoPlayer.currentMediaItemIndex
        try {
            val preview = ensurePreviewPlayer()
            preview.setMediaItem(nextItem)
            preview.seekTo(0L)
            preview.volume = 0f
            preview.prepare()
        } catch (e: Exception) {
            crossfadePreparedIndex = -1
        }
    }

    private fun startCrossfade(nextItem: MediaItem, customDurationMs: Long = 0L) {
        val currentMediaId = exoPlayer.currentMediaItem?.mediaId
        val nextId = nextItem.mediaId

        if (currentMediaId == null || nextId == null || currentMediaId == nextId) {
            return
        }

        activeCrossfadeDurationMs = if (customDurationMs > 0L) customDurationMs else crossfadeDurationMs
        isCrossfading = true
        crossfadeStartTimeMs = android.os.SystemClock.elapsedRealtime()
        skipNextFadeIn = true

        try {
            val preview = ensurePreviewPlayer()
            val previewMediaId = preview.currentMediaItem?.mediaId
            if (previewMediaId != nextId) {
                preview.setMediaItem(nextItem)
                preview.seekTo(0L)
                preview.prepare()
            }
            preview.volume = 0f
            applyPreferredAudioDevice()
            preview.play()
        } catch (e: Exception) {
            android.util.Log.w("AudioPlayerController", "Caught exception", e)
            isCrossfading = false
        }
    }

    private fun completeCrossfade() {
        isCrossfading = false
        val preview = previewPlayerInstance
        val previewPosition = preview?.currentPosition ?: 0L

        val previewMediaId = preview?.currentMediaItem?.mediaId

        if (previewMediaId == null) {
            stopCrossfadePreview()
            skipNextFadeIn = true
            persistSession(force = true)
            syncNotificationSafely()
            return
        }

        val curId = currentSong?.id?.ifEmpty { currentSong?.uri?.toString() }
        val isAlreadyTarget = curId != null && (curId == previewMediaId || currentSong?.uri?.toString() == preview.currentMediaItem?.localConfiguration?.uri?.toString())

        if (!isAlreadyTarget) {
            if (isShuffle) {
                val pool = if (baseQueue.isNotEmpty()) baseQueue else shuffleState.currentOrder
                val oldCycle = shuffleState.cycleNumber
                shuffleState = ShuffleManager.next(shuffleState, pool)
                if (shuffleState.cycleNumber != oldCycle) {
                    playlist.clear()
                    playlist.addAll(shuffleState.currentOrder)
                }
                currentSongIndex = shuffleState.currentIndex
                currentSong = shuffleState.currentSong
            } else {
                val nextIdx = (currentSongIndex + 1).takeIf { it < playlist.size } ?: if (repeatOption == RepeatOption.ALL) 0 else -1
                if (nextIdx >= 0) {
                    currentSongIndex = nextIdx
                    currentSong = playlist.getOrNull(currentSongIndex)
                } else {
                    isPlaying = false
                    exoPlayer.volume = volume
                    exoPlayer.pause()
                    stopCrossfadePreview()
                    pendingStopPreview = false
                    skipNextFadeIn = true
                    persistSession(force = true)
                    syncNotificationSafely()
                    return
                }
            }
        }

        exoPlayer.volume = 0f
        seekOrLoadMedia(currentSong, playlist.toList(), currentSongIndex, previewPosition)
        applyPreferredAudioDevice()
        exoPlayer.play()
        pendingStopPreview = true

        skipNextFadeIn = true
        persistSession(force = true)
        syncNotificationSafely()
    }

    private fun stopCrossfadePreview() {
        crossfadePreparedIndex = -1
        isHandoverFading = false
        pendingStopPreview = false
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

            val baseQueueJson = JSONArray()
            baseQueue.forEach { song ->
                baseQueueJson.put(
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
                .putString(KEY_BASE_QUEUE, baseQueueJson.toString())
                .putInt(KEY_INDEX, currentSongIndex)
                .putLong(KEY_POSITION, exoPlayer.currentPosition.coerceAtLeast(currentPositionMs))
                .putBoolean(KEY_PLAYING, exoPlayer.isPlaying)
                .putBoolean(KEY_SHUFFLE, isShuffle)
                .putString(KEY_SHUFFLE_STATE, shuffleState.toJson().toString())
                .putString(KEY_REPEAT, repeatOption.name)
                .putFloat(KEY_VOLUME, volume)
                .putLong("crossfade", crossfadeDurationMs)
                .putString(KEY_TITLE, currentSong?.title ?: "Music Player")
                .putString(KEY_ARTIST, currentSong?.artist ?: "Music")
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

            isShuffle = prefs.getBoolean(KEY_SHUFFLE, false)
            val shuffleStateJsonStr = prefs.getString(KEY_SHUFFLE_STATE, null)
            if (shuffleStateJsonStr != null) {
                runCatching {
                    shuffleState = ShuffleState.fromJson(JSONObject(shuffleStateJsonStr))
                }
            }

            val baseQueueJsonStr = prefs.getString(KEY_BASE_QUEUE, null)
            baseQueue.clear()
            if (baseQueueJsonStr != null) {
                runCatching {
                    val bqArr = JSONArray(baseQueueJsonStr)
                    for (i in 0 until bqArr.length()) {
                        val bqObj = bqArr.getJSONObject(i)
                        baseQueue.add(
                            AudioItem(
                                id = bqObj.optString("id"),
                                title = bqObj.optString("title", "Unknown Track"),
                                artist = bqObj.optString("artist", "Unknown Artist"),
                                album = bqObj.optString("album", "Unknown Album"),
                                durationMs = bqObj.optLong("durationMs", 0L),
                                uri = android.net.Uri.parse(bqObj.optString("uri")),
                                sizeBytes = bqObj.optLong("sizeBytes", 0L)
                            )
                        )
                    }
                }
            }
            if (baseQueue.isEmpty()) {
                baseQueue.addAll(restored)
            }

            repeatOption = prefs.getString(KEY_REPEAT, RepeatOption.OFF.name)
                ?.let { runCatching { RepeatOption.valueOf(it) }.getOrDefault(RepeatOption.OFF) }
                ?: RepeatOption.OFF
            volume = prefs.getFloat(KEY_VOLUME, 1f).coerceIn(0f, 1f)
            crossfadeDurationMs = prefs.getLong("crossfade", 2000L).coerceIn(0L, 10000L)
            val savedPosition = prefs.getLong(KEY_POSITION, 0L).coerceAtLeast(0L)
            val savedPlaying = prefs.getBoolean(KEY_PLAYING, false)

            if (isShuffle && shuffleState.currentOrder.isNotEmpty()) {
                playlist.clear()
                playlist.addAll(shuffleState.currentOrder)
                val savedIndex = shuffleState.currentIndex.coerceIn(0, shuffleState.currentOrder.lastIndex)
                currentSongIndex = savedIndex
                currentSong = shuffleState.currentOrder.getOrNull(savedIndex)
                currentPositionMs = savedPosition
                durationMs = currentSong?.durationMs ?: 0L

                exoPlayer.setMediaItems(shuffleState.currentOrder.map { MediaItem.fromUri(it.uri) }, savedIndex, savedPosition)
                exoPlayer.repeatMode = when (repeatOption) {
                    RepeatOption.OFF -> Player.REPEAT_MODE_OFF
                    RepeatOption.ALL -> Player.REPEAT_MODE_OFF
                    RepeatOption.ONE -> Player.REPEAT_MODE_ONE
                }
                exoPlayer.volume = volume
                exoPlayer.prepare()
            } else {
                playlist.clear()
                playlist.addAll(restored)
                val savedIndex = prefs.getInt(KEY_INDEX, 0).coerceIn(0, restored.lastIndex)
                currentSongIndex = savedIndex
                currentSong = restored.getOrNull(savedIndex)
                currentPositionMs = savedPosition
                durationMs = currentSong?.durationMs ?: 0L

                exoPlayer.setMediaItems(restored.map { MediaItem.fromUri(it.uri) }, savedIndex, savedPosition)
                exoPlayer.repeatMode = when (repeatOption) {
                    RepeatOption.OFF -> Player.REPEAT_MODE_OFF
                    RepeatOption.ALL -> Player.REPEAT_MODE_ALL
                    RepeatOption.ONE -> Player.REPEAT_MODE_ONE
                }
                exoPlayer.volume = volume
                exoPlayer.prepare()
            }

            val canAutoResume = MusicService.instance?.playerController == null || MusicService.instance?.playerController === this
            if (savedPlaying && canAutoResume) {
                pauseOthers()
                exoPlayer.playWhenReady = true
            }

            applyPreferredAudioDevice()
            syncNotificationSafely()
        } catch (e: Exception) {
            android.util.Log.w("AudioPlayerController", "Caught exception", e)
            prefs.edit().clear().apply()
            playlist.clear()
            baseQueue.clear()
            shuffleState = ShuffleState()
            currentSongIndex = -1
            currentSong = null
            currentPositionMs = 0L
        }
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
        try { exoPlayer.setPreferredAudioDevice(null) } catch (e: Exception) { android.util.Log.w("AudioPlayerController", "Caught throwable", e) }
        if (activeInstance === this) activeInstance = null
        exoPlayer.release()
        try { previewPlayerInstance?.release() } catch (e: Exception) { android.util.Log.w("AudioPlayerController", "Caught throwable", e) }
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
        private const val KEY_BASE_QUEUE = "base_queue"
        private const val KEY_SHUFFLE_STATE = "shuffle_state"
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
