import re

file_path = "app/src/main/java/com/example/player/AudioPlayerController.kt"
with open(file_path, "r") as f:
    content = f.read()

# Fix 1: seekOrLoadMedia should always sync the queue if it doesn't match
old_seek_or_load = """    private fun seekOrLoadMedia(target: AudioItem?, expectedList: List<AudioItem>, expectedIndex: Int, positionMs: Long = 0L) {
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
    }"""

new_seek_or_load = """    private fun seekOrLoadMedia(target: AudioItem?, expectedList: List<AudioItem>, expectedIndex: Int, positionMs: Long = 0L) {
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
    }"""

content = content.replace(old_seek_or_load, new_seek_or_load)

# Fix 2: completeCrossfade should append the remaining queue so gapless playback continues
old_complete_crossfade = """        // old exoPlayer is now preview, so stop it
        stopCrossfadePreview()
        
        currentPositionMs = previewPosition
        
        skipNextFadeIn = true
        persistSession(force = true)
        syncNotificationSafely()
    }"""

new_complete_crossfade = """        // old exoPlayer is now preview, so stop it
        stopCrossfadePreview()
        
        currentPositionMs = previewPosition
        
        skipNextFadeIn = true

        // Append remaining queue to new exoPlayer for gapless playback of subsequent tracks
        val expectedList = if (isShuffle) shuffleState.currentOrder else playlist.toList()
        val itemsToAdd = mutableListOf<MediaItem>()
        for (i in (currentSongIndex + 1) until expectedList.size) {
            val it = expectedList[i]
            itemsToAdd.add(
                MediaItem.Builder()
                    .setUri(it.uri)
                    .setMediaId(it.id.ifEmpty { it.uri.toString() })
                    .build()
            )
        }
        if (itemsToAdd.isNotEmpty()) {
            exoPlayer.addMediaItems(itemsToAdd)
        }

        persistSession(force = true)
        syncNotificationSafely()
    }"""

content = content.replace(old_complete_crossfade, new_complete_crossfade)

with open(file_path, "w") as f:
    f.write(content)

print("Applied fixes to AudioPlayerController.kt")

