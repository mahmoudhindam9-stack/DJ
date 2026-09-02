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
        private const val BASE_URL = "https://www.albumaty.com"
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
        AlbumatySection(
            title = link.title,
            url = link.url,
            songs = parseSongLinks(html).take(100)
        )
    }

    suspend fun resolveTrack(song: AlbumatyLink): OnlineMusicTrack = withContext(Dispatchers.IO) {
        require(song.url.contains("/song/", ignoreCase = true)) { "الرابط المحدد ليس أغنية" }

        val songHtml = getHtml(song.url)
        val downloadPageUrl = extractDownloadPage(songHtml)
            ?: error("لم يتم العثور على صفحة تحميل الأغنية")
        val downloadHtml = getHtml(downloadPageUrl)
        val audioUrl = extractAudioUrl(downloadHtml)
            ?: extractAudioUrl(songHtml)
            ?: error("لم يتم العثور على رابط الصوت المباشر")

        OnlineMusicTrack(
            id = song.url,
            title = extractSongTitle(songHtml).ifBlank { song.title },
            artist = extractArtist(songHtml),
            album = extractAlbum(songHtml),
            artworkUrl = extractImageUrl(songHtml),
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
        return AlbumatyHomeData(
            categories = links.filter { it.isPath("cat") }
                .distinctBy { it.url }
                .take(60),
            albums = links.filter { it.isPath("album") }
                .distinctBy { it.url }
                .take(60),
            songs = links.filter { it.isPath("song") }
                .distinctBy { it.url }
                .take(60),
            artists = links.filter { it.isPath("singer") || it.isPath("artist") }
                .distinctBy { it.url }
                .take(120)
        )
    }

    private fun parseSongLinks(html: String): List<AlbumatyLink> = parseLinks(html)
        .filter { it.isPath("song") }
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
            if (!isAlbumatyUrl(url)) return@mapNotNull null
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

        val source = Regex(
            "<(?:audio|source)[^>]+src=[\\\"']([^\\\"']+)[\\\"']",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)
        if (source?.contains(".mp3", ignoreCase = true) == true) return normalizeUrl(source)

        val anchor = Regex(
            "<a[^>]+href=[\\\"']([^\\\"']+)[\\\"'][^>]*>[^<]*(?:تحميل|download)[^<]*</a>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)?.groupValues?.getOrNull(1)
        return anchor?.let(::normalizeUrl)?.takeIf { it.contains(".mp3", ignoreCase = true) }
    }

    private fun extractSongTitle(html: String): String {
        val heading = Regex(
            "<h1[^>]*>\\s*اغنية\\s+(.+?)\\s+MP3\\s*</h1>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)?.groupValues?.getOrNull(1)
        return stripHtml(heading.orEmpty()).substringBeforeLast(" - ").trim()
    }

    private fun extractArtist(html: String): String {
        val heading = Regex(
            "<h1[^>]*>\\s*اغنية\\s+(.+?)\\s+-\\s+(.+?)\\s+MP3\\s*</h1>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)?.groupValues?.getOrNull(2)
        return stripHtml(heading.orEmpty()).trim()
    }

    private fun extractAlbum(html: String): String? {
        val album = Regex(
            "اغاني\\s+اخرى\\s+من\\s+ألبوم\\s+([^<]+)",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(stripHtml(html))?.groupValues?.getOrNull(1)
        return album?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun extractImageUrl(html: String): String? {
        val image = Regex(
            "<img[^>]+(?:src|data-src)=[\\\"']([^\\\"']+)[\\\"']",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)
        return image?.let(::normalizeUrl)
    }

    private fun normalizeUrl(value: String): String {
        val decoded = URLDecoder.decode(value.replace("&amp;", "&"), StandardCharsets.UTF_8.name())
        return when {
            decoded.startsWith("http://", ignoreCase = true) -> decoded.replaceFirst("http://", "https://")
            decoded.startsWith("https://", ignoreCase = true) -> decoded.replace("https://albumaty.com", BASE_URL, ignoreCase = true)
                .replace("https://www.albumaty.com", BASE_URL, ignoreCase = true)
            decoded.startsWith("//") -> "https:$decoded"
            decoded.startsWith("/") -> "$BASE_URL$decoded"
            else -> "$BASE_URL/$decoded"
        }
    }

    private fun isAlbumatyUrl(url: String): Boolean = try {
        java.net.URI(url).host?.lowercase()?.removePrefix("www.") == "albumaty.com"
    } catch (_: Exception) {
        false
    }

    private fun AlbumatyLink.isPath(segment: String): Boolean = try {
        java.net.URI(url).path.orEmpty().split('/').contains(segment)
    } catch (_: Exception) {
        false
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
