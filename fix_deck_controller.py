import re
with open('app/src/main/java/com/example/player/DJDeckController.kt', 'r') as f:
    content = f.read()

content = content.replace("fxProcessor.activeEffects = activeEffects.filterValues { it }.keys.toSet()", "fxProcessor.updateActiveEffects(activeEffects.filterValues { it }.keys.toSet())")

with open('app/src/main/java/com/example/player/DJDeckController.kt', 'w') as f:
    f.write(content)
