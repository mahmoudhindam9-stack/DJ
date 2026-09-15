import re

file_path = "app/src/main/java/com/example/player/AudioPlayerController.kt"
with open(file_path, "r") as f:
    content = f.read()

# Update startCrossfade to accept AudioItem
old_start = """    private fun startCrossfade(nextItem: MediaItem, customDurationMs: Long = 0L) {
        val currentMediaId = exoPlayer.currentMediaItem?.mediaId
        val nextId = nextItem.mediaId"""

new_start = """    private fun startCrossfade(nextSong: AudioItem, customDurationMs: Long = 0L) {
        val currentMediaId = exoPlayer.currentMediaItem?.mediaId
        val nextItem = mediaItemFromSong(nextSong)
        val nextId = nextItem.mediaId
        crossfadeTargetSong = nextSong"""

if old_start in content:
    content = content.replace(old_start, new_start)
    with open(file_path, "w") as f:
        f.write(content)
    print("Fixed startCrossfade signature")
else:
    print("Could not find startCrossfade signature")

