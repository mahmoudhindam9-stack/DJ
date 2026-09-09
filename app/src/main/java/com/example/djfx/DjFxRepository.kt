package com.example.djfx

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DjFxRepository(private val context: Context) {
    private val dao = DjFxDatabase.getDatabase(context).djFxDao()

    /** Returns the user's own DJ FX library — no factory bank is auto-injected. */
    suspend fun getAllFx(): List<DjFxItem> = withContext(Dispatchers.IO) {
        dao.getAllFx().map { it.toItem() }
    }

    private fun DjFxEntity.toItem() = DjFxItem(
        id = id,
        name = name,
        category = category,
        source = source,
        license = license,
        sourceUrl = sourceUrl,
        localUri = localUri,
        isFavorite = isFavorite
    )

    suspend fun insertFx(item: DjFxItem) = withContext(Dispatchers.IO) {
        dao.insertFx(
            DjFxEntity(
                item.id,
                item.name,
                item.category,
                item.source,
                item.license,
                item.sourceUrl,
                item.localUri,
                item.isFavorite
            )
        )
    }

    suspend fun getPadAssignments(): Map<String, String> = withContext(Dispatchers.IO) {
        dao.getAllPads().associate { it.padKey to it.fxId }
    }

    suspend fun assignPad(bank: String, index: Int, fxId: String) = withContext(Dispatchers.IO) {
        dao.insertPad(DjFxPadEntity("${bank}_$index", fxId))
    }

    suspend fun clearPad(bank: String, index: Int) = withContext(Dispatchers.IO) {
        dao.deletePad("${bank}_$index")
    }

    suspend fun injectMissingFactorySounds() = withContext(Dispatchers.IO) {
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
    }

    /**
     * Permanently removes the bundled factory sound bank (and any pad that
     * was still pointing at one of those sounds) from this user's library,
     * so the DJ FX page starts clean and stays that way.
     */
    suspend fun purgeFactorySounds() = withContext(Dispatchers.IO) {
        val factoryIds = FactoryFxCatalog.entries.map { it.id }
        if (factoryIds.isEmpty()) return@withContext
        dao.deletePadsByFxIds(factoryIds)
        dao.deleteFxByIds(factoryIds)
    }

    /**
     * Fills each bank's pad grid with the factory sounds that belong to it
     * (matched by category), so the pads show ready-to-play sounds instead of
     * an empty grid even though the sounds already exist in the library.
     * Only touches a bank that has zero pads assigned, so it never overwrites
     * a pad the user has since customized or cleared themselves.
     */
    suspend fun seedDefaultPads(bankCategories: Map<String, String>) = withContext(Dispatchers.IO) {
        val existingPadBanks = dao.getAllPads().map { it.padKey.substringBefore('_') }.toSet()
        val fxByCategory = dao.getAllFx().map { it.toItem() }.groupBy { it.category }
        bankCategories.forEach { (bank, category) ->
            if (bank in existingPadBanks) return@forEach
            val sounds = fxByCategory[category].orEmpty()
            sounds.take(16).forEachIndexed { index, fx ->
                dao.insertPad(DjFxPadEntity("${bank}_$index", fx.id))
            }
        }
    }
}
