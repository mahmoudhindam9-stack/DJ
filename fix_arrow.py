with open("app/src/main/java/com/example/FullPlayerScreen.kt", "r") as f:
    content = f.read()

content = content.replace("Icons.Filled.ArrowBack", "androidx.compose.material.icons.automirrored.filled.ArrowBack")

with open("app/src/main/java/com/example/FullPlayerScreen.kt", "w") as f:
    f.write(content)
