from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

RADIO = '''package com.example.studio

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.model.AudioItem
import com.example.onlinemusic.OnlineDeckTarget
import com.example.onlinemusic.OnlineDjBridge
import com.example.player.AudioPlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

private data class RadioStation(
    val id: String,
    val name: String,
    val streamUrls: List<String>,
    val tags: String,
    val codec: String,
    val bitrate: Int,
    val countryCode: String
)

private object RadioBrowserRepository {
    private const val BASE = "https://de1.api.radio-browser.info/json/stations/search"

    suspend fun stations(countryCode: String?, query: String): List<RadioStation> = withContext(Dispatchers.IO) {
        val params = buildString {
            append("hidebroken=true&lastcheckok=1&order=votes&reverse=true&limit=100")
            if (!countryCode.isNullOrBlank()) append("&countrycode=").append(URLEncoder.encode(countryCode, "UTF-8"))
            if (query.isNotBlank()) append("&name=").append(URLEncoder.encode(query, "UTF-8"))
        }
        val connection = (URL("$BASE?$params").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "DJ-Music-Player/1.0")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext emptyList()
            val json = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            buildList {
                for (i in 0 until json.length()) {
                    val item = json.optJSONObject(i) ?: continue
                    val resolved = item.optString("url_resolved").trim()
                    val raw = item.optString("url").trim()
                    val urls = listOf(resolved, raw).filter { it.startsWith("http://") || it.startsWith("https://") }.distinct()
                    if (urls.isEmpty()) continue
                    add(
                        RadioStation(
                            id = item.optString("stationuuid").ifBlank { urls.first() },
                            name = item.optString("name").ifBlank { "Radio" },
                            streamUrls = urls,
                            tags = item.optString("tags"),
                            codec = item.optString("codec").uppercase(),
                            bitrate = item.optInt("bitrate", 0),
                            countryCode = item.optString("countrycode")
                        )
                    )
                }
            }
        } finally { connection.disconnect() }
    }
}

private enum class RadioStatus { IDLE, LOADING, LIVE, FAILED }

private suspend fun resolvePlaylistUrl(url: String): String = withContext(Dispatchers.IO) {
    val lower = url.lowercase()
    if (!lower.substringBefore('?').endsWith(".m3u") && !lower.substringBefore('?').endsWith(".pls")) return@withContext url
    runCatching {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "DJ-Music-Player/1.0")
        }
        try {
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val candidate = body.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
            candidate ?: body.lineSequence()
                .map { it.substringAfter('=', "").trim() }
                .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
                ?: url
        } finally { c.disconnect() }
    }.getOrDefault(url)
}

private fun radioMime(codec: String, url: String): String? {
    val c = codec.lowercase()
    val u = url.lowercase()
    return when {
        c.contains("mp3") || c.contains("mpeg") || u.contains(".mp3") -> "audio/mpeg"
        c.contains("aac") || c.contains("aacp") || u.contains(".aac") -> "audio/aac"
        c.contains("ogg") || c.contains("vorbis") || u.contains(".ogg") -> "audio/ogg"
        c.contains("flac") || u.contains(".flac") -> "audio/flac"
        else -> null
    }
}

@Composable
fun MusicStudioScreen(@Suppress("UNUSED_PARAMETER") controller: MusicStudioController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playerController = remember { AudioPlayerController.obtain(context) }
    var country by remember { mutableStateOf("EG") }
    var search by remember { mutableStateOf("") }
    var refreshToken by remember { mutableIntStateOf(0) }
    var stations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var favorites by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val stationStatus = remember { mutableStateMapOf<String, RadioStatus>() }
    val attempts = remember { mutableStateMapOf<String, Int>() }
    var deckPicker by remember { mutableStateOf<RadioStation?>(null) }

    LaunchedEffect(country, search, refreshToken) {
        loading = true
        error = null
        try {
            val result = RadioBrowserRepository.stations(country, search.trim())
            stations = result
            stationStatus.clear()
            result.forEach { stationStatus[it.id] = RadioStatus.IDLE }
            if (result.isEmpty()) error = "لم يتم العثور على إذاعات متاحة حالياً"
        } catch (_: Exception) {
            stations = emptyList()
            error = "تعذر تحميل الإذاعات. تحقق من اتصال الإنترنت ثم حاول مرة أخرى."
        } finally { loading = false }
    }

    DisposableEffect(playerController) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val id = playerController.currentSong?.id ?: return
                if (!stationStatus.containsKey(id)) return
                stationStatus[id] = when (state) {
                    Player.STATE_BUFFERING -> RadioStatus.LOADING
                    Player.STATE_READY -> if (playerController.isPlaying) RadioStatus.LIVE else RadioStatus.IDLE
                    Player.STATE_ENDED -> RadioStatus.IDLE
                    else -> stationStatus[id] ?: RadioStatus.IDLE
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val id = playerController.currentSong?.id ?: return
                if (stationStatus.containsKey(id) && isPlaying) stationStatus[id] = RadioStatus.LIVE
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val id = playerController.currentSong?.id ?: return
                val station = stations.firstOrNull { it.id == id } ?: return
                val next = (attempts[id] ?: 0) + 1
                if (next < station.streamUrls.size) {
                    attempts[id] = next
                    stationStatus[id] = RadioStatus.LOADING
                    val scope = androidx.compose.runtime.rememberCoroutineScope
                } else {
                    stationStatus[id] = RadioStatus.FAILED
                }
            }
        }
        playerController.exoPlayer.addListener(listener)
        onDispose { playerController.exoPlayer.removeListener(listener) }
    }

    suspend fun playStation(station: RadioStation, requestedIndex: Int = 0) {
        var index = requestedIndex.coerceIn(0, station.streamUrls.lastIndex)
        stationStatus[station.id] = RadioStatus.LOADING
        attempts[station.id] = index
        while (index < station.streamUrls.size) {
            val resolvedUrl = resolvePlaylistUrl(station.streamUrls[index])
            val item = AudioItem(station.id, "📻 ${station.name}", if (station.countryCode == "EG") "إذاعة مصرية" else "Internet Radio", "Live Radio", 0L, Uri.parse(resolvedUrl))
            runCatching {
                playerController.playSong(item, listOf(item))
                val media = MediaItem.Builder().setUri(resolvedUrl).apply { radioMime(station.codec, resolvedUrl)?.let(::setMimeType) }.build()
                playerController.exoPlayer.setMediaItem(media)
                playerController.exoPlayer.prepare()
                playerController.exoPlayer.play()
            }.onSuccess { stationStatus[station.id] = RadioStatus.LOADING; return }
                .onFailure { index++; attempts[station.id] = index }
        }
        stationStatus[station.id] = RadioStatus.FAILED
    }

    val visibleStations = if (favorites.isEmpty()) stations else stations.sortedByDescending { favorites.contains(it.id) }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("📻 Radio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(if (country == "EG") "الإذاعات المصرية — Live" else "إذاعات من حول العالم — Live", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { refreshToken++ }) { Icon(Icons.Filled.Refresh, "تحديث") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = country == "EG", onClick = { country = "EG" }, label = { Text("🇪🇬 مصر") }, leadingIcon = { Icon(Icons.Filled.Radio, null, Modifier.size(18.dp)) })
            FilterChip(selected = country.isEmpty(), onClick = { country = "" }, label = { Text("🌍 العالم") }, leadingIcon = { Icon(Icons.Filled.Public, null, Modifier.size(18.dp)) })
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(24.dp), placeholder = { Text(if (country == "EG") "ابحث في الإذاعات المصرية..." else "ابحث عن محطة...") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, trailingIcon = if (search.isNotEmpty()) ({ IconButton(onClick = { search = "" }) { Icon(Icons.Filled.Close, "مسح") } }) else null)
        Spacer(Modifier.height(10.dp))
        if (loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Spacer(Modifier.height(10.dp)) }
        error?.let {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.WifiOff, null); Spacer(Modifier.width(8.dp)); Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer); TextButton(onClick = { refreshToken++ }) { Text("إعادة المحاولة") } }
            }
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxSize()) {
            items(visibleStations, key = { it.id }) { station ->
                val current = playerController.currentSong?.id == station.id
                val status = stationStatus[station.id] ?: RadioStatus.IDLE
                val isFavorite = favorites.contains(station.id)
                val statusText = when (status) {
                    RadioStatus.LOADING -> "تحميل البث..."
                    RadioStatus.LIVE -> "LIVE • شغال الآن"
                    RadioStatus.FAILED -> "فشل التحميل"
                    RadioStatus.IDLE -> "جاهزة للتشغيل"
                }
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(if (status == RadioStatus.LIVE) Icons.Filled.VolumeUp else Icons.Filled.Radio, null, tint = MaterialTheme.colorScheme.primary) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(station.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(buildString { if (station.tags.isNotBlank()) append(station.tags.take(45)); if (station.codec.isNotBlank()) { if (isNotEmpty()) append(" • "); append(station.codec) }; if (station.bitrate > 0) { append(" • "); append(station.bitrate); append(" kbps") } }.ifBlank { "بث مباشر" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(statusText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (status == RadioStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            }
                            if (status == RadioStatus.LOADING) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            IconButton(onClick = { favorites = if (isFavorite) favorites - station.id else favorites + station.id }) { Icon(if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, "المفضلة") }
                            IconButton(onClick = { deckPicker = station }) { Icon(Icons.Filled.Headset, "إرسال إلى DJ Deck") }
                            FilledIconButton(onClick = { if (status == RadioStatus.LIVE && current) playerController.pause() else androidx.compose.runtime.LaunchedEffect(Unit) { playStation(station) } }) { Icon(if (status == RadioStatus.LIVE && current) Icons.Filled.Pause else Icons.Filled.PlayArrow, "تشغيل") }
                        }
                    }
                }
            }
        }
    }

    deckPicker?.let { station ->
        AlertDialog(
            onDismissRequest = { deckPicker = null },
            title = { Text("تشغيل الإذاعة على أي Deck؟") },
            text = { Text(station.name) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        deckPicker = null
                        val item = AudioItem(station.id, "📻 ${station.name}", if (station.countryCode == "EG") "إذاعة مصرية" else "Internet Radio", "Live Radio", 0L, Uri.parse(station.streamUrls.first()))
                        OnlineDjBridge.send(item, OnlineDeckTarget.A)
                    }) { Text("Deck A") }
                    TextButton(onClick = {
                        deckPicker = null
                        val item = AudioItem(station.id, "📻 ${station.name}", if (station.countryCode == "EG") "إذاعة مصرية" else "Internet Radio", "Live Radio", 0L, Uri.parse(station.streamUrls.first()))
                        OnlineDjBridge.send(item, OnlineDeckTarget.B)
                    }) { Text("Deck B") }
                }
            }
        )
    }
}
'''

radio_path = ROOT / 'app/src/main/java/com/example/studio/MusicStudioScreen.kt'
radio_path.write_text(RADIO, encoding='utf-8')

# Rename the navigation label from Studio to Radio without changing its route.
main_path = ROOT / 'app/src/main/java/com/example/MainActivity.kt'
main = main_path.read_text(encoding='utf-8')
main = main.replace('contentDescription = "Studio"', 'contentDescription = "Radio"')
main = main.replace('label = { Text("Studio") }', 'label = { Text("Radio") }')
main_path.write_text(main, encoding='utf-8')

# Use Audius' current public API base instead of the legacy discovery-provider host.
audius_path = ROOT / 'app/src/main/java/com/example/onlinemusic/AudiusMusicRepository.kt'
audius = audius_path.read_text(encoding='utf-8')
audius = audius.replace('private const val DEFAULT_NODE = "https://discoveryprovider.audius.co/v1"', 'private const val DEFAULT_NODE = "https://api.audius.co/v1"')
audius_path.write_text(audius, encoding='utf-8')

# Ensure the foreign Play button always dispatches; the resolver is the authority on streamability.
online_path = ROOT / 'app/src/main/java/com/example/onlinemusic/OnlineMusicScreen.kt'
online = online_path.read_text(encoding='utf-8')
online = online.replace('IconButton(onClick = { onPlay(track) }, enabled = track.streamable)', 'IconButton(onClick = { onPlay(track) })')
online_path.write_text(online, encoding='utf-8')
