package com.example.djfx

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DjFxRepository(private val context: Context) {
    private val dao = DjFxDatabase.getDatabase(context).djFxDao()

    suspend fun getAllFx(): List<DjFxItem> = withContext(Dispatchers.IO) {
        val existing = dao.getAllFx().map { it.toItem() }
        val missing = FactoryFxCatalog.entries
            .filter { entry -> existing.none { it.id == entry.id } }
            .map { entry ->
                DjFxItem(
                    id = entry.id,
                    name = entry.name,
                    category = entry.category,
                    source = "CC0 Open Source",
                    license = "CC0-1.0",
                    sourceUrl = entry.url
                )
            }
        missing.forEach { insertFx(it) }
        dao.getAllFx().map { it.toItem() }
    }

    private fun DjFxEntity.toItem() = DjFxItem(id, name, category, source, license, sourceUrl, localUri, isFavorite)

    suspend fun insertFx(item: DjFxItem) = withContext(Dispatchers.IO) {
        dao.insertFx(
            DjFxEntity(
                item.id, item.name, item.category, item.source, item.license,
                item.sourceUrl, item.localUri, item.isFavorite
            )
        )
    }

    suspend fun getPadAssignments(): Map<String, String> = withContext(Dispatchers.IO) {
        dao.getAllPads().associate { it.padKey to it.fxId }
    }

    suspend fun ensureFactoryPadAssignments() = withContext(Dispatchers.IO) {
        val existing = dao.getAllPads().associate { it.padKey to it.fxId }
        val bankByCategory = mapOf("DJ FX" to "A", "Drums" to "B", "Electronic" to "C", "Party" to "D")
        FactoryFxCatalog.entries.groupBy { it.category }.forEach { (category, entries) ->
            val bank = bankByCategory[category] ?: return@forEach
            entries.take(16).forEachIndexed { index, entry ->
                val key = "${bank}_$index"
                if (key !in existing) {
                    dao.insertPad(DjFxPadEntity(key, entry.id))
                }
            }
        }
    }

    suspend fun assignPad(bank: String, index: Int, fxId: String) = withContext(Dispatchers.IO) {
        dao.insertPad(DjFxPadEntity("${bank}_$index", fxId))
    }

    suspend fun clearPad(bank: String, index: Int) = withContext(Dispatchers.IO) {
        dao.deletePad("${bank}_$index")
    }
}
