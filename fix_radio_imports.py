content = open("app/src/main/java/com/example/radio/RadioScreen.kt").read()
if "import androidx.compose.foundation.lazy.itemsIndexed" not in content:
    content = content.replace("import androidx.compose.foundation.lazy.items", "import androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.lazy.itemsIndexed")
open("app/src/main/java/com/example/radio/RadioScreen.kt", "w").write(content)
