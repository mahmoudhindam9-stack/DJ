package com.example

import android.content.Context
import com.example.model.AudioItem
import org.json.JSONArray
import org.json.JSONObject

/** Persists the user's imported audio library independently from MediaStore. */
object PlayerLibraryStore {
    private const val PREFS = "player_library_store"
    private const val KEY_LIBRARY = "library"

    fun load(context: Context): List<AudioItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LIBRARY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    val uri = o.optString("uri")
                    if (uri.isBlank()) continue
                    add(AudioItem(
                        id = o.optString("id", uri.hashCode().toString()),
                        title = o.optString("title", "Unknown Track"),
                        artist = o.optString("artist", "Unknown Artist"),
                        album = o.optString("album", "Unknown Album"),
                        durationMs = o.optLong("durationMs", 0L),
                        uri = android.net.Uri.parse(uri),
                        sizeBytes = o.optLong("sizeBytes", 0L)
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, songs: Collection<AudioItem>) {
        val array = JSONArray()
        songs.distinctBy { it.id }.forEach { song ->
            array.put(JSONObject().apply {
                put("id", song.id)
                put("title", song.title)
                put("artist", song.artist)
                put("album", song.album)
                put("durationMs", song.durationMs)
                put("uri", song.uri.toString())
                put("sizeBytes", song.sizeBytes)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LIBRARY, array.toString()).apply()
    }
}
