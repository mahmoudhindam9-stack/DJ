package com.example.player

import android.net.Uri
import com.example.model.AudioItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single Shuffle state containing:
 * - current shuffled order
 * - current position
 * - played/unplayed state
 * - persisted cycle state
 */
data class ShuffleState(
    val currentOrder: List<AudioItem> = emptyList(),
    val currentIndex: Int = -1,
    val cycleNumber: Int = 1,
    val lastCompletedSongId: String? = null
) {
    val currentSong: AudioItem?
        get() = currentOrder.getOrNull(currentIndex)

    val playedSongs: List<AudioItem>
        get() = if (currentIndex > 0) currentOrder.take(currentIndex) else emptyList()

    val unplayedSongs: List<AudioItem>
        get() = if (currentIndex in 0 until currentOrder.lastIndex) {
            currentOrder.drop(currentIndex + 1)
        } else if (currentIndex < 0) {
            currentOrder
        } else {
            emptyList()
        }

    val isCycleExhausted: Boolean
        get() = currentOrder.isNotEmpty() && currentIndex >= currentOrder.lastIndex

    val hasNext: Boolean
        get() = currentOrder.isNotEmpty() && currentIndex < currentOrder.lastIndex

    val hasPrevious: Boolean
        get() = currentOrder.isNotEmpty() && currentIndex > 0

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("cycleNumber", cycleNumber)
        json.put("currentIndex", currentIndex)
        json.put("lastCompletedSongId", lastCompletedSongId ?: "")
        val array = JSONArray()
        for (song in currentOrder) {
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
        json.put("currentOrder", array)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): ShuffleState {
            val cycleNumber = json.optInt("cycleNumber", 1)
            val currentIndex = json.optInt("currentIndex", -1)
            val lastId = json.optString("lastCompletedSongId", "").takeIf { it.isNotEmpty() }
            val array = json.optJSONArray("currentOrder")
            val order = mutableListOf<AudioItem>()
            if (array != null) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    order.add(
                        AudioItem(
                            id = obj.optString("id"),
                            title = obj.optString("title", "Unknown Track"),
                            artist = obj.optString("artist", "Unknown Artist"),
                            album = obj.optString("album", "Unknown Album"),
                            durationMs = obj.optLong("durationMs", 0L),
                            uri = Uri.parse(obj.optString("uri")),
                            sizeBytes = obj.optLong("sizeBytes", 0L)
                        )
                    )
                }
            }
            return ShuffleState(
                currentOrder = order,
                currentIndex = currentIndex.coerceIn(-1, (order.size - 1).coerceAtLeast(-1)),
                cycleNumber = cycleNumber,
                lastCompletedSongId = lastId
            )
        }
    }
}
