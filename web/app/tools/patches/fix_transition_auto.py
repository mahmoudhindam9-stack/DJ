import re

file_path = "app/src/main/java/com/example/player/AudioPlayerController.kt"
with open(file_path, "r") as f:
    content = f.read()

old_transition = """            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (isCrossfading && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    completeCrossfade()
                    return
                }
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
            }"""

new_transition = """            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (isCrossfading && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    completeCrossfade()
                    return
                }
                
                // Only automatically advance our state if ExoPlayer transitioned naturally.
                // For SEEK or PLAYLIST_CHANGED, we've already updated our state manually.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    val currentMediaId = mediaItem?.mediaId?.ifEmpty { mediaItem.localConfiguration?.uri?.toString() }
                    val index = if (currentMediaId != null) {
                        playlist.indexOfFirst { it.id.ifEmpty { it.uri.toString() } == currentMediaId }.takeIf { it >= 0 } ?: exoPlayer.currentMediaItemIndex
                    } else {
                        exoPlayer.currentMediaItemIndex
                    }
                    if (index in playlist.indices) {
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
                            currentSongIndex = index
                            currentSong = playlist[index]
                        }
                        currentPositionMs = 0L
                        persistSession(force = true)
                        syncNotificationSafely()
                    }
                }
            }"""

if old_transition in content:
    content = content.replace(old_transition, new_transition)
    with open(file_path, "w") as f:
        f.write(content)
    print("Fixed onMediaItemTransition to only handle AUTO")
else:
    print("Could not find onMediaItemTransition")

