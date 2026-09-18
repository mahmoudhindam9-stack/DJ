package com.example.player

import com.example.diagnostics.RuntimeDiagnostics

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
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.example.model.AudioItem
import org.json.JSONArray
import org.json.JSONObject

enum class RepeatOption { OFF, ALL, ONE }

@OptIn(UnstableApi::class)
class AudioPlayerController(private val context: Context) {
    val fxProcessor = DeckFxAudioProcessor().apply { initContext(context); diagnosticsLabel = "PLAYER" }
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
    private val previewFxProcessor = DeckFxAudioProcessor().apply { initContext(context); diagnosticsLabel = "CROSSFADE_PREVIEW" }
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
    private var crossfadeTargetSong: AudioItem? = null
    private var crossfadeSourceSong: AudioItem? = null
    private var crossfadeSourceDurationMs: Long = 0L

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

    private data class RadioRecoverySnapshot(
        val baseQueue: List<AudioItem>,
        val playlist: List<AudioItem>,
        val currentSongIndex: Int,
        val positionMs: Long,
        val isShuffle: Boolean,
        val shuffleState: ShuffleState,
        val repeatOption: RepeatOption,
        val wasPlaying: Boolean
    )

    private var radioRecoverySnapshot: RadioRecoverySnapshot? = null

    private fun captureBeforeRadioSwitch() {
        if (currentSong?.album == "Live Radio") return
        if (playlist.isEmpty()) return

        radioRecoverySnapshot = RadioRecoverySnapshot(
            baseQueue = baseQueue.toList(),
            playlist = playlist.toList(),
            currentSongIndex = currentSongIndex,
            positionMs = exoPlayer.currentPosition.coerceAtLeast(currentPositionMs),
            isShuffle = isShuffle,
            shuffleState = shuffleState,
            repeatOption = repeatOption,
            wasPlaying = exoPlayer.isPlaying
        )
    }

    private fun preparePlayerForRecoveryIfNeeded() {
        if (exoPlayer.playerError != null || exoPlayer.playbackState == Player.STATE_IDLE) {
            exoPlayer.prepare()
        }
    }

    fun recoverFromRadioFailure() {
        val snapshot = radioRecoverySnapshot
        radioRecoverySnapshot = null
        stopCrossfadePreview()

        if (snapshot == null || snapshot.playlist.isEmpty()) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            baseQueue.clear()
            playlist.clear()
            currentSongIndex = -1
            currentSong = null
            currentPositionMs = 0L
            durationMs = 0L
            isPlaying = false
            isBuffering = false
            persistSession(force = true)
            syncNotificationSafely()
            return
        }

        baseQueue.clear()
        baseQueue.addAll(snapshot.baseQueue)
        playlist.clear()
        playlist.addAll(snapshot.playlist)
        isShuffle = snapshot.isShuffle
        shuffleState = snapshot.shuffleState
        repeatOption = snapshot.repeatOption

        val safeIndex = snapshot.currentSongIndex.coerceIn(0, playlist.lastIndex)
        currentSongIndex = safeIndex
        currentSong = playlist[safeIndex]
        currentPositionMs = snapshot.positionMs

        exoPlayer.setMediaItems(playlist.map { mediaItemFromSong(it) }, safeIndex, snapshot.positionMs)
        exoPlayer.shuffleModeEnabled = false
        if (isShuffle) {
            applyShuffleOrderToPlayer(shuffleState.currentOrder)
        }

        exoPlayer.repeatMode = when (repeatOption) {
            RepeatOption.OFF -> Player.REPEAT_MODE_OFF
            RepeatOption.ALL -> if (isShuffle) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
            RepeatOption.ONE -> Player.REPEAT_MODE_ONE
        }

        exoPlayer.prepare()
        applyPreferredAudioDevice()
        if (snapshot.wasPlaying) {
            exoPlayer.play()
        }
        persistSession(force = true)
        syncNotificationSafely()
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var lastPersistAt = 0L

    init {
        activeInstance = this
        activePreferredAudioDevice?.let(::setPreferredAudioDevice)

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                RuntimeDiagnostics.recordAction(if (playing) "player_play" else "player_pause")
                persistSession(force = true)
                syncNotificationSafely()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    RuntimeDiagnostics.recordPlaybackState("READY", exoPlayer.isPlaying, isBuffering)
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    persistSession(force = true)
                } else if (playbackState == Player.STATE_ENDED) {
                    RuntimeDiagnostics.recordPlaybackState("ENDED", false, false)
                    handleTrackEnded()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                isPlaying = false
                isBuffering = false
                RuntimeDiagnostics.recordPlayerError(error.message ?: error.errorCodeName, error.stackTraceToString())
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                // Crossfade cancellation is now explicitly handled in playNext/Previous/seekTo
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (isCrossfading && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    completeCrossfade()
                    return
                }
                val currentMediaId = mediaItem?.mediaId?.ifEmpty { mediaItem.localConfiguration?.uri?.toString() }
                val index = if (currentMediaId != null) {
                    playlist.indexOfFirst { it.id.ifEmpty { it.uri.toString() } == currentMediaId }.takeIf { it >= 0 } ?: exoPlayer.currentMediaItemIndex
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
        playlist.clear()
        playlist.addAll(songs)

        val safeIndex = startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0))
        val items = songs.map {
            MediaItem.Builder()
                .setUri(it.uri)
                .setMediaId(it.id.ifEmpty { it.uri.toString() })
                .build()
        }

        exoPlayer.setMediaItems(items, safeIndex, 0L)

        if (isShuffle && songs.size > 1) {
            val selected = songs.getOrNull(safeIndex)
            shuffleState = ShuffleManager.startNewCycle(
                pool = songs,
                preferredFirstSong = selected,
                cycleNumber = shuffleState.cycleNumber + 1,
                lastCompletedSongId = shuffleState.lastCompletedSongId
            )
            applyShuffleOrderToPlayer(shuffleState.currentOrder)
            val currentOriginalIndex = songs.indexOfFirst {
                it.id.ifEmpty { it.uri.toString() } ==
                    selected?.id?.ifEmpty { selected.uri.toString() }
            }.coerceAtLeast(0)
            exoPlayer.seekTo(currentOriginalIndex, 0L)
            currentSongIndex = currentOriginalIndex
            currentSong = selected
        } else {
            exoPlayer.shuffleModeEnabled = false
            currentSongIndex = safeIndex
            currentSong = songs.getOrNull(safeIndex)
        }

        exoPlayer.repeatMode = when (repeatOption) {
            RepeatOption.OFF -> Player.REPEAT_MODE_OFF
            RepeatOption.ALL -> if (isShuffle) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
            RepeatOption.ONE -> Player.REPEAT_MODE_ONE
        }
        exoPlayer.prepare()
        applyPreferredAudioDevice()
        persistSession(force = true)
    }

    /**
     * Replaces the active player queue with radio stations.
     * Keeps the current station/position when requested so adding a station
     * to an already-playing radio queue does not interrupt the current stream.
     */
    fun setRadioQueue(
        songs: List<AudioItem>,
        mediaItems: List<MediaItem>,
        startIndex: Int = 0,
        preserveCurrent: Boolean = true
    ) {
        if (songs.isEmpty() || songs.size != mediaItems.size) return

        val currentMediaId = exoPlayer.currentMediaItem?.mediaId
        val currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
        val wasPlaying = exoPlayer.isPlaying
        val currentIsRadio = currentSong?.album == "Live Radio"

        isShuffle = false
        exoPlayer.shuffleModeEnabled = false
        baseQueue.clear()
        baseQueue.addAll(songs)
        playlist.clear()
        playlist.addAll(songs)

        val preservedIndex = if (preserveCurrent && currentIsRadio && !currentMediaId.isNullOrBlank()) {
            songs.indexOfFirst {
                it.id.ifEmpty { it.uri.toString() } == currentMediaId
            }.takeIf { it >= 0 }
        } else {
            null
        }

        currentPositionMs = if (preservedIndex != null) currentPosition else 0L

        if (preservedIndex != null && currentMediaId != null && exoPlayer.mediaItemCount > 0) {
            val currentPlayerIndex = exoPlayer.currentMediaItemIndex
            if (currentPlayerIndex in 0 until exoPlayer.mediaItemCount &&
                exoPlayer.getMediaItemAt(currentPlayerIndex).mediaId == currentMediaId
            ) {
                if (currentPlayerIndex + 1 < exoPlayer.mediaItemCount) {
                    exoPlayer.removeMediaItems(currentPlayerIndex + 1, exoPlayer.mediaItemCount)
                }
                if (currentPlayerIndex > 0) {
                    exoPlayer.removeMediaItems(0, currentPlayerIndex)
                }

                val before = mediaItems.subList(0, preservedIndex)
                val after = mediaItems.subList(preservedIndex + 1, mediaItems.size)
                if (after.isNotEmpty()) {
                    exoPlayer.addMediaItems(1, after)
                }
                if (before.isNotEmpty()) {
                    exoPlayer.addMediaItems(0, before)
                }

                currentSongIndex = preservedIndex
                currentSong = songs[preservedIndex]
                if (wasPlaying) exoPlayer.play()
                persistSession(force = true)
                syncNotificationSafely()
                return
            }
        }

        if (preserveCurrent && !currentIsRadio && currentMediaId != null) {
            // Queueing radio while music/another source is playing must not interrupt it.
            persistSession(force = true)
            syncNotificationSafely()
            return
        }

        val safeIndex = (startIndex).coerceIn(0, songs.lastIndex)
        currentSongIndex = safeIndex
        currentSong = songs[safeIndex]
        currentPositionMs = if (preserveCurrent) 0L else currentPosition

        exoPlayer.setMediaItems(mediaItems, safeIndex, currentPositionMs)
        exoPlayer.repeatMode = when (repeatOption) {
            RepeatOption.OFF -> Player.REPEAT_MODE_OFF
            RepeatOption.ALL -> Player.REPEAT_MODE_OFF
            RepeatOption.ONE -> Player.REPEAT_MODE_ONE
        }
        exoPlayer.prepare()
        applyPreferredAudioDevice()

        if (wasPlaying || !preserveCurrent) {
            exoPlayer.play()
        }

        persistSession(force = true)
        syncNotificationSafely()
    }

    fun startShuffle(songs: List<AudioItem>) {
        if (songs.isEmpty()) return
        pauseOthers()
        stopCrossfadePreview()

        isShuffle = songs.size > 1
        baseQueue.clear()
        baseQueue.addAll(songs)
        playlist.clear()
        playlist.addAll(songs)

        if (!isShuffle) {
            exoPlayer.shuffleModeEnabled = false
            exoPlayer.setMediaItems(
                songs.map { MediaItem.Builder().setUri(it.uri).setMediaId(it.id.ifEmpty { it.uri.toString() }).build() },
                0,
                0L
            )
            currentSongIndex = 0
            currentSong = songs.first()
            shuffleState = ShuffleState()
        } else {
            shuffleState = ShuffleManager.startNewCycle(
                pool = songs,
                preferredFirstSong = null,
                cycleNumber = shuffleState.cycleNumber + 1,
                lastCompletedSongId = shuffleState.lastCompletedSongId
            )
            exoPlayer.setMediaItems(
                songs.map { MediaItem.Builder().setUri(it.uri).setMediaId(it.id.ifEmpty { it.uri.toString() }).build() },
                0,
                0L
            )
            applyShuffleOrderToPlayer(shuffleState.currentOrder)
            val firstOriginalIndex = songs.indexOfFirst {
                it.id.ifEmpty { it.uri.toString() } ==
                    shuffleState.currentSong?.id?.ifEmpty { shuffleState.currentSong!!.uri.toString() }
            }.coerceAtLeast(0)
            exoPlayer.seekTo(firstOriginalIndex, 0L)
            currentSongIndex = firstOriginalIndex
            currentSong = shuffleState.currentSong
        }

        currentPositionMs = 0L
        exoPlayer.repeatMode = when (repeatOption) {
            RepeatOption.OFF -> Player.REPEAT_MODE_OFF
            RepeatOption.ALL -> if (isShuffle) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
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
        captureBeforeRadioSwitch()
        stopCrossfadePreview()

        baseQueue.clear()
        baseQueue.add(song)
        playlist.clear()
        playlist.add(song)
        currentSongIndex = 0
        currentSong = song
        currentPositionMs = 0L
        durationMs = 0L
        isShuffle = false
        exoPlayer.shuffleModeEnabled = false

        exoPlayer.setMediaItem(mediaItem, 0L)
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
            val startIndex = q.indexOfFirst { it.uri == song.uri }.takeIf { it >= 0 } ?: 0
            setQueue(q, startIndex)
            applyPreferredAudioDevice()
            exoPlayer.play()
        } else if (isShuffle) {
            val idx = playlist.indexOfFirst { it.uri == song.uri }
            if (idx >= 0) {
                shuffleState = ShuffleManager.jumpToIndex(
                    shuffleState,
                    shuffleState.currentOrder.indexOfFirst { it.uri == song.uri }
                        .takeIf { it >= 0 } ?: shuffleState.currentIndex
                )
                currentSongIndex = idx
                currentSong = playlist[idx]
                exoPlayer.seekTo(idx, 0L)
                currentPositionMs = 0L
                applyPreferredAudioDevice()
                preparePlayerForRecoveryIfNeeded()
                exoPlayer.play()
            } else {
                val pool = (baseQueue + listOf(song))
                    .distinctBy { it.id.ifEmpty { it.uri.toString() } }
                val startIndex = pool.indexOfFirst { it.uri == song.uri }.coerceAtLeast(0)
                setQueue(pool, startIndex)
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
        persistSession(force = true)
        syncNotificationSafely()
    }

    fun pause() {
        RuntimeDiagnostics.recordAction("player_pause")
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
            preparePlayerForRecoveryIfNeeded()
            exoPlayer.play()
        }
    }

    private fun seekOrLoadMedia(target: AudioItem?, expectedList: List<AudioItem>, expectedIndex: Int, positionMs: Long = 0L) {
        if (target == null) return
        val playerItems = (0 until exoPlayer.mediaItemCount).map { idx ->
            val item = exoPlayer.getMediaItemAt(idx)
            item.mediaId.ifBlank { item.localConfiguration?.uri?.toString().orEmpty() }
        }
        val expectedIds = expectedList.map { it.id.ifEmpty { it.uri.toString() } }
        val isQueueMatch = playerItems == expectedIds

        if (isQueueMatch) {
            val matchIdx = expectedIndex.coerceIn(0, (expectedList.size - 1).coerceAtLeast(0))
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
            if (exoPlayer.hasNextMediaItem()) {
                exoPlayer.seekToNextMediaItem()
            } else {
                val pool = if (baseQueue.isNotEmpty()) baseQueue.toList() else playlist.toList()
                if (pool.size > 1) {
                    shuffleState = ShuffleManager.next(shuffleState, pool)
                    playlist.clear()
                    playlist.addAll(pool)
                    applyShuffleOrderToPlayer(shuffleState.currentOrder)

                    val nextSong = shuffleState.currentSong
                    val nextKey = nextSong?.let { it.id.ifEmpty { it.uri.toString() } }
                    val nextOriginalIndex = playlist.indexOfFirst {
                        it.id.ifEmpty { it.uri.toString() } == nextKey
                    }.coerceAtLeast(0)
                    currentSongIndex = nextOriginalIndex
                    currentSong = nextSong
                    currentPositionMs = 0L
                    exoPlayer.seekTo(nextOriginalIndex, 0L)
                }
            }
        } else {
            if (exoPlayer.hasNextMediaItem()) {
                exoPlayer.seekToNextMediaItem()
            } else if (playlist.size > 1) {
                val nextIdx = (currentSongIndex + 1).coerceAtMost(playlist.lastIndex)
                currentSongIndex = nextIdx
                currentSong = playlist[nextIdx]
                exoPlayer.seekTo(nextIdx, 0L)
            } else {
                return
            }
            currentPositionMs = 0L
        }

        pauseOthers()
        currentPositionMs = 0L
        applyPreferredAudioDevice()
        preparePlayerForRecoveryIfNeeded()
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
                val previousSong = shuffleState.currentSong
                val previousKey = previousSong?.let { it.id.ifEmpty { it.uri.toString() } }
                val originalIndex = playlist.indexOfFirst {
                    it.id.ifEmpty { it.uri.toString() } == previousKey
                }.coerceAtLeast(0)
                currentSongIndex = originalIndex
                currentSong = previousSong
                exoPlayer.seekTo(originalIndex, 0L)
            } else {
                seekTo(0L)
                return
            }
        } else {
            val prevIdx = (currentSongIndex - 1).takeIf { it >= 0 } ?: (playlist.size - 1)
            currentSongIndex = prevIdx
            currentSong = playlist.getOrNull(prevIdx)
            exoPlayer.seekTo(prevIdx, 0L)
        }

        pauseOthers()
        currentPositionMs = 0L
        applyPreferredAudioDevice()
        preparePlayerForRecoveryIfNeeded()
        exoPlayer.play()
        persistSession(force = true)
        syncNotificationSafely()
    }

    fun seekTo(positionMs: Long) {
        isCrossfading = false
        stopCrossfadePreview()
        skipNextFadeIn = false

        val safe = positionMs.coerceAtLeast(0L)
        preparePlayerForRecoveryIfNeeded()
        exoPlayer.seekTo(safe)
        currentPositionMs = safe
        persistSession(force = true)
    }


    private fun applyShuffleOrderToPlayer(order: List<AudioItem>) {
        if (order.isEmpty() || exoPlayer.mediaItemCount != order.size) return

        val originalIndexes = order.map { song ->
            val key = song.id.ifEmpty { song.uri.toString() }
            playlist.indexOfFirst { it.id.ifEmpty { it.uri.toString() } == key }
        }
        if (originalIndexes.any { it < 0 } || originalIndexes.toSet().size != playlist.size) return

        exoPlayer.setShuffleOrder(
            DefaultShuffleOrder(
                originalIndexes.toIntArray(),
                System.nanoTime()
            )
        )
        exoPlayer.shuffleModeEnabled = true
    }

    fun toggleShuffle() {
        val wasPlaying = exoPlayer.isPlaying
        val currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
        val currentMediaId = exoPlayer.currentMediaItem?.mediaId
            ?: exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()

        val enabling = !isShuffle
        if (!enabling) {
            isShuffle = false
            exoPlayer.shuffleModeEnabled = false

            val currentIndex = playlist.indexOfFirst {
                val key = it.id.ifEmpty { it.uri.toString() }
                key == currentMediaId
            }.takeIf { it >= 0 }
            if (currentIndex != null) {
                currentSongIndex = currentIndex
                currentSong = playlist[currentIndex]
            }

            exoPlayer.repeatMode = when (repeatOption) {
                RepeatOption.OFF -> Player.REPEAT_MODE_OFF
                RepeatOption.ALL -> Player.REPEAT_MODE_ALL
                RepeatOption.ONE -> Player.REPEAT_MODE_ONE
            }
            persistSession(force = true)
            syncNotificationSafely()
            return
        }

        val pool = when {
            baseQueue.size > 1 -> baseQueue.toList()
            playlist.size > 1 -> playlist.toList()
            else -> emptyList()
        }
        if (pool.size < 2) {
            isShuffle = false
            exoPlayer.shuffleModeEnabled = false
            persistSession(force = true)
            syncNotificationSafely()
            return
        }

        val currentKey = currentMediaId
        val curSong = pool.firstOrNull {
            val key = it.id.ifEmpty { it.uri.toString() }
            key == currentKey || it.uri.toString() == currentKey
        } ?: currentSong?.takeIf { candidate ->
            pool.any { it.id.ifEmpty { it.uri.toString() } == candidate.id.ifEmpty { candidate.uri.toString() } }
        } ?: pool.first()

        playlist.clear()
        playlist.addAll(pool)
        baseQueue.clear()
        baseQueue.addAll(pool)

        shuffleState = ShuffleManager.startNewCycle(
            pool = pool,
            preferredFirstSong = curSong,
            cycleNumber = shuffleState.cycleNumber + 1,
            lastCompletedSongId = shuffleState.lastCompletedSongId
        )

        isShuffle = true
        applyShuffleOrderToPlayer(shuffleState.currentOrder)

        val originalCurrentIndex = playlist.indexOfFirst {
            it.id.ifEmpty { it.uri.toString() } == curSong.id.ifEmpty { curSong.uri.toString() }
        }.coerceAtLeast(0)
        currentSongIndex = originalCurrentIndex
        currentSong = curSong
        shuffleState = shuffleState.copy(currentIndex = 0)
        exoPlayer.shuffleModeEnabled = true

        exoPlayer.repeatMode = when (repeatOption) {
            RepeatOption.OFF -> Player.REPEAT_MODE_OFF
            RepeatOption.ALL -> Player.REPEAT_MODE_OFF
            RepeatOption.ONE -> Player.REPEAT_MODE_ONE
        }

        // Do not rebuild the media items: enabling shuffle must never restart the current song.
        if (wasPlaying) exoPlayer.play()
        currentPositionMs = currentPosition
        persistSession(force = true)
        syncNotificationSafely()
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
        if (isCrossfading) {
            completeCrossfade()
            return
        }
        if (repeatOption == RepeatOption.ONE) {
            return
        }

        if (isShuffle) {
            val pool = if (baseQueue.isNotEmpty()) baseQueue.toList() else playlist.toList()
            if (pool.size > 1) {
                shuffleState = ShuffleManager.next(shuffleState, pool)
                playlist.clear()
                playlist.addAll(pool)
                applyShuffleOrderToPlayer(shuffleState.currentOrder)

                val nextSong = shuffleState.currentSong
                val nextKey = nextSong?.let { it.id.ifEmpty { it.uri.toString() } }
                val nextOriginalIndex = playlist.indexOfFirst {
                    it.id.ifEmpty { it.uri.toString() } == nextKey
                }.coerceAtLeast(0)
                currentSongIndex = nextOriginalIndex
                currentSong = nextSong
                currentPositionMs = 0L
                exoPlayer.seekTo(nextOriginalIndex, 0L)
                applyPreferredAudioDevice()
                exoPlayer.play()
            } else {
                isPlaying = false
            }
            persistSession(force = true)
            syncNotificationSafely()
            return
        }

        if (repeatOption == RepeatOption.ALL) {
            val nextIdx = (currentSongIndex + 1).takeIf { it < playlist.size } ?: 0
            currentSongIndex = nextIdx
            currentSong = playlist.getOrNull(nextIdx)
            seekOrLoadMedia(currentSong, playlist.toList(), nextIdx)
            exoPlayer.play()
        } else if (currentSongIndex + 1 < playlist.size) {
            val nextIdx = currentSongIndex + 1
            currentSongIndex = nextIdx
            currentSong = playlist.getOrNull(nextIdx)
            seekOrLoadMedia(currentSong, playlist.toList(), nextIdx)
            exoPlayer.play()
        } else {
            isPlaying = false
            exoPlayer.seekToDefaultPosition(0)
        }

        persistSession(force = true)
        syncNotificationSafely()
    }

    fun onLibraryUpdated(newLibrary: List<AudioItem>) {
        if (newLibrary.isEmpty()) return

        val distinctLibrary = newLibrary.distinctBy { it.id.ifEmpty { it.uri.toString() } }
        val validIds = distinctLibrary.map { it.id.ifEmpty { it.uri.toString() } }.toSet()
        val wasPlaying = exoPlayer.isPlaying
        val curPos = exoPlayer.currentPosition
        val curKey = currentSong?.let { it.id.ifEmpty { it.uri.toString() } }

        val remainingBase = baseQueue.filter { (it.id.ifEmpty { it.uri.toString() }) in validIds }
        val baseKeys = remainingBase.map { it.id.ifEmpty { it.uri.toString() } }.toSet()
        val addedBase = distinctLibrary.filter { (it.id.ifEmpty { it.uri.toString() }) !in baseKeys }
        baseQueue.clear()
        baseQueue.addAll(remainingBase + addedBase)

        if (isShuffle) {
            shuffleState = ShuffleManager.onLibraryChanged(shuffleState, distinctLibrary)
            playlist.clear()
            playlist.addAll(distinctLibrary)

            if (shuffleState.currentOrder.isNotEmpty()) {
                applyShuffleOrderToPlayer(shuffleState.currentOrder)
                val currentKey = shuffleState.currentSong?.let { it.id.ifEmpty { it.uri.toString() } } ?: curKey
                val currentOriginalIndex = playlist.indexOfFirst {
                    it.id.ifEmpty { it.uri.toString() } == currentKey
                }.coerceAtLeast(0)
                currentSongIndex = currentOriginalIndex
                currentSong = playlist.getOrNull(currentOriginalIndex)
                exoPlayer.seekTo(currentOriginalIndex, curPos)
                if (wasPlaying) exoPlayer.play()
            }
        } else {
            val remainingPlaylist = playlist.filter { (it.id.ifEmpty { it.uri.toString() }) in validIds }
            val pKeys = remainingPlaylist.map { it.id.ifEmpty { it.uri.toString() } }.toSet()
            val addedPlaylist = distinctLibrary.filter { (it.id.ifEmpty { it.uri.toString() }) !in pKeys }
            playlist.clear()
            playlist.addAll(remainingPlaylist + addedPlaylist)

            val newIdx = playlist.indexOfFirst { it.id.ifEmpty { it.uri.toString() } == curKey }.coerceAtLeast(0)
            currentSongIndex = newIdx
            currentSong = playlist.getOrNull(newIdx)
            if (playlist.isNotEmpty()) {
                val items = playlist.map { mediaItemFromSong(it) }
                exoPlayer.setMediaItems(items, newIdx, curPos)
                exoPlayer.shuffleModeEnabled = false
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
                    val preview = previewPlayerInstance
                    val previewDuration = preview?.duration?.takeIf { it > 0L } ?: crossfadeSourceDurationMs
                    val sourceRemaining = if (preview != null && previewDuration > 0L) {
                        (previewDuration - preview.currentPosition).coerceAtLeast(0L)
                    } else {
                        (activeCrossfadeDurationMs - elapsed).coerceAtLeast(0L)
                    }

                    // Follow the real source-player position instead of a wall-clock-only
                    // timer so decoder/startup latency cannot cause a tiny cut at source end.
                    if (sourceRemaining <= 30L ||
                        (previewDuration <= 0L && elapsed >= activeCrossfadeDurationMs + 750L)
                    ) {
                        completeCrossfade()
                    } else {
                        if (previewDuration > 0L) {
                            crossfadeSourceDurationMs = previewDuration
                        }
                        val fadeWindow = activeCrossfadeDurationMs.coerceAtLeast(1L)
                        val progress = ((fadeWindow - sourceRemaining).toFloat() / fadeWindow.toFloat())
                            .coerceIn(0f, 1f)
                        val sourceVol = volume * (1f - progress)
                        val targetVol = volume * progress
                        try { previewPlayerInstance?.volume = sourceVol } catch (e: Exception) {}
                        try { exoPlayer.volume = targetVol } catch (e: Exception) {}
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

                    if (repeatOption != RepeatOption.ONE && remaining in 1..crossfadeDurationMs && nextSong != null) {
                        startCrossfade(nextSong, remaining)
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
        val sourceSong = currentSong ?: return
        val sourceItem = mediaItemFromSong(sourceSong)
        try {
            val preview = ensurePreviewPlayer()
            val sameItem = preview.currentMediaItem?.mediaId == sourceItem.mediaId
            if (!sameItem) {
                preview.setMediaItem(sourceItem)
                preview.seekTo(exoPlayer.currentPosition.coerceAtLeast(0L))
                preview.prepare()
            } else if (preview.playbackState != Player.STATE_READY) {
                preview.seekTo(exoPlayer.currentPosition.coerceAtLeast(0L))
                preview.prepare()
            } else {
                preview.seekTo(exoPlayer.currentPosition.coerceAtLeast(0L))
            }
            preview.volume = 0f
        } catch (e: Exception) {
            crossfadePreparedIndex = -1
        }
    }

    private fun startCrossfade(nextSong: AudioItem, customDurationMs: Long = 0L) {
        val sourceSong = currentSong ?: return
        val currentMediaId = exoPlayer.currentMediaItem?.mediaId
        val nextItem = mediaItemFromSong(nextSong)
        val nextId = nextItem.mediaId

        if (currentMediaId == null || currentMediaId == nextId) return

        var targetIndex = playlist.indexOfFirst {
            it.id.ifEmpty { it.uri.toString() } == nextSong.id.ifEmpty { nextSong.uri.toString() }
        }

        if (isShuffle && targetIndex < 0) {
            val pool = if (baseQueue.isNotEmpty()) baseQueue.toList() else shuffleState.currentOrder
            val lastSongId = shuffleState.currentOrder.lastOrNull()?.let {
                it.id.ifEmpty { it.uri.toString() }
            }
            val nextCycleOrder = ShuffleManager.generateCycleOrder(
                pool = pool,
                lastCompletedSongId = lastSongId,
                previousOrder = shuffleState.currentOrder
            )
            shuffleState = shuffleState.copy(
                currentOrder = nextCycleOrder,
                currentIndex = 0,
                cycleNumber = shuffleState.cycleNumber + 1,
                lastCompletedSongId = lastSongId
            )
            playlist.clear()
            playlist.addAll(pool)
            applyShuffleOrderToPlayer(shuffleState.currentOrder)
            targetIndex = playlist.indexOfFirst {
                it.id.ifEmpty { it.uri.toString() } == nextSong.id.ifEmpty { nextSong.uri.toString() }
            }
        }

        if (targetIndex < 0) return

        val oldPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
        val duration = if (customDurationMs > 0L) customDurationMs else crossfadeDurationMs

        crossfadeSourceSong = sourceSong
        crossfadeTargetSong = nextSong
        activeCrossfadeDurationMs = duration
        crossfadeSourceDurationMs = sourceSong.durationMs.coerceAtLeast(0L)
        isCrossfading = true
        crossfadeStartTimeMs = android.os.SystemClock.elapsedRealtime()
        skipNextFadeIn = true

        RuntimeDiagnostics.recordCrossfadeStarted(
            sourceSong.title,
            nextSong.title,
            activeCrossfadeDurationMs
        )

        try {
            // The old/current song becomes the background stream.
            val preview = ensurePreviewPlayer()
            val sourceItem = mediaItemFromSong(sourceSong)
            val sameItem = preview.currentMediaItem?.mediaId == sourceItem.mediaId
            if (!sameItem) {
                preview.setMediaItem(sourceItem)
                preview.seekTo(oldPosition)
                preview.prepare()
            } else {
                preview.seekTo(oldPosition)
                if (preview.playbackState != Player.STATE_READY) {
                    preview.prepare()
                }
            }
            if (preview.duration > 0L) {
                crossfadeSourceDurationMs = preview.duration
            }
            preview.volume = volume
            preview.play()

            // The next song becomes the main player immediately without replacing
            // the whole playlist, so shuffle order and queued media remain intact.
            currentSongIndex = targetIndex
            currentSong = nextSong
            if (isShuffle) {
                val shuffleIndex = shuffleState.currentOrder.indexOfFirst {
                    it.id.ifEmpty { it.uri.toString() } ==
                        nextSong.id.ifEmpty { nextSong.uri.toString() }
                }
                if (shuffleIndex >= 0) {
                    shuffleState = shuffleState.copy(currentIndex = shuffleIndex)
                }
            }
            currentPositionMs = 0L

            exoPlayer.seekTo(targetIndex, 0L)
            exoPlayer.repeatMode = when (repeatOption) {
                RepeatOption.OFF -> Player.REPEAT_MODE_OFF
                RepeatOption.ALL -> if (isShuffle) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
                RepeatOption.ONE -> Player.REPEAT_MODE_ONE
            }
            exoPlayer.volume = 0f
            applyPreferredAudioDevice()
            exoPlayer.play()

            pendingStopPreview = false
            isHandoverFading = false
            persistSession(force = true)
            syncNotificationSafely()
        } catch (e: Exception) {
            android.util.Log.w("AudioPlayerController", "Crossfade start failed", e)
            RuntimeDiagnostics.recordCrossfadeFailure(e.stackTraceToString())
            isCrossfading = false
            crossfadeSourceSong = null
            crossfadeTargetSong = null
            try { exoPlayer.volume = volume } catch (_: Exception) {}
            stopCrossfadePreview()
        }
    }

    private fun completeCrossfade() {
        val actualDuration =
            if (crossfadeStartTimeMs > 0L) {
                android.os.SystemClock.elapsedRealtime() - crossfadeStartTimeMs
            } else {
                0L
            }

        RuntimeDiagnostics.recordCrossfadeCompleted(
            crossfadeSourceSong?.title ?: "unknown",
            crossfadeTargetSong?.title ?: currentSong?.title ?: "unknown",
            activeCrossfadeDurationMs,
            actualDuration
        )

        isCrossfading = false
        pendingStopPreview = false
        isHandoverFading = false

        try { previewPlayerInstance?.volume = 0f } catch (_: Exception) {}
        stopCrossfadePreview()

        try { exoPlayer.volume = volume } catch (_: Exception) {}

        crossfadeSourceSong = null
        crossfadeTargetSong = null
        crossfadeSourceDurationMs = 0L
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
                playlist.addAll(restored)

                val currentKey = shuffleState.currentSong?.id?.ifEmpty { shuffleState.currentSong?.uri?.toString() }
                val savedOriginalIndex = restored.indexOfFirst {
                    it.id.ifEmpty { it.uri.toString() } == currentKey
                }.takeIf { it >= 0 } ?: prefs.getInt(KEY_INDEX, 0).coerceIn(0, restored.lastIndex)

                currentSongIndex = savedOriginalIndex
                currentSong = restored.getOrNull(savedOriginalIndex)
                currentPositionMs = savedPosition
                durationMs = currentSong?.durationMs ?: 0L

                val items = restored.map {
                    MediaItem.Builder()
                        .setUri(it.uri)
                        .setMediaId(it.id.ifEmpty { it.uri.toString() })
                        .build()
                }
                exoPlayer.setMediaItems(items, savedOriginalIndex, savedPosition)

                val validShuffle = shuffleState.currentOrder.mapNotNull { song ->
                    val key = song.id.ifEmpty { song.uri.toString() }
                    restored.indexOfFirst { it.id.ifEmpty { it.uri.toString() } == key }
                        .takeIf { it >= 0 }
                }
                if (validShuffle.size == restored.size && validShuffle.distinct().size == restored.size) {
                    exoPlayer.setShuffleOrder(DefaultShuffleOrder(validShuffle.toIntArray(), System.nanoTime()))
                    exoPlayer.shuffleModeEnabled = true
                } else {
                    exoPlayer.shuffleModeEnabled = true
                }

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

                exoPlayer.setMediaItems(restored.map { MediaItem.Builder().setUri(it.uri).setMediaId(it.id.ifEmpty { it.uri.toString() }).build() }, savedIndex, savedPosition)
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
