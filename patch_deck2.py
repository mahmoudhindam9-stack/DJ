with open('app/src/main/java/com/example/player/DJDeckController.kt', 'r') as f:
    text = f.read()

text = text.replace('activeEffects.keys.filter', 'activeEffects.keys.toList().filter')

with open('app/src/main/java/com/example/player/DJDeckController.kt', 'w') as f:
    f.write(text)
