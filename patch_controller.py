import re
with open("app/src/main/java/com/example/djfx/DjFxController.kt", "r") as f:
    content = f.read()

# Force loadData to run everything
content = content.replace('if (true) {', 'if (true) { // Force seed')

# Refresh assignments AFTER seeding
replacement = """
            if (true) { // Force seed
                repository.seedDefaultPads(bankLabels)
                prefs.edit().putBoolean("default_pads_seeded", true).apply()
                padAssignments = repository.getPadAssignments()
            }
"""

content = re.sub(r'if \(true\) \{\s*repository\.seedDefaultPads\(bankLabels\)\s*prefs\.edit\(\)\.putBoolean\("default_pads_seeded", true\)\.apply\(\)\s*\}', replacement, content)

with open("app/src/main/java/com/example/djfx/DjFxController.kt", "w") as f:
    f.write(content)
