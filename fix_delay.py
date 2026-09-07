with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    text = f.read()

text = text.replace('playerController.updateProgress()\\n            delay(250)', 'playerController.updateProgress()\\n            delay(50)')

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(text)
