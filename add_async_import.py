import re
content = open("app/src/main/java/com/example/radio/RadioScreen.kt").read()

if "import kotlinx.coroutines.async" not in content:
    content = content.replace("import kotlinx.coroutines.launch", "import kotlinx.coroutines.launch\nimport kotlinx.coroutines.async")
    open("app/src/main/java/com/example/radio/RadioScreen.kt", "w").write(content)
