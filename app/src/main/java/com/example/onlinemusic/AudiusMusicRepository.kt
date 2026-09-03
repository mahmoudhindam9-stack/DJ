package com.example.onlinemusic

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class AudiusMusicRepository {
    companion object {
        private const val PRIMARY_NODE = "https://discoveryprovider.audius.co/v1"
        private const val API_NODE = "https://api.audius.co/v1"
        private const val APP_NAME = "DJMusicPlayer"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun getHome(): AudiusHomeData = withContext(Dispatchers.IO) {
        val trending = parseTracks(getData("/tracks/trending?limit=50"))
        val latest = parseTracks(getData("/tracks/latest?limit=50"))
        val combined = (trending + latest).distinctBy { it.id }
        AudiusHomeData(
            trending = trending,
            latest = latest,
            artists = combined.map { AudiusArtist(it.artistId, it.artist, it.artworkUrl) }.distinctBy { it.id },
            genres = combined.mapNotNull { it.genre?.takeIf(String::isNotBlank) }.distinct()
        )
    }

    suspend fun search(query: String): List<AudiusTrack> = withContext(Dispatchers.IO) {
        parseTracks(getData("/tracks/search?query=${encode(query)}&limit=50&sort_method=relevant"))
    }

    suspend fun getArtistTracks(artistId: String): AudiusArtistDetail = withContext(Dispatchers.IO) {
        val user = parseSingleUser(getData("/users/$artistId"))
        val tracks = parseTracks(getData("/users/$artistId/tracks?limit=100"))
        AudiusArtistDetail(user, tracks)
    }

    suspend fun getGenreTracks(genre: String): List<AudiusTrack> = withContext(Dispatchers.IO) {
        parseTracks(getData("/tracks/search?query=${encode(genre)}&limit=100&sort_method=popular"))
            .filter { it.genre.equals(genre, ignoreCase = true) || it.genre.isNullOrBlank() }
    }

    suspend fun resolveTrack(track: AudiusTrack): OnlineMusicTrack = withContext(Dispatchers.IO) {
        val streamCandidates = listOf(PRIMARY_NODE, API_NODE)
            .distinct()
            .map { "$it/tracks/${track.id}/stream?app_name=$APP_NAME" }
        val stream = streamCandidates.firstOrNull(::isReachableStream) ?: streamCandidates.first()
        val download = if (track.downloadable) {
            listOf(PRIMARY_NODE, API_NODE)
                .distinct()
                .map { "$it/tracks/${track.id}/download?app_name=$APP_NAME" }
                .firstOrNull(::isReachableStream)
                ?: "$PRIMARY_NODE/tracks/${track.id}/download?app_name=$APP_NAME"
        } else null

        OnlineMusicTrack(
            id = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            artworkUrl = track.artworkUrl,
            streamUrl = stream,
            downloadUrl = download
        )
    }

    suspend fun downloadToUri(audioUrl: String, resolver: ContentResolver, destination: Uri): Long = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(audioUrl)
            .header("User-Agent", "DJ Music Player/1.0 Android")
            .header("Accept", "audio/mpeg,audio/*;q=0.9,*/*;q=0.8")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("فشل تنزيل الملف: HTTP ${response.code}")
            val body = response.body ?: error("ملف الصوت فارغ")
            resolver.openOutputStream(destination)?.use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        total += count
                    }
                    output.flush()
                    total
                }
            } ?: error("تعذر فتح مكان الحفظ")
        }
    }

    private fun getData(path: String): JSONArray {
        var lastFailure: Throwable? = null
        for (base in listOf(PRIMARY_NODE, API_NODE).distinct()) {
            val request = Request.Builder()
                .url("$base$path${if (path.contains('?')) '&' else '?'}app_name=$APP_NAME")
                .header("User-Agent", "DJ Music Player/1.0 Android")
                .header("Accept", "application/json")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastFailure = IllegalStateException("Audius returned ${response.code}")
                    } else {
                        val root = JSONObject(response.body?.string().orEmpty())
                        return root.optJSONArray("data") ?: JSONArray()
                    }
                }
            } catch (t: Throwable) {
                lastFailure = t
            }
        }
        throw lastFailure ?: IllegalStateException("تعذر الاتصال بخدمة Audius")
    }

    private fun isReachableStream(url: String): Boolean = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "DJ Music Player/1.0 Android")
            .header("Range", "bytes=0-1")
            .build()
        client.newCall(request).execute().use { response -> response.isSuccessful }
    }.getOrDefault(false)

    private fun parseTracks(array: JSONArray): List<AudiusTrack> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id")
            if (id.isBlank()) continue
            val user = item.optJSONObject("user")
            val artist = user?.optString("name").orEmpty().ifBlank { user?.optString("handle").orEmpty() }
            val artistId = user?.optString("id").orEmpty()
            val artwork = item.optJSONObject("artwork")
            add(AudiusTrack(id, item.optString("title").ifBlank { "Untitled" }, artist.ifBlank { "Unknown Artist" }, artistId,
                item.optString("album_backlink").takeIf { it.isNotBlank() },
                artwork?.optString("_480x480")?.takeIf { it.isNotBlank() } ?: artwork?.optString("_150x150")?.takeIf { it.isNotBlank() },
                item.optString("genre").takeIf { it.isNotBlank() },
                item.optBoolean("is_streamable", true), item.optBoolean("is_downloadable", false)))
        }
    }.distinctBy { it.id }

    private fun parseSingleUser(array: JSONArray): AudiusArtist {
        val item = array.optJSONObject(0) ?: error("لم يتم العثور على الفنان")
        val id = item.optString("id")
        val name = item.optString("name").ifBlank { item.optString("handle") }.ifBlank { "Artist" }
        val image = item.optJSONObject("profile_picture")?.optString("_480x480") ?: item.optJSONObject("profile_picture")?.optString("_150x150")
        return AudiusArtist(id, name, image?.takeIf { it.isNotBlank() })
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

data class AudiusHomeData(val trending: List<AudiusTrack> = emptyList(), val latest: List<AudiusTrack> = emptyList(), val artists: List<AudiusArtist> = emptyList(), val genres: List<String> = emptyList())
data class AudiusArtistDetail(val artist: AudiusArtist, val tracks: List<AudiusTrack>)
data class AudiusTrack(val id: String, val title: String, val artist: String, val artistId: String, val album: String?, val artworkUrl: String?, val genre: String?, val streamable: Boolean, val downloadable: Boolean)
data class AudiusArtist(val id: String, val name: String, val artworkUrl: String?)
