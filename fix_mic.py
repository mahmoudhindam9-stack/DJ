with open("app/src/main/java/com/example/MicScreen.kt", "r") as f:
    content = f.read()

content = content.replace("Icons.AutoMirrored.Filled.VolumeUp", "Icons.Filled.VolumeUp")

with open("app/src/main/java/com/example/MicScreen.kt", "w") as f:
    f.write(content)
