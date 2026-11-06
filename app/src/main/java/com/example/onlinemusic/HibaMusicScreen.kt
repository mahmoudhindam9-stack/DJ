package com.example.onlinemusic

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.example.model.AudioItem
import com.example.player.AudioPlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

private data class HibaTrackLink(val title: String, val url: String)

private class HibaMusicRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val base = "https://www.hibamusic.com"
    private val agent = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"

    private suspend fun html(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", agent)
            .header("Accept-Language", "ar,en-US;q=0.9").header("Referer", base + "/ar").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Hiba Music HTTP " + response.code)
            response.body?.string().orEmpty()
        }
    }

    suspend fun home(): List<HibaTrackLink> = parse(html(base + "/ar"))

    suspend fun search(query: String): List<HibaTrackLink> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val pages = listOf(base + "/ar/recherche?q=" + encoded, base + "/ar?search=" + encoded)
        for (url in pages) {
            val result = runCatching { parse(html(url)) }.getOrDefault(emptyList())
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }

    suspend fun resolve(link: HibaTrackLink): String = withContext(Dispatchers.IO) {
        val page = html(link.url)
        val candidates = listOf(
            Regex("""<(?:audio|source)[^>]+(?:src|data-src|data-audio)=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""(?:audioUrl|audio_url|streamUrl|stream_url|mp3Url|file)\s*["']?\s*[:=]\s*["']([^"']+\.mp3[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""https?://[^\s"'<>]+\.mp3(?:\?[^\s"'<>]*)?""", RegexOption.IGNORE_CASE)
        )
        for (pattern in candidates) {
            val raw = pattern.find(page)?.groupValues?.getOrNull(1) ?: continue
            val absolute = when {
                raw.startsWith("http", true) -> raw
                raw.startsWith("//") -> "https:" + raw
                raw.startsWith("/") -> base + raw
                else -> base + "/" + raw
            }.replace("&amp;", "&")
            if (absolute.startsWith("https://")) return@withContext absolute
        }
        error("لم يعثر الموقع على رابط صوت مباشر لهذه الأغنية")
    }

    suspend fun download(audioUrl: String, resolver: android.content.ContentResolver, destination: Uri) =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(audioUrl).header("User-Agent", agent)
                .header("Referer", base + "/ar").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("تعذر تنزيل الملف: HTTP " + response.code)
                val body = response.body ?: error("ملف الصوت فارغ")
                resolver.openOutputStream(destination)?.use { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                } ?: error("تعذر فتح مكان الحفظ")
            }
        }

    private fun parse(page: String): List<HibaTrackLink> {
        val pattern = Regex("""<a\b[^>]+href=["']([^"']+)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val excluded = listOf("/genre/", "/artiste/", "/artist/", "/pays/", "/country/",
            "/decennie/", "/era/", "/selection/", "/playlist/", "/album/", "/archives")
        return pattern.findAll(page).mapNotNull { match ->
            val href = match.groupValues[1].replace("&amp;", "&").trim()
            val title = match.groupValues[2].replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ").trim()
            if (title.isBlank() || excluded.any { href.contains(it, true) }) return@mapNotNull null
            val absolute = when {
                href.startsWith("http", true) -> href
                href.startsWith("//") -> "https:" + href
                href.startsWith("/") -> base + href
                else -> base + "/" + href
            }.substringBefore("#")
            val path = runCatching { java.net.URI(absolute).path.orEmpty() }.getOrDefault("")
            val looksLikeTrack = listOf("/titre/", "/chanson/", "/track/", "/song/", "/ar/t/").any { path.contains(it, true) }
            if (!looksLikeTrack || !absolute.startsWith(base)) return@mapNotNull null
            HibaTrackLink(title, absolute)
        }.distinctBy { it.url }.take(150)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun HibaMusicScreen(
    playerController: AudioPlayerController,
    onShowQueue: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { HibaMusicRepository() }
    var tracks by remember { mutableStateOf<List<HibaTrackLink>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deckTrack by remember { mutableStateOf<HibaTrackLink?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/mpeg")) { uri ->
        val item = pending
        pending = null
        if (uri != null && item != null) scope.launch {
            message = "جاري تنزيل " + item.first
            runCatching { repository.download(item.second, context.contentResolver, uri) }
                .onSuccess { message = "تم تنزيل الملف" }
                .onFailure { message = it.message ?: "فشل التنزيل" }
        }
    }

    suspend fun refresh(term: String) {
        loading = true
        message = null
        runCatching {
            if (term.isBlank()) repository.home() else repository.search(term)
        }.onSuccess { tracks = it }
            .onFailure { message = it.message ?: "تعذر تحميل مكتبة Hiba Music" }
        loading = false
    }

    LaunchedEffect(Unit) { refresh("") }
    LaunchedEffect(query) {
        delay(400)
        if (query.trim().isNotEmpty()) refresh(query.trim()) else if (tracks.isEmpty()) refresh("")
    }

    fun play(track: HibaTrackLink) {
        scope.launch {
            val current = playerController.currentSong
            if (current?.id == "hiba:" + track.url) {
                playerController.togglePlayPause()
                return@launch
            }
            message = "جاري تجهيز الأغنية..."
            runCatching {
                val audioUrl = repository.resolve(track)
                AudioItem("hiba:" + track.url, track.title, "Hiba Music", "Oldies & Classics", 0L, Uri.parse(audioUrl))
            }.onSuccess { item ->
                playerController.play(item, listOf(item))
                message = "يعمل الآن: " + item.title
            }.onFailure { message = it.message ?: "تعذر تشغيل الأغنية" }
        }
    }

    fun queue(track: HibaTrackLink) {
        scope.launch {
            runCatching {
                val url = repository.resolve(track)
                AudioItem("hiba:" + track.url, track.title, "Hiba Music", "Oldies & Classics", 0L, Uri.parse(url))
            }.onSuccess {
                playerController.enqueueOnlineSong(it)
                message = "أضيفت إلى قائمة التشغيل"
            }.onFailure { message = it.message ?: "تعذر إضافة الأغنية" }
        }
    }

    fun download(track: HibaTrackLink) {
        scope.launch {
            runCatching { repository.resolve(track) }.onSuccess { url ->
                pending = track.title to url
                saveLauncher.launch(track.title.replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".mp3")
            }.onFailure { message = it.message ?: "تعذر تجهيز التنزيل" }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LibraryMusic, null, Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Text("Oldies & Classics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { scope.launch { refresh(query.trim()) } }) { Icon(Icons.Filled.Refresh, "Refresh") }
            IconButton(onClick = onShowQueue) { Icon(Icons.Filled.QueueMusic, "Queue") }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            singleLine = true, label = { Text("ابحث في أرشيف الأغاني القديمة") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
        if (loading && tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(message ?: "لا توجد نتائج متاحة")
                    TextButton(onClick = { scope.launch { refresh(query.trim()) } }) { Text("إعادة المحاولة") }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(tracks, key = { it.url }) { track ->
                    val active = playerController.currentSong?.id == "hiba:" + track.url
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.MusicNote, null, Modifier.size(30.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(track.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { play(track) }) { Icon(if (active && playerController.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play") }
                            IconButton(onClick = { queue(track) }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to queue") }
                            IconButton(onClick = { download(track) }) { Icon(Icons.Filled.Download, "Download") }
                        }
                    }
                }
                message?.let { item { Text(it, style = MaterialTheme.typography.bodySmall) } }
            }
        }
    }
}
