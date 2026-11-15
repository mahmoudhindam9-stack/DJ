package com.example.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val STREAM_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

@OptIn(UnstableApi::class)
fun createAppMediaSourceFactory(context: Context): DefaultMediaSourceFactory {
    val httpFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)
        .setUserAgent(STREAM_USER_AGENT)
    return DefaultMediaSourceFactory(
        DefaultDataSource.Factory(context.applicationContext, httpFactory)
    )
}

/**
 * Shared queue utilities and lazy stream resolver used across Player, Quran, Radio,
 * Online Music (Albumaty / Hiba / Audius / AI Playlists), and Local Folders.
 */
object UnifiedPlaybackQueue {
    private val DIRECT_AUDIO_EXTENSIONS = setOf(
        ".mp3", ".m4a", ".aac", ".wav", ".flac", ".ogg", ".opus", ".amr", ".3gp", ".m3u8", ".mpd"
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

    private val probeClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val resolvedCache = ConcurrentHashMap<String, AudioItem>()
    private val detectedMimeCache = ConcurrentHashMap<String, String>()
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

        if (lowerUri.startsWith("audius://") || (item.id.startsWith("audius:") && !lowerUri.contains("/stream"))) {
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
            if (pathNoQuery.endsWith(".m3u") || pathNoQuery.endsWith(".pls") || pathNoQuery.endsWith(".asx")) return true
            if (cached != null && cached.uri == item.uri) return false
            return true
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
        val key = UnifiedPlaybackQueue.stableKey(item)
        val fallbacks = RadioStreamCatalog.getFallbackUrls(item.id)
        if (fallbacks.isEmpty()) {
            val probed = probeStreamEndpoint(item.uri.toString())
            if (probed != null) {
                probed.detectedMime?.let { detectedMimeCache[key] = it }
                return item.copy(uri = Uri.parse(probed.finalUrl))
            }
            return item
        }

        val safeStart = attemptIndex.coerceIn(0, fallbacks.lastIndex)
        for (idx in safeStart..fallbacks.lastIndex) {
            radioFallbackAttempt[item.id] = idx
            val candidateUrl = fallbacks[idx]
            val probed = probeStreamEndpoint(candidateUrl)
            if (probed != null && probed.isPlayable) {
                probed.detectedMime?.let { detectedMimeCache[key] = it } ?: detectedMimeCache.remove(key)
                return item.copy(uri = Uri.parse(probed.finalUrl))
            }
        }

        // All probes rejected/failed; fall back to raw attempt index URL so ExoPlayer reports final status
        radioFallbackAttempt[item.id] = safeStart
        detectedMimeCache.remove(key)
        val directUrl = resolveRadioPlaylistUrl(fallbacks[safeStart])
        return item.copy(uri = Uri.parse(directUrl))
    }

    internal data class ProbedStream(
        val finalUrl: String,
        val detectedMime: String?,
        val isPlayable: Boolean
    )

    internal suspend fun probeStreamEndpoint(url: String, depth: Int = 0): ProbedStream? = withContext(Dispatchers.IO) {
        if (depth > 2) return@withContext null
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
            return@withContext ProbedStream(trimmed, inferMimeFromUrl(trimmed), isPlayable = true)
        }

        val urlHintMime = inferMimeFromUrl(trimmed)
        runCatching {
            val req = Request.Builder()
                .url(trimmed)
                .header("User-Agent", STREAM_USER_AGENT)
                .header("Accept", "*/*")
                .build()
            probeClient.newCall(req).execute().use { resp ->
                val finalUrl = resp.request.url.toString()
                if (!resp.isSuccessful) {
                    return@withContext ProbedStream(finalUrl, null, isPlayable = false)
                }
                val contentType = resp.header("Content-Type")?.lowercase().orEmpty()
                val finalPath = finalUrl.lowercase().substringBefore('?').substringBefore('#')

                // Reject HTML landing/error pages immediately so we don't trigger UnrecognizedInputFormatException
                if (contentType.contains("text/html") || contentType.contains("application/xhtml") ||
                    contentType.contains("application/json")
                ) {
                    return@withContext ProbedStream(finalUrl, null, isPlayable = false)
                }

                // Explicit DASH manifest
                if (finalPath.endsWith(".mpd") || contentType.contains("dash+xml")) {
                    return@withContext ProbedStream(finalUrl, MimeTypes.APPLICATION_MPD, isPlayable = true)
                }

                val looksLikePlaylistOrHls = finalPath.endsWith(".m3u8") ||
                    finalPath.endsWith(".m3u") ||
                    finalPath.endsWith(".pls") ||
                    contentType.contains("mpegurl") ||
                    contentType.contains("scpls") ||
                    contentType.contains("pls+xml")

                if (looksLikePlaylistOrHls) {
                    // Read a bounded slice to distinguish HLS (#EXT-X-*) vs M3U/PLS redirect playlist
                    val bodySample = resp.body?.source()?.let { source ->
                        source.request(16_384L)
                        source.buffer.clone().readUtf8(minOf(source.buffer.size, 16_384L))
                    }.orEmpty()

                    val trimmedSample = bodySample.trimStart()
                    if (trimmedSample.startsWith("<!DOCTYPE", ignoreCase = true) ||
                        trimmedSample.startsWith("<html", ignoreCase = true)
                    ) {
                        return@withContext ProbedStream(finalUrl, null, isPlayable = false)
                    }

                    val isHls = finalPath.endsWith(".m3u8") ||
                        bodySample.contains("#EXT-X-STREAM-INF", ignoreCase = true) ||
                        bodySample.contains("#EXT-X-TARGETDURATION", ignoreCase = true) ||
                        bodySample.contains("#EXT-X-MEDIA-SEQUENCE", ignoreCase = true) ||
                        bodySample.contains("#EXT-X-VERSION", ignoreCase = true)

                    if (isHls) {
                        return@withContext ProbedStream(finalUrl, MimeTypes.APPLICATION_M3U8, isPlayable = true)
                    }

                    val extracted = extractUrlFromPlaylistBody(bodySample, finalUrl)
                    if (!extracted.isNullOrBlank() && extracted != finalUrl) {
                        return@withContext probeStreamEndpoint(extracted, depth + 1)
                    }
                    // If an .m3u/.pls had no usable URL inside, reject it
                    if (finalPath.endsWith(".m3u") || finalPath.endsWith(".pls")) {
                        return@withContext ProbedStream(finalUrl, null, isPlayable = false)
                    }
                }

                // Direct progressive audio / octet stream
                val inferred = inferMimeFromUrl(finalUrl) ?: inferMimeFromContentType(contentType) ?: urlHintMime
                ProbedStream(finalUrl, inferred, isPlayable = true)
            }
        }.getOrNull()
    }

    internal fun extractUrlFromPlaylistBody(body: String, baseUrl: String): String? {
        val candidates = mutableListOf<String>()
        for (rawLine in body.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) continue
            val value = if (line.contains('=') && line.substringBefore('=').lowercase().startsWith("file")) {
                line.substringAfter('=', "").trim()
            } else if (!line.contains('=') || line.startsWith("http", ignoreCase = true)) {
                line
            } else {
                ""
            }
            if (value.isNotBlank()) {
                val resolved = runCatching {
                    when {
                        value.startsWith("http://", true) || value.startsWith("https://", true) -> value
                        else -> URI(baseUrl).resolve(value).toString()
                    }
                }.getOrNull()
                if (!resolved.isNullOrBlank() && (resolved.startsWith("http://", true) || resolved.startsWith("https://", true))) {
                    candidates.add(resolved)
                }
            }
        }
        return candidates.firstOrNull()
    }

    suspend fun resolveRadioPlaylistUrl(url: String): String = withContext(Dispatchers.IO) {
        val clean = url.trim().lowercase().substringBefore('?').substringBefore('#')
        if (!clean.endsWith(".m3u") && !clean.endsWith(".pls")) return@withContext url
        probeStreamEndpoint(url)?.finalUrl ?: url
    }

    private fun inferMimeFromUrl(url: String): String? {
        val pathNoQuery = url.trim().lowercase().substringBefore('?').substringBefore('#')
        return when {
            pathNoQuery.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
            pathNoQuery.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            pathNoQuery.endsWith(".mp3") -> MimeTypes.AUDIO_MPEG
            pathNoQuery.endsWith(".aac") || pathNoQuery.endsWith(".aacp") -> MimeTypes.AUDIO_AAC
            pathNoQuery.endsWith(".ogg") || pathNoQuery.endsWith(".oga") -> MimeTypes.AUDIO_OGG
            pathNoQuery.endsWith(".flac") -> MimeTypes.AUDIO_FLAC
            pathNoQuery.endsWith(".wav") -> MimeTypes.AUDIO_WAV
            else -> null
        }
    }

    private fun inferMimeFromContentType(contentType: String): String? {
        val ct = contentType.lowercase()
        return when {
            ct.contains("mpegurl") -> MimeTypes.APPLICATION_M3U8
            ct.contains("dash+xml") -> MimeTypes.APPLICATION_MPD
            ct.contains("audio/mpeg") || ct.contains("audio/mp3") -> MimeTypes.AUDIO_MPEG
            ct.contains("audio/aac") || ct.contains("audio/aacp") -> MimeTypes.AUDIO_AAC
            ct.contains("ogg") -> MimeTypes.AUDIO_OGG
            ct.contains("flac") -> MimeTypes.AUDIO_FLAC
            else -> null
        }
    }

    fun mimeTypeFor(item: AudioItem): String? {
        val key = UnifiedPlaybackQueue.stableKey(item)
        detectedMimeCache[key]?.let { return it }

        val uriStr = item.uri.toString().lowercase()
        inferMimeFromUrl(uriStr)?.let { return it }

        if (item.album == "Live Radio") {
            val codec = RadioStreamCatalog.getCodec(item.id).lowercase()
            return when {
                codec.contains("hls") || uriStr.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
                codec.contains("dash") || uriStr.contains(".mpd") -> MimeTypes.APPLICATION_MPD
                codec.contains("mp3") || codec.contains("mpeg") -> MimeTypes.AUDIO_MPEG
                codec.contains("aac") || codec.contains("aacp") -> MimeTypes.AUDIO_AAC
                codec.contains("ogg") || codec.contains("vorbis") -> MimeTypes.AUDIO_OGG
                codec.contains("flac") -> MimeTypes.AUDIO_FLAC
                else -> null
            }
        }
        return null
    }
}
