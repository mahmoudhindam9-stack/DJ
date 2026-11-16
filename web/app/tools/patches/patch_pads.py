import re
with open("app/src/main/java/com/example/djfx/DjFxController.kt", "r") as f:
    content = f.read()

# Change if (!prefs.getBoolean("default_pads_seeded", false)) to if (true)
# and also map bank A to "DJ" category instead of "DJ FX" to match FactoryFxCatalog
content = content.replace('if (!prefs.getBoolean("default_pads_seeded", false))', 'if (true)')
content = content.replace('"A" to "DJ FX"', '"A" to "DJ"')

with open("app/src/main/java/com/example/djfx/DjFxController.kt", "w") as f:
    f.write(content)

with open("app/src/main/java/com/example/djfx/DjFxRepository.kt", "r") as f:
    repo = f.read()
# Force seeding by commenting out 'if (bank in existingPadBanks) return@forEach'
repo = repo.replace('if (bank in existingPadBanks) return@forEach', '// if (bank in existingPadBanks) return@forEach')
with open("app/src/main/java/com/example/djfx/DjFxRepository.kt", "w") as f:
    f.write(repo)
