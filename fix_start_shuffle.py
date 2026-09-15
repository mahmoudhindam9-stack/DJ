import re

file_path = "app/src/main/java/com/example/player/AudioPlayerController.kt"
with open(file_path, "r") as f:
    content = f.read()

old_items = "val items = shuffleState.currentOrder.map { MediaItem.fromUri(it.uri) }"
new_items = """val items = shuffleState.currentOrder.map {
            MediaItem.Builder()
                .setUri(it.uri)
                .setMediaId(it.id.ifEmpty { it.uri.toString() })
                .build()
        }"""

if old_items in content:
    content = content.replace(old_items, new_items)
    with open(file_path, "w") as f:
        f.write(content)
    print("Fixed startShuffle")
else:
    print("Could not find startShuffle items")

