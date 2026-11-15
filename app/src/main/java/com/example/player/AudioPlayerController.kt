package com.example.player

import com.example.diagnostics.RuntimeDiagnostics

import android.content.Context
import android.media.AudioDeviceInfo
import androidx.annotation.OptIn
import androidx.compose.runtime.*
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import com.example.visualizer.AudioVisualizerEngine
import com.example.visualizer.VisualizerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

enum class RepeatOption { OFF, ALL, ONE }

@OptIn(UnstableApi::class)
class AudioPlayerController(private val context: Context) {
    val visualizerEngine = AudioVisualizerEngine()
    var visualizerMode by mutableStateOf(VisualizerMode.SPECTRUM)

    val fxProcessor = DeckFxAudioProcessor().apply { initContext(context); diagnosticsLabel = "PLAYER" }
    var crossfadeDurationMs by mutableLongStateOf(2000L)

    private val renderersFactory = object : DefaultRenderersFactory(context) {
        init {
            setEnableDecoderFallback(true)
        }
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
            return DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(fxProcessor, visualizerEngine.audioProcessor))
                .build()
        }
    }

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context, renderersFactory).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            false
        )
    }

    // --- Auto crossfade support -------------------------------------------------
    // A second, hidden player used only to pre-roll the upcoming track underneath
    // the tail of the current one so the transition between songs overlaps
    // instead of just fading the current track to silence.
    private val previewFxProcessor = DeckFxAudioProcessor().apply { initContext(context); diagnosticsLabel = "CROSSFADE_PREVIEW" }
    private val previewRenderersFactory = object : DefaultRenderersFactory(context) {
        init {
            setEnableDecoderFallback(true)
        }
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
            .apply {
                volume = 0f
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    false
                )
            }
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
    var playbackErrorMessage by mutableStateOf<String?>(null)
        private set
    private var skipNextFadeIn = false

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var resolutionJob: Job? = null
    private var preResolveJob: Job? = null
    @Volatile private var resolutionToken = 0L

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
        // Ensure saved/default Equalizer state is loaded into GlobalEqualizerState immediately,
        // even if MusicService starts before EqualizerScreen is ever opened.
        runCatching { EqualizerController.obtain(context.applicationContext) }
        activePreferredAudioDevice?.let(::setPreferredAudioDevice)

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                visualizerEngine.isPlaying = playing
                RuntimeDiagnostics.recordAction(if (playing) "player_play" else "player_pause")
                persistSession(force = true)
                syncNotificationSafely()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    playbackErrorMessage = null
                    RuntimeDiagnostics.recordPlaybackState("READY", exoPlayer.isPlaying, isBuffering)
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    persistSession(force = true)
                    preResolveUpcomingTrack()
                } else if (playbackState == Player.STATE_ENDED) {
                    RuntimeDiagnostics.recordPlaybackState("ENDED", false, false)
                    handleTrackEnded()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val active = currentSong
                if (active != null && active.album == "Live Radio") {
                    val token = resolutionToken
                    controllerScope.launch {
                        val fallback = UnifiedStreamResolver.resolveNextRadioFallback(active)
                        if (token == resolutionToken && fallback != null) {
                            updateResolvedItemInQueue(fallback)
                            val idx = currentSongIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))
                            if (idx < exoPlayer.mediaItemCount) {
                                exoPlayer.replaceMediaItem(idx, mediaItemFromSong(fallback))
                            }
                            exoPlayer.seekTo(idx, 0L)
                            exoPlayer.prepare()
                            applyPreferredAudioDevice()
                            exoPlayer.play()
                            return@launch
                        }
                        isPlaying = false
                        isBuffering = false
                        playbackErrorMessage = error.message ?: error.errorCodeName
                        RuntimeDiagnostics.recordPlayerError(error.message ?: error.errorCodeName, error.stackTraceToString())
                    }
                    return
                }
                isPlaying = false
                isBuffering = false
                playbackErrorMessage = error.message ?: error.errorCodeName
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
                    playlist.indexOfFirst { UnifiedPlaybackQueue.stableKey(it) == currentMediaId }.takeIf { it >= 0 } ?: exoPlayer.currentMediaItemIndex
                } else {
                    exoPlayer.currentMediaItemIndex
                }
                if (index in playlist.indices) {
                    val transitioned = playlist[index]
                    currentSongIndex = index
                    currentSong = transitioned
                    currentPositionMs = 0L
                    if (isShuffle) {
                        val shuffleIdx = shuffleState.currentOrder.indexOfFirst {
                            UnifiedPlaybackQueue.stableKey(it) == UnifiedPlaybackQueue.stableKey(transitioned)
                        }
                        if (shuffleIdx >= 0) {
                            shuffleState = shuffleState.copy(currentIndex = shuffleIdx)
                        }
                    }

                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                        UnifiedStreamResolver.requiresResolution(transitioned)
                    ) {
                        playIndexWithResolution(index)
                        return
                    }

                    preResolveUpcomingTrack()
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
        val deduped = UnifiedPlaybackQueue.deduplicate(songs).map {
            UnifiedStreamResolver.getCached(it) ?: it
        }
        stopCrossfadePreview()
        baseQueue.clear()
        baseQueue.addAll(deduped)
        playlist.clear()
        playlist.addAll(deduped)

        val safeIndex = startIndex.coerceIn(0, (deduped.size - 1).coerceAtLeast(0))
        val items = deduped.map { mediaItemFromSong(it) }

        exoPlayer.setMediaItems(items, safeIndex, 0L)

        if (isShuffle && deduped.size > 1) {
            val selected = deduped.getOrNull(safeIndex)
            shuffleState = ShuffleManager.startNewCycle(
                pool = deduped,
                preferredFirstSong = selected,
                cycleNumber = shuffleState.cycleNumber + 1,
                lastCompletedSongId = shuffleState.lastCompletedSongId
            )
            applyShuffleOrderToPlayer(shuffleState.currentOrder)
            val currentOriginalIndex = deduped.indexOfFirst {
                UnifiedPlaybackQueue.stableKey(it) == selected?.let(UnifiedPlaybackQueue::stableKey)
            }.coerceAtLeast(0)
            exoPlayer.seekTo(currentOriginalIndex, 0L)
            currentSongIndex = currentOriginalIndex
            currentSong = selected
        } else {
            exoPlayer.shuffleModeEnabled = false
            currentSongIndex = safeIndex
            currentSong = deduped.getOrNull(safeIndex)
        }

        exoPlayer.repeatMode = when (repeatOption) {
            RepeatOption.OFF -> Player.REPEAT_MODE_OFF
            RepeatOption.ALL -> if (isShuffle) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
            RepeatOption.ONE -> Player.REPEAT_MODE_ONE
        }
        val active = currentSong
        if (active == null || !UnifiedStreamResolver.requiresResolution(active)) {
            exoPlayer.prepare()
        }
        applyPreferredAudioDevice()
        persistSession(force = true)
    }

    fun syncPlaylistShuffleIfNeeded() {
        if (!isShuffle || playlist.size <= 1) return
        val cur = currentSong
        shuffleState = ShuffleManager.startNewCycle(
            pool = playlist.toList(),
            preferredFirstSong = cur,
            cycleNumber = shuffleState.cycleNumber + 1,
            lastCompletedSongId = shuffleState.lastCompletedSongId
        )
        applyShuffleOrderToPlayer(shuffleState.currentOrder)
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
        val deduped = UnifiedPlaybackQueue.deduplicate(songs).map {
            UnifiedStreamResolver.getCached(it) ?: it
        }
        if (deduped.isEmpty()) return
        pauseOthers()
        stopCrossfadePreview()

        isShuffle = deduped.size > 1
        baseQueue.clear()
        baseQueue.addAll(deduped)
        playlist.clear()
        playlist.addAll(deduped)

        if (!isShuffle) {
            exoPlayer.shuffleModeEnabled = false
            exoPlayer.setMediaItems(deduped.map(::mediaItemFromSong), 0, 0L)
            currentSongIndex = 0
            currentSong = deduped.first()
            shuffleState = ShuffleState()
        } else {
            shuffleState = ShuffleManager.startNewCycle(
                pool = deduped,
                preferredFirstSong = null,
                cycleNumber = shuffleState.cycleNumber + 1,
                lastCompletedSongId = shuffleState.lastCompletedSongId
            )
            exoPlayer.setMediaItems(deduped.map(::mediaItemFromSong), 0, 0L)
            applyShuffleOrderToPlayer(shuffleState.currentOrder)
            val firstKey = shuffleState.currentSong?.let(UnifiedPlaybackQueue::stableKey)
            val firstOriginalIndex = deduped.indexOfFirst {
                UnifiedPlaybackQueue.stableKey(it) == firstKey
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
        val active = currentSong
        if (active == null || !UnifiedStreamResolver.requiresResolution(active)) {
            exoPlayer.prepare()
            applyPreferredAudioDevice()
            exoPlayer.play()
        } else {
            playIndexWithResolution(currentSongIndex)
        }
        persistSession(force = true)
        syncNotificationSafely()
    }

    // --- GLOBAL PAUSE MECHANISM ---
    private fun pauseOthers() {
        // If this player starts, stop the DJ Decks
        DJDeckController.activeDecks.forEach { it.pause() }
    }

    fun playRadio(song: AudioItem, mediaItem: MediaItem) {
        UnifiedStreamResolver.cacheResolved(song)
        playRadioCollection(listOf(song), startIndex = 0)
    }

    /**
     * Unified collection playback entrypoint used across Player, Local Folders, Quran,
     * Radio, Online Music (New Songs, Search, Sections, Audius, Hiba), and AI Playlists.
     */
    fun playRadioCollection(stations: List<AudioItem>, startIndex: Int = 0) {
        val deduped = UnifiedPlaybackQueue.deduplicate(stations)
        if (deduped.isEmpty()) return
        pauseOthers()
        captureBeforeRadioSwitch()
        stopCrossfadePreview()

        val safeIndex = startIndex.coerceIn(0, deduped.lastIndex)
        isShuffle = false
        exoPlayer.shuffleModeEnabled = false
        UnifiedStreamResolver.resetRadioFallbackIndex(deduped[safeIndex].id)
        setQueue(deduped, safeIndex)
        skipNextFadeIn = true
        playIndexWithResolution(currentSongIndex.coerceIn(0, playlist.lastIndex))
    }

    fun playCollection(items: List<AudioItem>, startIndex: Int = 0) {
        val deduped = UnifiedPlaybackQueue.deduplicate(items)
        if (deduped.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, deduped.lastIndex)
        play(deduped[safeIndex], deduped)
    }

    fun play(song: AudioItem, newQueue: List<AudioItem>? = null) {
        pauseOthers()
        val dedupedQueue = newQueue?.let {
            UnifiedPlaybackQueue.deduplicate(it).map { item ->
                if (UnifiedPlaybackQueue.stableKey(item) == UnifiedPlaybackQueue.stableKey(song) &&
                    !UnifiedStreamResolver.requiresResolution(song)
                ) {
                    UnifiedStreamResolver.cacheResolved(song)
                    song
                } else {
                    UnifiedStreamResolver.getCached(item) ?: item
                }
            }
        }
        val effectiveSong = UnifiedStreamResolver.getCached(song) ?: song
        val songKey = UnifiedPlaybackQueue.stableKey(effectiveSong)

        val isDifferentQueue = dedupedQueue != null && !UnifiedPlaybackQueue.isSameQueue(dedupedQueue, baseQueue)

        if (isDifferentQueue) {
            val q = dedupedQueue!!
            val startIndex = q.indexOfFirst { UnifiedPlaybackQueue.stableKey(it) == songKey }.takeIf { it >= 0 } ?: 0
            setQueue(q, startIndex)
            if (effectiveSong.album == "Live Radio") skipNextFadeIn = true
            playIndexWithResolution(currentSongIndex.coerceIn(0, playlist.lastIndex))
        } else if (isShuffle) {
            val idx = playlist.indexOfFirst { UnifiedPlaybackQueue.stableKey(it) == songKey }
            if (idx >= 0) {
                shuffleState = ShuffleManager.jumpToIndex(
                    shuffleState,
                    shuffleState.currentOrder.indexOfFirst { UnifiedPlaybackQueue.stableKey(it) == songKey }
                        .takeIf { it >= 0 } ?: shuffleState.currentIndex
                )
                playIndexWithResolution(idx)
            } else {
                val pool = UnifiedPlaybackQueue.deduplicate(baseQueue + listOf(effectiveSong))
                val startIndex = pool.indexOfFirst { UnifiedPlaybackQueue.stableKey(it) == songKey }.coerceAtLeast(0)
                setQueue(pool, startIndex)
                playIndexWithResolution(currentSongIndex.coerceIn(0, playlist.lastIndex))
            }
        } else {
            val idx = playlist.indexOfFirst { UnifiedPlaybackQueue.stableKey(it) == songKey }
            if (idx >= 0) {
                if (playlist[idx].uri != effectiveSong.uri && !UnifiedStreamResolver.requiresResolution(effectiveSong)) {
                    updateResolvedItemInQueue(effectiveSong)
                    if (idx < exoPlayer.mediaItemCount) {
                        exoPlayer.replaceMediaItem(idx, mediaItemFromSong(effectiveSong))
                    }
                }
                playIndexWithResolution(idx)
            } else {
                val q = dedupedQueue ?: listOf(effectiveSong)
                val startIndex = q.indexOfFirst { UnifiedPlaybackQueue.stableKey(it) == songKey }.takeIf { it >= 0 } ?: 0
                setQueue(q, startIndex)
                playIndexWithResolution(currentSongIndex.coerceIn(0, playlist.lastIndex))
            }
        }
        persistSession(force = true)
        syncNotificationSafely()
    }

    private fun updateResolvedItemInQueue(resolved: AudioItem) {
        val key = UnifiedPlaybackQueue.stableKey(resolved)
        val pIdx = playlist.indexOfFirst { UnifiedPlaybackQueue.stableKey(it) == key }
        if (pIdx >= 0) playlist[pIdx] = resolved
        val bIdx = baseQueue.indexOfFirst { UnifiedPlaybackQueue.stableKey(it) == key }
        if (bIdx >= 0) baseQueue[bIdx] = resolved
        if (isShuffle && shuffleState.currentOrder.isNotEmpty()) {
            val updatedOrder = shuffleState.currentOrder.map {
                if (UnifiedPlaybackQueue.stableKey(it) == key) resolved else it
            }
            shuffleState = shuffleState.copy(currentOrder = updatedOrder)
        }
        if (currentSong != null && UnifiedPlaybackQueue.stableKey(currentSong!!) == key) {
            currentSong = resolved
        }
    }

    private fun playIndexWithResolution(targetIndex: Int) {
        val targetSong = playlist.getOrNull(targetIndex) ?: return
        val cachedSong = UnifiedStreamResolver.getCached(targetSong) ?: targetSong
        if (cachedSong !== targetSong) {
            updateResolvedItemInQueue(cachedSong)
        }

        val token = ++resolutionToken
        resolutionJob?.cancel()
        currentSongIndex = targetIndex
        currentSong = cachedSong
        currentPositionMs = 0L
        durationMs = cachedSong.durationMs.coerceAtLeast(0L)
        playbackErrorMessage = null
        if (cachedSong.album == "Live Radio") {
            UnifiedStreamResolver.resetRadioFallbackIndex(cachedSong.id)
            skipNextFadeIn = true
        }

        if (!UnifiedStreamResolver.requiresResolution(cachedSong)) {
            if (targetIndex < exoPlayer.mediaItemCount) {
                val existingMedia = exoPlayer.getMediaItemAt(targetIndex)
                val expectedUri = cachedSong.uri
                if (existingMedia.localConfiguration?.uri != expectedUri) {
                    exoPlayer.replaceMediaItem(targetIndex, mediaItemFromSong(cachedSong))
                }
            }
            exoPlayer.seekTo(targetIndex, 0L)
            preparePlayerForRecoveryIfNeeded()
            applyPreferredAudioDevice()
            exoPlayer.play()
            preResolveUpcomingTrack()
            persistSession(force = true)
            syncNotificationSafely()
            return
        }

        // Lazy remote resolution required before ExoPlayer can stream this item
        exoPlayer.pause()
        isBuffering = true
        syncNotificationSafely()
        resolutionJob = controllerScope.launch {
            runCatching {
                UnifiedStreamResolver.resolve(cachedSong)
            }.onSuccess { resolved ->
                if (token != resolutionToken) return@onSuccess
                val activeIdx = playlist.indexOfFirst {
                    UnifiedPlaybackQueue.stableKey(it) == UnifiedPlaybackQueue.stableKey(resolved)
                }.takeIf { it >= 0 } ?: targetIndex
                updateResolvedItemInQueue(resolved)
                if (activeIdx in 0 until exoPlayer.mediaItemCount) {
                    exoPlayer.replaceMediaItem(activeIdx, mediaItemFromSong(resolved))
                } else {
                    exoPlayer.setMediaItems(playlist.map(::mediaItemFromSong), activeIdx.coerceIn(0, (playlist.size - 1).coerceAtLeast(0)), 0L)
                }
                currentSongIndex = activeIdx
                currentSong = resolved
                exoPlayer.seekTo(activeIdx, 0L)
                exoPlayer.prepare()
                applyPreferredAudioDevice()
                exoPlayer.play()
                isBuffering = false
                preResolveUpcomingTrack()
                persistSession(force = true)
                syncNotificationSafely()
            }.onFailure { err ->
                if (token != resolutionToken) return@onFailure
                isBuffering = false
                isPlaying = false
                playbackErrorMessage = err.message ?: "تعذر تشغيل المقطع"
                if (cachedSong.album == "Live Radio") {
                    recoverFromRadioFailure()
                }
                syncNotificationSafely()
            }
        }
    }

    private fun upcomingSongForPreResolve(): Pair<Int, AudioItem>? {
        if (playlist.size <= 1 || repeatOption == RepeatOption.ONE) return null
        val nextSong = if (isShuffle) {
            resolveNextShuffleSong()
        } else {
            val nextIdx = currentSongIndex + 1
            when {
                nextIdx < playlist.size -> playlist[nextIdx]
                repeatOption == RepeatOption.ALL -> playlist.firstOrNull()
                else -> null
            }
        } ?: return null
        val idx = playlist.indexOfFirst {
            UnifiedPlaybackQueue.stableKey(it) == UnifiedPlaybackQueue.stableKey(nextSong)
        }
        if (idx < 0 || idx == currentSongIndex) return null
        return idx to playlist[idx]
    }

    private fun preResolveUpcomingTrack() {
        val (nextIdx, nextSong) = upcomingSongForPreResolve() ?: return
        if (!UnifiedStreamResolver.requiresResolution(nextSong)) return
        val expectedKey = UnifiedPlaybackQueue.stableKey(nextSong)
        val token = resolutionToken
        preResolveJob?.cancel()
        preResolveJob = controllerScope.launch {
            runCatching {
                UnifiedStreamResolver.resolve(nextSong)
            }.onSuccess { resolved ->
                if (token != resolutionToken) return@onSuccess
                val idxNow = playlist.indexOfFirst { UnifiedPlaybackQueue.stableKey(it) == expectedKey }
                if (idxNow >= 0 && idxNow != currentSongIndex) {
                    updateResolvedItemInQueue(resolved)
                    if (idxNow < exoPlayer.mediaItemCount) {
                        exoPlayer.replaceMediaItem(idxNow, mediaItemFromSong(resolved))
                    }
                }
            }
        }
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
        pauseOthers()

        if (isShuffle) {
            val nextFromCycle = resolveNextShuffleSong()
            if (shuffleState.currentIndex < shuffleState.currentOrder.lastIndex && nextFromCycle != null) {
                shuffleState = shuffleState.copy(currentIndex = shuffleState.currentIndex + 1)
                val nextKey = UnifiedPlaybackQueue.stableKey(nextFromCycle)
                val nextIdx = playlist.indexOfFirst { UnifiedPlaybackQueue.stableKey(it) == nextKey }.coerceAtLeast(0)
                playIndexWithResolution(nextIdx)
            } else {
                val pool = if (baseQueue.isNotEmpty()) baseQueue.toList() else playlist.toList()
                if (pool.size > 1) {
                    shuffleState = ShuffleManager.next(shuffleState, pool)
                    playlist.clear()
                    playlist.addAll(pool)
                    applyShuffleOrderToPlayer(shuffleState.currentOrder)

                    val nextSong = shuffleState.currentSong
                    val nextKey = nextSong?.let(UnifiedPlaybackQueue::stableKey)
                    val nextOriginalIndex = playlist.indexOfFirst {
                        UnifiedPlaybackQueue.stableKey(it) == nextKey
                    }.coerceAtLeast(0)
                    playIndexWithResolution(nextOriginalIndex)
                }
            }
        } else {
            if (playlist.size <= 1) {
                if (repeatOption != RepeatOption.OFF) {
                    playIndexWithResolution(0)
                }
                return
            }
            val nextIdx = if (currentSongIndex + 1 < playlist.size) currentSongIndex + 1 else 0
            playIndexWithResolution(nextIdx)
        }
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return

        isCrossfading = false
        stopCrossfadePreview()
        skipNextFadeIn = false

        if (currentPositionMs > 3000L && currentSong?.album != "Live Radio") {
            seekTo(0L)
            return
        }

        pauseOthers()

        if (isShuffle) {
            if (shuffleState.hasPrevious) {
                shuffleState = ShuffleManager.previous(shuffleState)
                val previousSong = shuffleState.currentSong
                val previousKey = previousSong?.let(UnifiedPlaybackQueue::stableKey)
                val originalIndex = playlist.indexOfFirst {
                    UnifiedPlaybackQueue.stableKey(it) == previousKey
                }.coerceAtLeast(0)
                playIndexWithResolution(originalIndex)
            } else {
                seekTo(0L)
                return
            }
        } else {
            val prevIdx = (currentSongIndex - 1).takeIf { it >= 0 } ?: (playlist.size - 1)
            playIndexWithResolution(prevIdx)
        }
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
                val nextKey = nextSong?.let(UnifiedPlaybackQueue::stableKey)
                val nextOriginalIndex = playlist.indexOfFirst {
                    UnifiedPlaybackQueue.stableKey(it) == nextKey
                }.coerceAtLeast(0)
                playIndexWithResolution(nextOriginalIndex)
            } else {
                isPlaying = false
                persistSession(force = true)
                syncNotificationSafely()
            }
            return
        }

        if (repeatOption == RepeatOption.ALL) {
            val nextIdx = (currentSongIndex + 1).takeIf { it < playlist.size } ?: 0
            playIndexWithResolution(nextIdx)
        } else if (currentSongIndex + 1 < playlist.size) {
            val nextIdx = currentSongIndex + 1
            playIndexWithResolution(nextIdx)
        } else {
            isPlaying = false
            exoPlayer.seekToDefaultPosition(0)
            persistSession(force = true)
            syncNotificationSafely()
        }
    }

    fun onLibraryUpdated(newLibrary: List<AudioItem>) {
        if (newLibrary.isEmpty()) return

        val distinctLibrary = UnifiedPlaybackQueue.deduplicate(newLibrary)
        val validIds = distinctLibrary.map(UnifiedPlaybackQueue::stableKey).toSet()
        val wasPlaying = exoPlayer.isPlaying
        val curPos = exoPlayer.currentPosition
        val curKey = currentSong?.let(UnifiedPlaybackQueue::stableKey)

        val remainingBase = baseQueue.filter { UnifiedPlaybackQueue.stableKey(it) in validIds }
        val baseKeys = remainingBase.map(UnifiedPlaybackQueue::stableKey).toSet()
        val addedBase = distinctLibrary.filter { UnifiedPlaybackQueue.stableKey(it) !in baseKeys }
        baseQueue.clear()
        baseQueue.addAll(remainingBase + addedBase)

        if (isShuffle) {
            shuffleState = ShuffleManager.onLibraryChanged(shuffleState, distinctLibrary)
            playlist.clear()
            playlist.addAll(distinctLibrary)

            if (shuffleState.currentOrder.isNotEmpty()) {
                applyShuffleOrderToPlayer(shuffleState.currentOrder)
                val currentKey = shuffleState.currentSong?.let(UnifiedPlaybackQueue::stableKey) ?: curKey
                val currentOriginalIndex = playlist.indexOfFirst {
                    UnifiedPlaybackQueue.stableKey(it) == currentKey
                }.coerceAtLeast(0)
                currentSongIndex = currentOriginalIndex
                currentSong = playlist.getOrNull(currentOriginalIndex)
                exoPlayer.seekTo(currentOriginalIndex, curPos)
                if (wasPlaying) exoPlayer.play()
            }
        } else {
            val remainingPlaylist = playlist.filter { UnifiedPlaybackQueue.stableKey(it) in validIds }
            val pKeys = remainingPlaylist.map(UnifiedPlaybackQueue::stableKey).toSet()
            val addedPlaylist = distinctLibrary.filter { UnifiedPlaybackQueue.stableKey(it) !in pKeys }
            playlist.clear()
            playlist.addAll(remainingPlaylist + addedPlaylist)

            val newIdx = playlist.indexOfFirst { UnifiedPlaybackQueue.stableKey(it) == curKey }.coerceAtLeast(0)
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
            .setMediaId(UnifiedPlaybackQueue.stableKey(song))
            .apply { UnifiedStreamResolver.mimeTypeFor(song)?.let(::setMimeType) }
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
                    val nextMediaItem = nextSong
                        ?.takeIf { !UnifiedStreamResolver.requiresResolution(it) }
                        ?.let { mediaItemFromSong(it) }

                    if (repeatOption != RepeatOption.ONE && remaining in 1..crossfadeDurationMs &&
                        nextSong != null && !UnifiedStreamResolver.requiresResolution(nextSong)
                    ) {
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
            previewPlayerInstance?.release()
            previewPlayerInstance = null
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
                .putString("visualizer_mode", visualizerMode.name)
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
            prefs.getString("visualizer_mode", null)?.let { savedMode ->
                runCatching { VisualizerMode.valueOf(savedMode) }.getOrNull()?.let { visualizerMode = it }
            }
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

                val items = restored.map(::mediaItemFromSong)
                exoPlayer.setMediaItems(items, savedOriginalIndex, savedPosition)

                val validShuffle = shuffleState.currentOrder.mapNotNull { song ->
                    val key = UnifiedPlaybackQueue.stableKey(song)
                    restored.indexOfFirst { UnifiedPlaybackQueue.stableKey(it) == key }
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
                val active = currentSong
                if (active == null || !UnifiedStreamResolver.requiresResolution(active)) {
                    exoPlayer.prepare()
                }
            } else {
                playlist.clear()
                playlist.addAll(restored)
                val savedIndex = prefs.getInt(KEY_INDEX, 0).coerceIn(0, restored.lastIndex)
                currentSongIndex = savedIndex
                currentSong = restored.getOrNull(savedIndex)
                currentPositionMs = savedPosition
                durationMs = currentSong?.durationMs ?: 0L

                exoPlayer.setMediaItems(restored.map(::mediaItemFromSong), savedIndex, savedPosition)
                exoPlayer.repeatMode = when (repeatOption) {
                    RepeatOption.OFF -> Player.REPEAT_MODE_OFF
                    RepeatOption.ALL -> Player.REPEAT_MODE_ALL
                    RepeatOption.ONE -> Player.REPEAT_MODE_ONE
                }
                exoPlayer.volume = volume
                val active = currentSong
                if (active == null || !UnifiedStreamResolver.requiresResolution(active)) {
                    exoPlayer.prepare()
                }
            }

            val canAutoResume = MusicService.instance?.playerController == null || MusicService.instance?.playerController === this
            if (savedPlaying && canAutoResume) {
                val active = currentSong
                if (active != null && UnifiedStreamResolver.requiresResolution(active)) {
                    playIndexWithResolution(currentSongIndex)
                } else {
                    pauseOthers()
                    exoPlayer.playWhenReady = true
                }
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
        controllerScope.cancel()
        PlaybackNotificationRouter.clear("player")
        try { exoPlayer.setPreferredAudioDevice(null) } catch (e: Exception) { android.util.Log.w("AudioPlayerController", "Caught throwable", e) }
        if (activeInstance === this) activeInstance = null
        visualizerEngine.release()
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
