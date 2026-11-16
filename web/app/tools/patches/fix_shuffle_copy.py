import re

file_path = "app/src/main/java/com/example/player/AudioPlayerController.kt"
with open(file_path, "r") as f:
    content = f.read()

# Fix 1022
old_copy_1 = """                    shuffleState = shuffleState.copy(
                        currentOrder = nextCycleOrder,
                        currentIndex = 0,
                        cycleNumber = shuffleState.cycleNumber + 1,
                        currentSong = nextCycleOrder.firstOrNull(),
                        lastCompletedSongId = lastSongId
                    )"""

new_copy_1 = """                    shuffleState = shuffleState.copy(
                        currentOrder = nextCycleOrder,
                        currentIndex = 0,
                        cycleNumber = shuffleState.cycleNumber + 1,
                        lastCompletedSongId = lastSongId
                    )"""

# Fix 1036
old_copy_2 = """                    shuffleState = shuffleState.copy(
                        currentIndex = idx,
                        currentSong = shuffleState.currentOrder[idx]
                    )"""

new_copy_2 = """                    shuffleState = shuffleState.copy(
                        currentIndex = idx
                    )"""

content = content.replace(old_copy_1, new_copy_1)
content = content.replace(old_copy_2, new_copy_2)

with open(file_path, "w") as f:
    f.write(content)
print("Fixed shuffleState.copy")
