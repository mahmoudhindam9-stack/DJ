package com.example.onlinemusic

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class OnlineMusicRepository {
    companion object {
        const val HOME_URL = "https://www.albumaty.com/cat/1.html"
        private const val BASE_URL = "https://www.albumaty.com"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private val client = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()

    private suspend fun getHtml(url: String): String = withContext(Dispatchers.IO) {
        val safeUrl = encodeUrlSafely(url)
        val request = Request.Builder()
            .url(safeUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "ar,en-US;q=0.9,en;q=0.8")
            .header("Referer", "$BASE_URL/")
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
            .ifEmpty {
                parseLinks(html).filter {
                    when (type) {
                        "cat", "category" -> it.isSong() || it.isAlbum() || it.isArtist()
                        "album" -> it.isSong()
                        "singer", "artist" -> it.isAlbum() || it.isSong()
                        "lastalbums" -> it.isAlbum()
                        else -> it.isSong() || it.isAlbum() || it.isArtist()
                    }
                }
            }
            .filterNot { it.url.trimEnd('/') == link.url.trimEnd('/') }
            .distinctBy { it.url }
            .take(500)
        AlbumatySection(link.title, link.url, content)
    }

    suspend fun resolveTrack(song: AlbumatyLink): OnlineMusicTrack = withContext(Dispatchers.IO) {
        val songUrl = normalizeUrl(song.url)
        val songHtml = getHtml(songUrl)

        // 1. Direct audio URL from song page (where Albumaty embeds it)
        var audioUrl = extractAudioUrl(songHtml)

        // 2. If not found, try download page if one exists
        if (audioUrl == null) {
            val downloadPageUrl = extractDownloadPage(songHtml)
            if (downloadPageUrl != null) {
                runCatching {
                    val downloadHtml = getHtml(downloadPageUrl)
                    audioUrl = extractAudioUrl(downloadHtml)
                }
            }
        }

        // 3. Fallback: check any .mp3 link in the song page
        if (audioUrl == null) {
            audioUrl = extractAnyMp3(songHtml)
        }

        val directAudioUrl = audioUrl ?: error("لم يتم العثور على رابط تشغيل الأغنية المباشر")
        val safeAudioUrl = encodeUrlSafely(directAudioUrl)

        val (h1Title, h1Artist) = extractTitleAndArtistFromH1(songHtml)
        val title = h1Title.ifBlank { extractSongTitle(songHtml).ifBlank { song.title } }
        val artist = h1Artist.ifBlank { extractArtist(songHtml).ifBlank { "Albumaty" } }
        val album = extractAlbum(songHtml)
        val imageUrl = extractImageUrl(songHtml)

        OnlineMusicTrack(
            id = song.url,
            title = title,
            artist = artist,
            album = album,
            artworkUrl = imageUrl,
            streamUrl = safeAudioUrl,
            downloadUrl = safeAudioUrl
        )
    }

    suspend fun downloadToUri(audioUrl: String, resolver: ContentResolver, destination: Uri): Long = withContext(Dispatchers.IO) {
        val safeUrl = encodeUrlSafely(audioUrl)
        val request = Request.Builder().url(safeUrl)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "$BASE_URL/")
            .header("Accept", "*/*")
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
        val q = query.trim()
        if (q.isBlank()) return@withContext getHome().songs

        val remoteResults = runCatching {
            val encodedQuery = URLEncoder.encode(q, StandardCharsets.UTF_8.name())
            val searchUrl = "$BASE_URL/search.php?q=$encodedQuery"
            val html = getHtml(searchUrl)
            val mainContent = extractMainContentHtml(html)
            parseLinks(mainContent).filter { it.isSong() || it.isAlbum() || it.isArtist() }
        }.getOrDefault(emptyList())

        if (remoteResults.isNotEmpty()) {
            return@withContext remoteResults.distinctBy { it.url }
        }

        val home = getHome()
        (home.albums + home.songs + home.artists + home.categories)
            .filter { it.title.contains(q, true) }
            .distinctBy { it.url }
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

    private fun parseSectionContent(html: String, type: String): List<AlbumatyLink> {
        val mainHtml = extractMainContentHtml(html)
        if (mainHtml.isBlank()) return emptyList()

        return when (type) {
            "album" -> parseLinks(mainHtml).filter { it.isSong() }
            "singer", "artist" -> parseLinks(mainHtml).filter { it.isAlbum() || it.isSong() }
            "lastalbums" -> parseLinks(mainHtml).filter { it.isAlbum() }
            "cat", "category" -> parseLinks(mainHtml).filter { it.isSong() || it.isAlbum() || it.isArtist() }
            else -> parseLinks(mainHtml).filter { it.isSong() || it.isAlbum() || it.isArtist() }
        }
    }

    private fun extractMainContentHtml(html: String): String {
        val h1 = Regex("<h1\\b[^>]*>", RegexOption.IGNORE_CASE).find(html) ?: return html
        val start = h1.range.first

        val footerStart = Regex(
            "<(?:footer|/footer)\\b|(?:اتصل بنا|contact us|about us|جميع الحقوق محفوظة)",
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

    private fun extractDownloadPage(html: String): String? {
        val hrefRegex = Regex("<a[^>]+href=[\\\"']([^\\\"']+)[\\\"'][^>]*>", RegexOption.IGNORE_CASE)
        return hrefRegex.findAll(html)
            .map { it.groupValues[1] }
            .firstOrNull { it.contains("/download/", true) || it.contains("download", true) }
            ?.let(::normalizeUrl)
    }

    private fun extractAudioUrl(html: String): String? {
        // 1. Check itemprop="contentUrl" or itemprop="url" with .mp3
        Regex("""<meta[^>]+itemprop=["'](?:contentUrl|url)["'][^>]+content=["']([^"']+\.mp3[^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.let { return normalizeUrl(it) }

        // 2. Check <audio ... src="..."> or <source ... src="..."> or data-src
        Regex("""<(?:audio|source)[^>]+(?:src|data-src)=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.let {
                if (it.contains(".mp3", ignoreCase = true) || it.contains("serv", ignoreCase = true)) return normalizeUrl(it)
            }

        // 3. Check direct mp3 URL
        Regex("""https?://[^\s"'<>]+\.mp3(?:\?[^\s"'<>]*)?""", RegexOption.IGNORE_CASE)
            .find(html)?.value?.let { return normalizeUrl(it) }

        // 4. Check download anchors
        return Regex("<a[^>]+href=[\\\"']([^\\\"']+)[\\\"'][^>]*>[^<]*(?:تحميل|download)[^<]*</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.getOrNull(1)?.let(::normalizeUrl)?.takeIf { it.contains(".mp3", true) }
    }

    private fun extractAnyMp3(html: String): String? {
        return Regex("""https?://[^\s"'<>]+\.mp3(?:\?[^\s"'<>]*)?""", RegexOption.IGNORE_CASE)
            .find(html)?.value?.let(::normalizeUrl)
    }

    private fun extractTitleAndArtistFromH1(html: String): Pair<String, String> {
        val h1Match = Regex("<h1[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
            ?: return Pair("", "")
        var text = stripHtml(h1Match.groupValues[1])
        text = text.replace(Regex("^(?:اغنية|أغنية)\\s*", RegexOption.IGNORE_CASE), "").trim()
        text = text.replace(Regex("\\s*MP3\\s*$", RegexOption.IGNORE_CASE), "").trim()

        return if (text.contains(" - ")) {
            val parts = text.split(" - ", limit = 2)
            Pair(parts[0].trim(), parts.getOrNull(1)?.trim().orEmpty())
        } else {
            Pair(text, "")
        }
    }

    private fun extractSongTitle(html: String): String =
        Regex("<h1[^>]*>\\s*اغنية\\s+(.+?)\\s+MP3\\s*</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.getOrNull(1)?.let { stripHtml(it).substringBeforeLast(" - ").trim() }.orEmpty()

    private fun extractArtist(html: String): String {
        val schemaArtist = Regex("""itemprop=["']byArtist["'][^>]*>.*?<span[^>]*itemprop=["']name["'][^>]*>(.*?)</span>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.getOrNull(1)?.let(::stripHtml)?.trim()
        if (!schemaArtist.isNullOrBlank()) return schemaArtist

        return Regex("<h1[^>]*>\\s*اغنية\\s+(.+?)\\s+-\\s+(.+?)\\s+MP3\\s*</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.getOrNull(2)?.let(::stripHtml)?.trim().orEmpty()
    }

    private fun extractAlbum(html: String): String? =
        Regex("اغاني\\s+اخرى\\s+من\\s+ألبوم\\s+([^<]+)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(stripHtml(html))?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }

    private fun extractImageUrl(html: String): String? {
        val metaImg = Regex("""<meta[^>]+itemprop=["']image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.let(::normalizeUrl)
        if (!metaImg.isNullOrBlank() && !metaImg.contains("logo.png") && !metaImg.contains("empty.png")) {
            return metaImg
        }

        return Regex("<img[^>]+(?:src|data-src)=[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.let(::normalizeUrl)
    }

    private fun encodeUrlSafely(rawUrl: String): String {
        return try {
            val u = URL(rawUrl)
            val decodedPath = URLDecoder.decode(u.path, StandardCharsets.UTF_8.name())
            val uri = URI(u.protocol, u.authority, decodedPath, u.query, u.ref)
            uri.toASCIIString()
        } catch (_: Exception) {
            rawUrl
        }
    }

    private fun normalizeUrl(value: String): String {
        val safe = value.replace("&amp;", "&").trim()
        val decoded = runCatching { URLDecoder.decode(safe, StandardCharsets.UTF_8.name()) }.getOrDefault(safe)
        return when {
            decoded.startsWith("http://", true) -> decoded.replaceFirst("http://", "https://")
            decoded.startsWith("https://", true) -> decoded.replace("https://albumaty.com", BASE_URL, true).replace("https://www.albumaty.com", BASE_URL, true)
            decoded.startsWith("//") -> "https:$decoded"
            decoded.startsWith("/") -> "$BASE_URL$decoded"
            else -> "$BASE_URL/$decoded"
        }
    }

    private fun isAlbumatyUrl(url: String): Boolean = try {
        URI(url).host?.lowercase()?.removePrefix("www.") == "albumaty.com"
    } catch (e: Exception) {
        android.util.Log.w("OnlineMusicRepository", "Caught exception", e)
        false
    }

    private fun pathSegments(url: String): List<String> = try {
        URI(url).path.orEmpty()
            .trim('/')
            .lowercase()
            .split('/')
            .filter { it.isNotBlank() }
    } catch (e: Exception) {
        android.util.Log.w("OnlineMusicRepository", "Caught exception", e)
        emptyList()
    }

    private fun pageType(url: String): String {
        val segments = pathSegments(url)
        return segments.firstOrNull {
            it == "song" || it.startsWith("song") ||
                it == "album" || it.startsWith("album") ||
                it == "singer" || it == "artist" ||
                it == "cat" || it == "category" ||
                it == "lastalbums"
        } ?: segments.firstOrNull().orEmpty()
    }

    private fun AlbumatyLink.isSong(): Boolean = pathSegments(url).any { it == "song" || it.startsWith("song") }
    private fun AlbumatyLink.isAlbum(): Boolean = pathSegments(url).any { it == "album" || it.startsWith("album") }
    private fun AlbumatyLink.isArtist(): Boolean = pathSegments(url).any { it == "singer" || it == "artist" }
    private fun AlbumatyLink.isCategory(): Boolean = pathSegments(url).any { it == "cat" || it == "category" }

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