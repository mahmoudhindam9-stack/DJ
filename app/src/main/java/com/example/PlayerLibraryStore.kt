package com.example

import android.content.Context
import android.net.Uri
import com.example.model.AudioItem
import com.example.room.AppDatabase
import com.example.room.AudioItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Persists the user's imported audio library in Room database. */
object PlayerLibraryStore {
    private const val PREFS = "player_library_store"
    private const val KEY_LIBRARY = "library"

    var isLoaded = false
        private set

    suspend fun load(context: Context): List<AudioItem> = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val dao = db.audioItemDao()
        
        // Perform migration if needed
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LIBRARY, null)
        
        if (raw != null) {
            // We have legacy data to migrate
            try {
                val array = JSONArray(raw)
                val legacyItems = buildList(array.length()) {
                    for (i in 0 until array.length()) {
                        val o = array.getJSONObject(i)
                        val uri = o.optString("uri")
                        if (uri.isBlank()) continue
                        add(AudioItemEntity(
                            id = o.optString("id", uri.hashCode().toString()),
                            title = o.optString("title", "Unknown Track"),
                            artist = o.optString("artist", "Unknown Artist"),
                            album = o.optString("album", "Unknown Album"),
                            durationMs = o.optLong("durationMs", 0L),
                            uriString = uri,
                            sizeBytes = o.optLong("sizeBytes", 0L)
                        ))
                    }
                }
                if (legacyItems.isNotEmpty()) {
                    dao.insertAudioItems(legacyItems)
                }
                // Clear the old pref to avoid re-migration
                prefs.edit().remove(KEY_LIBRARY).apply()
            } catch (e: Exception) {
                android.util.Log.w("PlayerLibraryStore", "Migration failed", e)
            }
        }
        
        // Load from Room
        val result = dao.getAllAudioItems().map { entity ->
            AudioItem(
                id = entity.id,
                title = entity.title,
                artist = entity.artist,
                album = entity.album,
                durationMs = entity.durationMs,
                uri = Uri.parse(entity.uriString),
                sizeBytes = entity.sizeBytes
            )
        }
        isLoaded = true
        result
    }

    suspend fun save(context: Context, songs: Collection<AudioItem>) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val dao = db.audioItemDao()
        
        val entities = songs.distinctBy { it.id }.map { song ->
            AudioItemEntity(
                id = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.durationMs,
                uriString = song.uri.toString(),
                sizeBytes = song.sizeBytes
            )
        }
        
        dao.insertAudioItems(entities)
    }
}
