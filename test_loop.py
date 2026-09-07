with open('app/src/main/java/com/example/player/AudioPlayerController.kt', 'r') as f:
    text = f.read()

idx = text.find('private fun startProgressLoop()')
print(text[idx:idx+1500])
