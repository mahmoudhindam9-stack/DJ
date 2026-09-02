package com.example.onlinemusic

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class OnlineMusicRepository {
    companion object {
        const val HOME_URL = "https://www.albumaty.com/cat/1.html"
    }

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private suspend fun getHtml(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) DJ Music Player")
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Albumaty returned ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    suspend fun getHome(): AlbumatyHomeData = withContext(Dispatchers.IO) {
        parseHome(getHtml(HOME_URL))
    }

    suspend fun getSection(link: AlbumatyLink): AlbumatySection = withContext(Dispatchers.IO) {
        val html = getHtml(link.url)
        AlbumatySection(link.title, link.url, parseSongLinks(html).take(80))
    }

    suspend fun resolveTrack(song: AlbumatyLink): OnlineMusicTrack = withContext(Dispatchers.IO) {
        val songHtml = getHtml(song.url)
        val downloadPageUrl = extractDownloadPage(songHtml)
            ?: error("لم يتم العثور على صفحة تحميل الأغنية")
        val downloadHtml = getHtml(downloadPageUrl)
        val audioUrl = extractAudioUrl(downloadHtml)
            ?: error("لم يتم العثور على رابط الصوت المباشر")
        OnlineMusicTrack(
            id = song.url,
            title = song.title,
            artist = "",
            album = null,
            artworkUrl = null,
            streamUrl = audioUrl,
            downloadUrl = audioUrl
        )
    }

    suspend fun downloadToUri(audioUrl: String, resolver: ContentResolver, destination: Uri): Long = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(audioUrl)
            .header("User-Agent", "Mozilla/5.0 (Android) DJ Music Player")
            .header("Referer", "https://www.albumaty.com/")
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

    suspend fun search(query: String): List<AlbumatyLink> = withContext(Dispatchers.IO) {
        val home = getHome()
        val q = query.trim()
        if (q.isBlank()) return@withContext home.songs
        (home.albums + home.songs + home.artists + home.categories)
            .filter { it.title.contains(q, ignoreCase = true) }
            .distinctBy { it.url }
    }

    private fun parseHome(html: String): AlbumatyHomeData {
        val links = parseLinks(html)
        fun unique(prefix: String): List<AlbumatyLink> = links
            .filter { it.url.contains("/$prefix/") }
            .distinctBy { it.url }
            .take(60)
        return AlbumatyHomeData(
            categories = unique("cat"),
            albums = unique("album"),
            songs = unique("song"),
            artists = unique("artist")
        )
    }

    private fun parseSongLinks(html: String): List<AlbumatyLink> = parseLinks(html)
        .filter { it.url.contains("/song/") }
        .distinctBy { it.url }

    private fun parseLinks(html: String): List<AlbumatyLink> {
        val linkRegex = Regex(
            "<a[^>]+href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        return linkRegex.findAll(html).mapNotNull { match ->
            val title = stripHtml(match.groupValues[2])
            if (title.isBlank()) return@mapNotNull null
            val url = normalizeUrl(match.groupValues[1].trim())
            if (!url.startsWith("https://www.albumaty.com/")) return@mapNotNull null
            AlbumatyLink(title, url)
        }.toList()
    }

    private fun extractDownloadPage(html: String): String? {
        val regex = Regex(
            "<a[^>]+href=[\\\"']([^\\\"']*?/download/[^\\\"']+)[\\\"'][^>]*>",
            RegexOption.IGNORE_CASE
        )
        return regex.find(html)?.groupValues?.getOrNull(1)?.let(::normalizeUrl)
    }

    private fun extractAudioUrl(html: String): String? {
        val directMp3 = Regex(
            "https?://[^\\\"'<>\\s]+\\.mp3(?:\\?[^\\\"'<>\\s]*)?",
            RegexOption.IGNORE_CASE
        ).find(html)?.value
        if (directMp3 != null) return normalizeUrl(directMp3)

        val anchor = Regex(
            "<a[^>]+href=[\\\"']([^\\\"']+)[\\\"'][^>]*>[^<]*(?:تحميل|download)[^<]*</a>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)?.groupValues?.getOrNull(1)
        return anchor?.let(::normalizeUrl)?.takeIf { it.contains(".mp3", ignoreCase = true) }
    }

    private fun normalizeUrl(value: String): String {
        val decoded = URLDecoder.decode(value.replace("&amp;", "&"), StandardCharsets.UTF_8.name())
        return when {
            decoded.startsWith("http://") -> decoded.replaceFirst("http://", "https://")
            decoded.startsWith("https://") -> decoded
            decoded.startsWith("/") -> "https://www.albumaty.com$decoded"
            else -> "https://www.albumaty.com/$decoded"
        }
    }

    private fun stripHtml(value: String): String = value
        .replace(Regex("<script.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("<style.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()
}
