import re

file_path = "app/src/main/java/com/example/player/AudioPlayerController.kt"
with open(file_path, "r") as f:
    content = f.read()

old_handle = """    private fun handleTrackEnded() {
        if (repeatOption == RepeatOption.ONE) {
            return
        }"""

new_handle = """    private fun handleTrackEnded() {
        if (isCrossfading) {
            completeCrossfade()
            return
        }
        if (repeatOption == RepeatOption.ONE) {
            return
        }"""

if old_handle in content:
    content = content.replace(old_handle, new_handle)
    with open(file_path, "w") as f:
        f.write(content)
    print("Fixed handleTrackEnded")
else:
    print("Could not find handleTrackEnded block")

