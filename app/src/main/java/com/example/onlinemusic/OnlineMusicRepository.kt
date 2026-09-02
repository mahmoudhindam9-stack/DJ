package com.example.onlinemusic

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

    private val client = OkHttpClient.Builder().build()

    suspend fun getHome(): AlbumatyHomeData = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(HOME_URL)
            .header("User-Agent", "Mozilla/5.0 (Android) DJ Music Player")
            .build()
        val html = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Albumaty returned ${response.code}")
            response.body?.string().orEmpty()
        }
        parseHome(html)
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
        val linkRegex = Regex("<a[^>]+href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL)
        val links = linkRegex.findAll(html).mapNotNull { match ->
            val rawHref = match.groupValues[1].trim()
            val rawText = match.groupValues[2]
            val title = stripHtml(rawText)
            if (title.isBlank()) return@mapNotNull null
            val url = normalizeUrl(rawHref)
            if (!url.startsWith("https://www.albumaty.com/")) return@mapNotNull null
            AlbumatyLink(title = title, url = url)
        }.toList()

        fun unique(prefix: String): List<AlbumatyLink> = links
            .filter { it.url.contains("/$prefix/") }
            .distinctBy { it.url }
            .take(24)

        return AlbumatyHomeData(
            categories = unique("cat"),
            albums = unique("album"),
            songs = unique("song"),
            artists = unique("artist")
        )
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
        .replace(Regex("<script.*?</script>", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("<style.*?</style>", RegexOption.IGNORE_CASE or RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()
}
