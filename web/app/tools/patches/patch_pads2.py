import re
with open("app/src/main/java/com/example/djfx/DjFxRepository.kt", "r") as f:
    repo = f.read()

# Instead of just inserting, we should probably delete the pads first to make sure they get cleanly overwritten
replacement = """
            // if (bank in existingPadBanks) return@forEach
            val sounds = fxByCategory[category].orEmpty()
            
            // clear the bank first
            for (i in 0..15) {
                dao.deletePad("${bank}_$i")
            }

            sounds.take(16).forEachIndexed { index, fx ->
                dao.insertPad(DjFxPadEntity("${bank}_$index", fx.id))
            }
"""

repo = repo.replace("""
            // if (bank in existingPadBanks) return@forEach
            val sounds = fxByCategory[category].orEmpty()
            sounds.take(16).forEachIndexed { index, fx ->
                dao.insertPad(DjFxPadEntity("${bank}_$index", fx.id))
            }
""", replacement)
with open("app/src/main/java/com/example/djfx/DjFxRepository.kt", "w") as f:
    f.write(repo)
