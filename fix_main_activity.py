import re

with open("/app/applet/app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

target = """    val initialRoute = remember { prefs.getString("last_route", "player") ?: "player" }"""
replacement = """    val activity = context as? android.app.Activity
    val intentRoute = activity?.intent?.getStringExtra("open_route")
    val initialRoute = remember { intentRoute ?: prefs.getString("last_route", "player") ?: "player" }"""

content = content.replace(target, replacement)

with open("/app/applet/app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
