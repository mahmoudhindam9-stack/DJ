package com.example.djfx

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DjFxRepository(private val context: Context) {
    private val dao = DjFxDatabase.getDatabase(context).djFxDao()

    private val defaultFxCatalog = listOf(
        DjFxItem("fx_alarm_1", "Bugle Alarm", "FX", "Google Actions", "CC0", "https://actions.google.com/sounds/v1/alarms/bugle_tune.ogg", "https://actions.google.com/sounds/v1/alarms/bugle_tune.ogg"),
        DjFxItem("fx_crowd_1", "Crowd Cheer", "Crowd", "Google Actions", "CC0", "https://actions.google.com/sounds/v1/crowds/crowd_cheer.ogg", "https://actions.google.com/sounds/v1/crowds/crowd_cheer.ogg"),
        DjFxItem("fx_bell_1", "Bicycle Bell", "FX", "Google Actions", "CC0", "https://actions.google.com/sounds/v1/alarms/bicycle_bell_fast.ogg", "https://actions.google.com/sounds/v1/alarms/bicycle_bell_fast.ogg"),
        DjFxItem("fx_whistle_1", "Whistle", "FX", "Google Actions", "CC0", "https://actions.google.com/sounds/v1/sports/football_whistle.ogg", "https://actions.google.com/sounds/v1/sports/football_whistle.ogg")
    )

    suspend fun getAllFx(): List<DjFxItem> = withContext(Dispatchers.IO) {
        val dbFx = dao.getAllFx().map {
            DjFxItem(it.id, it.name, it.category, it.source, it.license, it.sourceUrl, it.localUri, it.isFavorite)
        }
        val defaultMissing = defaultFxCatalog.filter { defaultItem -> dbFx.none { it.id == defaultItem.id } }
        
        defaultMissing.forEach { insertFx(it) }
        
        if (defaultMissing.isNotEmpty()) {
            dao.getAllFx().map { DjFxItem(it.id, it.name, it.category, it.source, it.license, it.sourceUrl, it.localUri, it.isFavorite) }
        } else {
            dbFx
        }
    }

    suspend fun insertFx(item: DjFxItem) = withContext(Dispatchers.IO) {
        dao.insertFx(DjFxEntity(item.id, item.name, item.category, item.source, item.license, item.sourceUrl, item.localUri, item.isFavorite))
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
}
