package com.example.player

import android.net.Uri
import com.example.model.AudioItem
import com.example.onlinemusic.AlbumatyLink
import com.example.onlinemusic.AudiusMusicRepository
import com.example.onlinemusic.AudiusTrack
import com.example.onlinemusic.HibaMusicRepository
import com.example.onlinemusic.HibaTrackLink
import com.example.onlinemusic.OnlineMusicRepository
import com.example.radio.RadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared queue utilities and lazy stream resolver used across Player, Quran, Radio,
 * Online Music (Albumaty / Hiba / Audius / AI Playlists), and Local Folders.
 */
object UnifiedPlaybackQueue {
    private val DIRECT_AUDIO_EXTENSIONS = setOf(
        ".mp3", ".m4a", ".aac", ".wav", ".flac", ".ogg", ".opus", ".amr", ".3gp"
    )

    fun stableKey(item: AudioItem): String = item.id.ifBlank { item.uri.toString() }

    fun deduplicate(items: List<AudioItem>): List<AudioItem> {
        if (items.size <= 1) return items
        val seenKeys = HashSet<String>(items.size)
        return items.filter { seenKeys.add(stableKey(it)) }
    }

    fun isSameQueue(first: List<AudioItem>, second: List<AudioItem>): Boolean {
        if (first.size != second.size) return false
        for (i in first.indices) {
            if (stableKey(first[i]) != stableKey(second[i])) return false
        }
        return true
    }

    fun albumatyLinkToAudioItem(
        link: AlbumatyLink,
        fallbackAlbum: String = "Online Music"
    ): AudioItem {
        val clean = link.title
            .replace(Regex("^(?:اغنية|أغنية|تحميل|استماع)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*MP3\\s*$", RegexOption.IGNORE_CASE), "")
            .trim()
        val hasSplit = clean.contains(" - ")
        val title = if (hasSplit) clean.substringBefore(" - ").trim().ifBlank { clean } else clean.ifBlank { link.title }
        val artist = if (hasSplit) clean.substringAfter(" - ").trim().ifBlank { "Albumaty" } else "Albumaty"
        return AudioItem(
            id = link.url,
            title = title,
            artist = artist,
            album = fallbackAlbum,
            durationMs = 0L,
            uri = Uri.parse(link.url)
        )
    }

    internal fun hibaTrackToAudioItem(track: HibaTrackLink): AudioItem =
        AudioItem(
            id = "hiba:${track.url}",
            title = track.title,
            artist = "Hiba Music",
            album = "Oldies & Classics",
            durationMs = 0L,
            uri = Uri.parse(track.url)
        )

    fun audiusTrackToAudioItem(track: AudiusTrack): AudioItem {
        AudiusTrackCatalog.register(track)
        return AudioItem(
            id = "audius:${track.id}",
            title = track.title,
            artist = track.artist,
            album = track.album ?: "Audius",
            durationMs = 0L,
            uri = Uri.parse("audius://resolve/${track.id}")
        )
    }

    fun radioStationToAudioItem(station: RadioStation): AudioItem {
        RadioStreamCatalog.register(station)
        return AudioItem(
            id = station.id,
            title = "📻 ${station.name}",
            artist = if (station.countryCode.equals("EG", ignoreCase = true)) "إذاعة مصرية" else "Internet Radio",
            album = "Live Radio",
            durationMs = 0L,
            uri = Uri.parse(station.streamUrls.first())
        )
    }

    fun isDirectPlayableUri(uriString: String): Boolean {
        val lower = uriString.trim().lowercase().substringBefore('?').substringBefore('#')
        if (lower.startsWith("content://") || lower.startsWith("file://") || lower.startsWith("android.resource://")) {
            return true
        }
        return DIRECT_AUDIO_EXTENSIONS.any { lower.endsWith(it) }
    }
}

object RadioStreamCatalog {
    private val stations = ConcurrentHashMap<String, RadioStation>()

    fun register(station: RadioStation) {
        if (station.id.isNotBlank() && station.streamUrls.isNotEmpty()) {
            stations[station.id] = station
        }
    }

    fun registerAll(list: List<RadioStation>) {
        list.forEach(::register)
    }

    fun get(id: String): RadioStation? = stations[id]

    fun getFallbackUrls(stationId: String): List<String> = stations[stationId]?.streamUrls.orEmpty()

    fun getCodec(stationId: String): String = stations[stationId]?.codec.orEmpty()
}

object AudiusTrackCatalog {
    private val tracks = ConcurrentHashMap<String, AudiusTrack>()

    fun register(track: AudiusTrack) {
        tracks["audius:${track.id}"] = track
    }

    fun registerAll(list: List<AudiusTrack>) {
        list.forEach(::register)
    }

    fun get(audioItemId: String): AudiusTrack? = tracks[audioItemId]
}

object UnifiedStreamResolver {
    private val onlineRepo by lazy { OnlineMusicRepository() }
    private val hibaRepo by lazy { HibaMusicRepository() }
    private val audiusRepo by lazy { AudiusMusicRepository() }

    private val resolvedCache = ConcurrentHashMap<String, AudioItem>()
    private val radioFallbackAttempt = ConcurrentHashMap<String, Int>()

    fun cacheResolved(item: AudioItem) {
        val uriStr = item.uri.toString()
        if (uriStr.isNotBlank() && !requiresResolution(item)) {
            resolvedCache[UnifiedPlaybackQueue.stableKey(item)] = item
        }
    }

    fun getCached(item: AudioItem): AudioItem? = resolvedCache[UnifiedPlaybackQueue.stableKey(item)]

    fun resetRadioFallbackIndex(stationId: String) {
        radioFallbackAttempt[stationId] = 0
    }

    fun requiresResolution(item: AudioItem): Boolean {
        val key = UnifiedPlaybackQueue.stableKey(item)
        val uriStr = item.uri.toString().trim()
        if (uriStr.isBlank()) return true

        val cached = resolvedCache[key]
        if (cached != null && cached.uri == item.uri && cached.album != "Live Radio") {
            return false
        }

        val lowerUri = uriStr.lowercase()
        val pathNoQuery = lowerUri.substringBefore('?').substringBefore('#')

        if (lowerUri.startsWith("audius://") || item.id.startsWith("audius:") && !lowerUri.contains("/stream")) {
            return true
        }
        if (item.id.startsWith("hiba:") && !UnifiedPlaybackQueue.isDirectPlayableUri(uriStr)) {
            return true
        }
        if ((lowerUri.contains("albumaty.com/") || item.id.contains("albumaty.com/")) &&
            !UnifiedPlaybackQueue.isDirectPlayableUri(uriStr)
        ) {
            return true
        }
        if (item.album == "Live Radio") {
            if (pathNoQuery.endsWith(".m3u") || pathNoQuery.endsWith(".pls")) return true
            if (cached != null && cached.uri == item.uri) return false
            // Allow first-time catalog check if fallback URLs exist and not yet resolved
            val fallbacks = RadioStreamCatalog.getFallbackUrls(item.id)
            if (fallbacks.isNotEmpty() && cached == null) return true
        }
        return false
    }

    suspend fun resolve(item: AudioItem): AudioItem = withContext(Dispatchers.IO) {
        val key = UnifiedPlaybackQueue.stableKey(item)
        resolvedCache[key]?.let { cached ->
            if (!requiresResolution(cached)) return@withContext cached
        }

        val uriStr = item.uri.toString().trim()
        val resolved = when {
            item.album == "Live Radio" -> resolveRadioItem(item, attemptIndex = 0)
            item.id.startsWith("audius:") -> resolveAudiusItem(item)
            item.id.startsWith("hiba:") -> resolveHibaItem(item)
            uriStr.contains("albumaty.com/", ignoreCase = true) || item.id.contains("albumaty.com/", ignoreCase = true) -> {
                resolveAlbumatyItem(item)
            }
            else -> item
        }
        resolvedCache[key] = resolved
        resolved
    }

    suspend fun resolveNextRadioFallback(item: AudioItem): AudioItem? = withContext(Dispatchers.IO) {
        if (item.album != "Live Radio") return@withContext null
        val fallbacks = RadioStreamCatalog.getFallbackUrls(item.id)
        if (fallbacks.size <= 1) return@withContext null
        val nextIndex = (radioFallbackAttempt[item.id] ?: 0) + 1
        if (nextIndex >= fallbacks.size) return@withContext null
        val updated = resolveRadioItem(item, nextIndex)
        resolvedCache[UnifiedPlaybackQueue.stableKey(updated)] = updated
        updated
    }

    private suspend fun resolveAlbumatyItem(item: AudioItem): AudioItem {
        val songPageUrl = item.id.takeIf { it.startsWith("http") } ?: item.uri.toString()
        val track = onlineRepo.resolveTrack(AlbumatyLink(item.title, songPageUrl))
        val stream = track.streamUrl?.takeIf { it.isNotBlank() }
            ?: error("لم يتم العثور على رابط تشغيل الأغنية")
        return item.copy(
            title = track.title.ifBlank { item.title },
            artist = track.artist.ifBlank { item.artist },
            album = track.album?.takeIf { it.isNotBlank() } ?: item.album,
            uri = Uri.parse(stream)
        )
    }

    private suspend fun resolveHibaItem(item: AudioItem): AudioItem {
        val pageUrl = item.id.removePrefix("hiba:").ifBlank { item.uri.toString() }
        val stream = hibaRepo.resolve(HibaTrackLink(item.title, pageUrl))
        return item.copy(uri = Uri.parse(stream))
    }

    private suspend fun resolveAudiusItem(item: AudioItem): AudioItem {
        val rawId = item.id.removePrefix("audius:")
        val track = AudiusTrackCatalog.get(item.id) ?: AudiusTrack(
            id = rawId,
            title = item.title,
            artist = item.artist,
            artistId = "",
            album = item.album,
            artworkUrl = null,
            genre = null,
            streamable = true,
            downloadable = false
        )
        val resolved = audiusRepo.resolveTrack(track)
        val stream = resolved.streamUrl?.takeIf { it.isNotBlank() }
            ?: error("Stream unavailable for ${item.title}")
        return item.copy(
            title = resolved.title.ifBlank { item.title },
            artist = resolved.artist.ifBlank { item.artist },
            album = resolved.album ?: item.album,
            uri = Uri.parse(stream)
        )
    }

    private suspend fun resolveRadioItem(item: AudioItem, attemptIndex: Int): AudioItem {
        val fallbacks = RadioStreamCatalog.getFallbackUrls(item.id)
        val rawUrl = fallbacks.getOrElse(attemptIndex) { item.uri.toString() }
        radioFallbackAttempt[item.id] = attemptIndex
        val directUrl = resolveRadioPlaylistUrl(rawUrl)
        return item.copy(uri = Uri.parse(directUrl))
    }

    suspend fun resolveRadioPlaylistUrl(url: String): String = withContext(Dispatchers.IO) {
        val clean = url.trim().lowercase().substringBefore('?')
        if (!clean.endsWith(".m3u") && !clean.endsWith(".pls")) return@withContext url
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "DJ-Music-Player/1.0")
            }
            try {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val direct = body.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
                direct ?: body.lineSequence()
                    .map { it.substringAfter('=', "").trim() }
                    .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
                    ?: url
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(url)
    }

    fun mimeTypeFor(item: AudioItem): String? {
        if (item.album == "Live Radio") {
            val codec = RadioStreamCatalog.getCodec(item.id).lowercase()
            val u = item.uri.toString().lowercase()
            return when {
                codec.contains("mp3") || codec.contains("mpeg") || u.contains(".mp3") -> "audio/mpeg"
                codec.contains("aac") || codec.contains("aacp") || u.contains(".aac") -> "audio/aac"
                codec.contains("ogg") || codec.contains("vorbis") || u.contains(".ogg") -> "audio/ogg"
                codec.contains("flac") || u.contains(".flac") -> "audio/flac"
                else -> null
            }
        }
        return null
    }
}
