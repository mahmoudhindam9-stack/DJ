import re

file_path = "app/src/main/java/com/example/player/AudioPlayerController.kt"
with open(file_path, "r") as f:
    content = f.read()

old_transition = """            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val currentUri = mediaItem?.localConfiguration?.uri"""

new_transition = """            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (isCrossfading && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    completeCrossfade()
                    return
                }
                val currentUri = mediaItem?.localConfiguration?.uri"""

if old_transition in content:
    content = content.replace(old_transition, new_transition)
    with open(file_path, "w") as f:
        f.write(content)
    print("Fixed onMediaItemTransition")
else:
    print("Could not find onMediaItemTransition")

