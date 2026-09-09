with open("app/src/main/java/com/example/FullPlayerScreen.kt", "r") as f:
    content = f.read()

content = content.replace("Icons.AutoMirrored.Filled.ArrowBack", "Icons.Filled.ArrowBack")

with open("app/src/main/java/com/example/FullPlayerScreen.kt", "w") as f:
    f.write(content)
