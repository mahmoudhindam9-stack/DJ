import re

file_path = "app/src/main/java/com/example/player/AudioPlayerController.kt"
with open(file_path, "r") as f:
    content = f.read()

old_sync = """    private fun syncShuffleQueuePreservingCurrent(
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
    }"""

new_sync = """    private fun syncShuffleQueuePreservingCurrent(
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

        val playerItems = (0 until exoPlayer.mediaItemCount).map { idx ->
            val mi = exoPlayer.getMediaItemAt(idx)
            mi.mediaId.ifBlank { mi.localConfiguration?.uri?.toString().orEmpty() }
        }
        val expectedIds = items.map { it.mediaId }
        if (playerItems == expectedIds) {
            return
        }

        val currentExoIndex = exoPlayer.currentMediaItemIndex
        if (exoPlayer.playbackState != androidx.media3.common.Player.STATE_IDLE && currentExoIndex in 0 until exoPlayer.mediaItemCount) {
            val playingMediaId = exoPlayer.getMediaItemAt(currentExoIndex).mediaId.ifBlank { exoPlayer.getMediaItemAt(currentExoIndex).localConfiguration?.uri?.toString().orEmpty() }
            val expectedPlayingId = items[currentIndex].mediaId
            if (playingMediaId == expectedPlayingId) {
                // We are playing the right track, just modify the queue around it smoothly!
                if (exoPlayer.mediaItemCount > currentExoIndex + 1) {
                    exoPlayer.removeMediaItems(currentExoIndex + 1, exoPlayer.mediaItemCount)
                }
                if (currentExoIndex > 0) {
                    exoPlayer.removeMediaItems(0, currentExoIndex)
                }
                // exoPlayer now only has 1 item at index 0.
                if (currentIndex + 1 < items.size) {
                    exoPlayer.addMediaItems(items.subList(currentIndex + 1, items.size))
                }
                if (currentIndex > 0) {
                    exoPlayer.addMediaItems(0, items.subList(0, currentIndex))
                }
                return
            }
        }

        val safePosition = currentPosition.coerceAtLeast(0L)
        exoPlayer.setMediaItems(items, currentIndex, safePosition)
        exoPlayer.prepare()
        if (wasPlaying) {
            exoPlayer.play()
        }
    }"""

if old_sync in content:
    content = content.replace(old_sync, new_sync)
    with open(file_path, "w") as f:
        f.write(content)
    print("Fixed syncShuffleQueuePreservingCurrent")
else:
    print("Could not find syncShuffleQueuePreservingCurrent")

