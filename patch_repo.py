with open("app/src/main/java/com/example/djfx/DjFxRepository.kt", "r") as f:
    text = f.read()

old_block = """    suspend fun injectMissingFactorySounds() = withContext(Dispatchers.IO) {
        val existingIds = dao.getAllFx().map { it.id }.toSet()
        val missing = FactoryFxCatalog.entries.filter { it.id !in existingIds }
        missing.forEach { entry ->
            dao.insertFx(
                DjFxEntity(
                    id = entry.id,
                    name = entry.name,
                    category = entry.category,
                    source = entry.source,
                    license = "CC0-1.0",
                    sourceUrl = entry.sourceUrl ?: entry.assetPath,
                    localUri = entry.assetPath.takeIf { !it.startsWith("http") && it.isNotBlank() },
                    isFavorite = false
                )
            )
        }
    }"""

new_block = """    suspend fun injectMissingFactorySounds() = withContext(Dispatchers.IO) {
        val existing = dao.getAllFx()
        val existingMap = existing.associateBy { it.id }
        
        FactoryFxCatalog.entries.forEach { entry ->
            val existingEntity = existingMap[entry.id]
            if (existingEntity == null) {
                dao.insertFx(
                    DjFxEntity(
                        id = entry.id,
                        name = entry.name,
                        category = entry.category,
                        source = entry.source,
                        license = "CC0-1.0",
                        sourceUrl = entry.sourceUrl ?: entry.assetPath,
                        localUri = entry.assetPath.takeIf { !it.startsWith("http") && it.isNotBlank() },
                        isFavorite = false
                    )
                )
            } else if (existingEntity.category != entry.category) {
                dao.insertFx(existingEntity.copy(category = entry.category))
            }
        }
    }"""

text = text.replace(old_block, new_block)

with open("app/src/main/java/com/example/djfx/DjFxRepository.kt", "w") as f:
    f.write(text)
