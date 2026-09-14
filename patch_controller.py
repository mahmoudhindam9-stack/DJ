import re
with open("app/src/main/java/com/example/djfx/DjFxController.kt", "r") as f:
    content = f.read()

# Restore the if statement to run once!
content = content.replace('if (true) { // Force seed', 'if (!prefs.getBoolean("default_pads_seeded_v2", false)) {')
# also fix prefs
content = content.replace('prefs.edit().putBoolean("default_pads_seeded", true).apply()', 'prefs.edit().putBoolean("default_pads_seeded_v2", true).apply()')

with open("app/src/main/java/com/example/djfx/DjFxController.kt", "w") as f:
    f.write(content)

