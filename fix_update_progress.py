import re

file_path = "app/src/main/java/com/example/player/AudioPlayerController.kt"
with open(file_path, "r") as f:
    content = f.read()

old_update = """                    val nextMediaItem = nextSong?.let { mediaItemFromSong(it) }

                    if (repeatOption != RepeatOption.ONE && remaining in 1 until crossfadeDurationMs && nextMediaItem != null) {
                        startCrossfade(nextMediaItem, remaining)"""

new_update = """                    val nextMediaItem = nextSong?.let { mediaItemFromSong(it) }

                    if (repeatOption != RepeatOption.ONE && remaining in 1..crossfadeDurationMs && nextSong != null) {
                        startCrossfade(nextSong, remaining)"""

if old_update in content:
    content = content.replace(old_update, new_update)
    with open(file_path, "w") as f:
        f.write(content)
    print("Fixed updateProgress crossfade call")
else:
    print("Could not find updateProgress crossfade call")

