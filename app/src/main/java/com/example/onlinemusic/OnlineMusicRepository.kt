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

    private val client = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()

    private suspend fun getHtml(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) DJ Music Player")
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Albumaty returned ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    suspend fun getHome(): AlbumatyHomeData = withContext(Dispatchers.IO) { parseHome(getHtml(HOME_URL)) }

    suspend fun getSection(link: AlbumatyLink): AlbumatySection = withContext(Dispatchers.IO) {
        val html = getHtml(link.url)
        val type = pageType(link.url)
        val content = parseSectionContent(html, type)
            .filterNot { it.url.trimEnd('/') == link.url.trimEnd('/') }
            .distinctBy { it.url }
            .take(500)
        AlbumatySection(link.title, link.url, content)
    }

    suspend fun resolveTrack(song: AlbumatyLink): OnlineMusicTrack = withContext(Dispatchers.IO) {
        require(song.isSong()) { "الرابط المحدد ليس أغنية" }
        val songHtml = getHtml(song.url)
        val downloadPageUrl = extractDownloadPage(songHtml) ?: error("لم يتم العثور على صفحة تحميل الأغنية")
        val downloadHtml = getHtml(downloadPageUrl)
        val audioUrl = extractAudioUrl(downloadHtml) ?: extractAudioUrl(songHtml) ?: error("لم يتم العثور على رابط الصوت المباشر")
        OnlineMusicTrack(song.url, extractSongTitle(songHtml).ifBlank { song.title }, extractArtist(songHtml), extractAlbum(songHtml), extractImageUrl(songHtml), audioUrl, audioUrl)
    }

    suspend fun downloadToUri(audioUrl: String, resolver: ContentResolver, destination: Uri): Long = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(audioUrl)
            .header("User-Agent", "Mozilla/5.0 (Android) DJ Music Player")
            .header("Referer", "$BASE_URL/").build()
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
        (home.albums + home.songs + home.artists + home.categories).filter { it.title.contains(q, true) }.distinctBy { it.url }
    }

    private fun parseHome(html: String): AlbumatyHomeData {
        val links = parseLinks(html)
        return AlbumatyHomeData(
            categories = links.filter { it.isCategory() }.distinctBy { it.url }.take(100),
            albums = links.filter { it.isAlbum() }.distinctBy { it.url }.take(100),
            songs = links.filter { it.isSong() }.distinctBy { it.url }.take(100),
            artists = links.filter { it.isArtist() }.distinctBy { it.url }.take(300)
        )
    }

    /**
     * Parse only the page's own content area.
     *
     * Albumaty repeats the global navigation, latest albums/songs and artist lists on
     * many pages. Parsing every <a> on the document made those unrelated links leak
     * into a selected category/artist/album screen. The first h1 marks the main page
     * content on Albumaty, so we scope parsing to that region and then apply a stricter
     * album rule so an album screen contains only its own songs.
     */
    private fun parseSectionContent(html: String, type: String): List<AlbumatyLink> {
        val mainHtml = extractMainContentHtml(html)
        if (mainHtml.isBlank()) return emptyList()

        return when (type) {
            "album" -> parseLinks(mainHtml)
                .filter { it.isSong() }
            "singer", "artist" -> parseLinks(mainHtml)
                .filter { it.isAlbum() || it.isSong() }
            "lastalbums" -> parseLinks(mainHtml)
                .filter { it.isAlbum() }
            "cat", "category" -> parseLinks(mainHtml)
                .filter { it.isSong() || it.isAlbum() || it.isArtist() }
            else -> parseLinks(mainHtml)
                .filter { it.isSong() || it.isAlbum() || it.isArtist() }
        }
    }

    private fun extractMainContentHtml(html: String): String {
        val h1 = Regex("<h1\\b[^>]*>", RegexOption.IGNORE_CASE).find(html) ?: return html
        val start = h1.range.first

        val footerStart = Regex(
            "<(?:footer|\\/footer)\\b|(?:اتصل بنا|contact us|about us|جميع الحقوق محفوظة)",
            RegexOption.IGNORE_CASE
        ).find(html, start + h1.value.length)?.range?.first ?: html.length

        if (footerStart <= start) return html.substring(start)
        return html.substring(start, footerStart)
    }

    private fun parseLinks(html: String): List<AlbumatyLink> {
        val linkRegex = Regex("<a[^>]+href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        return linkRegex.findAll(html).mapNotNull { match ->
            val title = stripHtml(match.groupValues[2])
            if (title.isBlank()) return@mapNotNull null
            val url = normalizeUrl(match.groupValues[1].trim())
            if (!isAlbumatyUrl(url)) return@mapNotNull null
            AlbumatyLink(title, url)
        }.toList()
    }

    private fun extractDownloadPage(html: String): String? = Regex("<a[^>]+href=[\\\"']([^\\\"']*?/download/[^\\\"']+)[\\\"'][^>]*>", RegexOption.IGNORE_CASE)
        .find(html)?.groupValues?.getOrNull(1)?.let(::normalizeUrl)

    private fun extractAudioUrl(html: String): String? {
        Regex("https?://[^\\\"'<>\\s]+\\.mp3(?:\\?[^\\\"'<>\\s]*)?", RegexOption.IGNORE_CASE).find(html)?.value?.let { return normalizeUrl(it) }
        Regex("<(?:audio|source)[^>]+src=[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1)?.let { if (it.contains(".mp3", true)) return normalizeUrl(it) }
        return Regex("<a[^>]+href=[\\\"']([^\\\"']+)[\\\"'][^>]*>[^<]*(?:تحميل|download)[^<]*</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.getOrNull(1)?.let(::normalizeUrl)?.takeIf { it.contains(".mp3", true) }
    }

    private fun extractSongTitle(html: String): String = Regex("<h1[^>]*>\\s*اغنية\\s+(.+?)\\s+MP3\\s*</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .find(html)?.groupValues?.getOrNull(1)?.let { stripHtml(it).substringBeforeLast(" - ").trim() }.orEmpty()

    private fun extractArtist(html: String): String = Regex("<h1[^>]*>\\s*اغنية\\s+(.+?)\\s+-\\s+(.+?)\\s+MP3\\s*</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .find(html)?.groupValues?.getOrNull(2)?.let(::stripHtml)?.trim().orEmpty()

    private fun extractAlbum(html: String): String? = Regex("اغاني\\s+اخرى\\s+من\\s+ألبوم\\s+([^<]+)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .find(stripHtml(html))?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }

    private fun extractImageUrl(html: String): String? = Regex("<img[^>]+(?:src|data-src)=[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE)
        .find(html)?.groupValues?.getOrNull(1)?.let(::normalizeUrl)

    private fun normalizeUrl(value: String): String {
        val decoded = URLDecoder.decode(value.replace("&amp;", "&"), StandardCharsets.UTF_8.name())
        return when {
            decoded.startsWith("http://", true) -> decoded.replaceFirst("http://", "https://")
            decoded.startsWith("https://", true) -> decoded.replace("https://albumaty.com", BASE_URL, true).replace("https://www.albumaty.com", BASE_URL, true)
            decoded.startsWith("//") -> "https:$decoded"
            decoded.startsWith("/") -> "$BASE_URL$decoded"
            else -> "$BASE_URL/$decoded"
        }
    }

    private fun isAlbumatyUrl(url: String): Boolean = try { java.net.URI(url).host?.lowercase()?.removePrefix("www.") == "albumaty.com" } catch (_: Exception) { false }

    private fun path(url: String): String = try { java.net.URI(url).path.orEmpty().trim('/').lowercase() } catch (_: Exception) { "" }
    private fun pageType(url: String): String = path(url).split('/').firstOrNull().orEmpty()
    private fun AlbumatyLink.isSong(): Boolean = pageType(url) == "song"
    private fun AlbumatyLink.isAlbum(): Boolean = pageType(url) == "album"
    private fun AlbumatyLink.isArtist(): Boolean = pageType(url) == "singer" || pageType(url) == "artist"
    private fun AlbumatyLink.isCategory(): Boolean = pageType(url) == "cat" || pageType(url) == "category"

    private fun stripHtml(value: String): String = value
        .replace(Regex("<script.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("<style.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace(Regex("\\s+"), " ").trim()
}
