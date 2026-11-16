import re
content = open("app/src/main/java/com/example/radio/RadioScreen.kt").read()

content = content.replace("kotlinx.coroutines.async {", "async {")
open("app/src/main/java/com/example/radio/RadioScreen.kt", "w").write(content)
