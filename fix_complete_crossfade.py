import re

file_path = "app/src/main/java/com/example/player/AudioPlayerController.kt"
with open(file_path, "r") as f:
    content = f.read()

old_complete = """    private fun completeCrossfade() {
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
        }"""

new_complete = """    private fun completeCrossfade() {
        isCrossfading = false
        val preview = previewPlayerInstance
        val previewPosition = preview?.currentPosition ?: 0L

        val previewMediaId = preview?.currentMediaItem?.mediaId
        val targetSong = crossfadeTargetSong

        if (previewMediaId == null || targetSong == null) {
            stopCrossfadePreview()
            skipNextFadeIn = true
            persistSession(force = true)
            syncNotificationSafely()
            return
        }

        val curId = currentSong?.id?.ifEmpty { currentSong?.uri?.toString() }
        val isAlreadyTarget = curId != null && (curId == targetSong.id.ifEmpty { targetSong.uri.toString() } || currentSong?.uri == targetSong.uri)

        if (!isAlreadyTarget) {
            if (isShuffle) {
                val pool = if (baseQueue.isNotEmpty()) baseQueue else shuffleState.currentOrder
                // First ensure we have a cycle
                if (shuffleState.currentOrder.isEmpty()) {
                    shuffleState = ShuffleManager.startNewCycle(pool)
                }
                
                // We must find targetSong in the CURRENT cycle. If it's not there, it must be the first of the NEXT cycle.
                val targetKey = targetSong.id.ifEmpty { targetSong.uri.toString() }
                var idx = shuffleState.currentOrder.indexOfFirst { it.id.ifEmpty { it.uri.toString() } == targetKey }
                
                if (idx < 0) {
                    // It's in the next cycle, generate it.
                    val lastSongId = shuffleState.currentOrder.lastOrNull()?.let { it.id.ifEmpty { it.uri.toString() } }
                    val nextCycleOrder = ShuffleManager.generateCycleOrder(
                        pool = pool,
                        lastCompletedSongId = lastSongId,
                        previousOrder = shuffleState.currentOrder
                    )
                    shuffleState = shuffleState.copy(
                        currentOrder = nextCycleOrder,
                        currentIndex = 0,
                        cycleNumber = shuffleState.cycleNumber + 1,
                        currentSong = nextCycleOrder.firstOrNull(),
                        lastCompletedSongId = lastSongId
                    )
                    playlist.clear()
                    playlist.addAll(shuffleState.currentOrder)
                    
                    idx = shuffleState.currentOrder.indexOfFirst { it.id.ifEmpty { it.uri.toString() } == targetKey }
                }
                
                if (idx >= 0) {
                    currentSongIndex = idx
                    currentSong = shuffleState.currentOrder[idx]
                    shuffleState = shuffleState.copy(
                        currentIndex = idx,
                        currentSong = shuffleState.currentOrder[idx]
                    )
                }
            } else {
                val targetKey = targetSong.id.ifEmpty { targetSong.uri.toString() }
                val idx = playlist.indexOfFirst { it.id.ifEmpty { it.uri.toString() } == targetKey }
                if (idx >= 0) {
                    currentSongIndex = idx
                    currentSong = playlist[idx]
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
        }"""

if old_complete in content:
    content = content.replace(old_complete, new_complete)
    with open(file_path, "w") as f:
        f.write(content)
    print("Fixed completeCrossfade")
else:
    print("Could not find completeCrossfade")

