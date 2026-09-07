import re

# 1. Fix DJDeckController pauseAll
with open('app/src/main/java/com/example/player/DJDeckController.kt', 'r') as f:
    text = f.read()
text = text.replace('fun pauseAll() { deckA.pause(); deckB.pause() }', '')
text = text.replace('fun release() {\n        deckA.release()', 'fun pauseAll() {\n        deckA.pause()\n        deckB.pause()\n    }\n\n    fun release() {\n        deckA.release()')
with open('app/src/main/java/com/example/player/DJDeckController.kt', 'w') as f:
    f.write(text)

# 2. Fix AudioPlayerController.play to take queue
with open('app/src/main/java/com/example/player/AudioPlayerController.kt', 'r') as f:
    text = f.read()

text = text.replace('fun play(song: AudioItem) {', 'fun play(song: AudioItem, newQueue: List<AudioItem>? = null) {')
text = text.replace('setQueue(listOf(song), 0)', 'setQueue(newQueue ?: listOf(song), newQueue?.indexOfFirst { it.uri == song.uri }?.takeIf { it >= 0 } ?: 0)')
with open('app/src/main/java/com/example/player/AudioPlayerController.kt', 'w') as f:
    f.write(text)
