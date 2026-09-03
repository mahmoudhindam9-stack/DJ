package com.example.studio

import android.net.Uri
import androidx.compose.foundation.background
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private data class RadioStation(
    val id: String,
    val name: String,
    val streamUrls: List<String>,
    val tags: String,
    val codec: String,
    val bitrate: Int,
    val countryCode: String
)

private enum class RadioStatus { IDLE, LOADING, LIVE, FAILED }

private object RadioBrowserRepository {
    private const val BASE = "https://de1.api.radio-browser.info/json/stations/search"

    suspend fun stations(countryCode: String?, query: String): List<RadioStation> = withContext(Dispatchers.IO) {
        val params = buildString {
            append("hidebroken=true")
            append("&lastcheckok=1")
            append("&order=votes")
            append("&reverse=true")
            append("&limit=100")
            if (!countryCode.isNullOrBlank()) {
                append("&countrycode=")
                append(URLEncoder.encode(countryCode, "UTF-8"))
            }
            if (query.isNotBlank()) {
                append("&name=")
                append(URLEncoder.encode(query, "UTF-8"))
            }
        }

        val connection = (URL("$BASE?$params").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "DJ-Music-Player/1.0")
            setRequestProperty("Accept", "application/json")
        }

        try {
            if (connection.responseCode !in 200..299) return@withContext emptyList()
            val array = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val resolved = item.optString("url_resolved").trim()
                    val raw = item.optString("url").trim()
                    val urls = listOf(resolved, raw)
                        .filter { it.startsWith("http://") || it.startsWith("https://") }
                        .distinct()
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
        } finally {
            connection.disconnect()
        }
    }
}

private suspend fun resolvePlaylistUrl(url: String): String = withContext(Dispatchers.IO) {
    val path = url.substringBefore('?').lowercase()
    if (!path.endsWith(".m3u") && !path.endsWith(".pls")) return@withContext url

    runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "DJ-Music-Player/1.0")
        }
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            body.lineSequence()
                .map { line -> line.substringAfter('=', line).trim() }
                .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
                ?: url
        } finally {
            connection.disconnect()
        }
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
    val scope = rememberCoroutineScope()

    var country by remember { mutableStateOf("EG") }
    var search by remember { mutableStateOf("") }
    var refreshToken by remember { mutableIntStateOf(0) }
    var stations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var deckPicker by remember { mutableStateOf<RadioStation?>(null) }
    val statuses = remember { mutableStateMapOf<String, RadioStatus>() }
    val attempts = remember { mutableStateMapOf<String, Int>() }

    LaunchedEffect(country, search, refreshToken) {
        loading = true
        error = null
        runCatching { RadioBrowserRepository.stations(country, search.trim()) }
            .onSuccess { result ->
                stations = result
                statuses.clear()
                result.forEach { station -> statuses[station.id] = RadioStatus.IDLE }
                if (result.isEmpty()) error = "لم يتم العثور على إذاعات متاحة حالياً"
            }
            .onFailure {
                stations = emptyList()
                error = "تعذر تحميل الإذاعات. تحقق من اتصال الإنترنت ثم حاول مرة أخرى."
            }
        loading = false
    }

    suspend fun playStation(station: RadioStation, requestedIndex: Int = 0) {
        var index = requestedIndex.coerceIn(0, station.streamUrls.lastIndex)
        statuses[station.id] = RadioStatus.LOADING
        attempts[station.id] = index

        while (index < station.streamUrls.size) {
            val resolvedUrl = resolvePlaylistUrl(station.streamUrls[index])
            val audio = AudioItem(
                station.id,
                "📻 ${station.name}",
                if (station.countryCode == "EG") "إذاعة مصرية" else "Internet Radio",
                "Live Radio",
                0L,
                Uri.parse(resolvedUrl)
            )

            try {
                playerController.playSong(audio, listOf(audio))
                val mediaItem = MediaItem.Builder()
                    .setUri(resolvedUrl)
                    .apply {
                        radioMime(station.codec, resolvedUrl)?.let { setMimeType(it) }
                    }
                    .build()
                playerController.exoPlayer.setMediaItem(mediaItem)
                playerController.exoPlayer.prepare()
                playerController.exoPlayer.play()
                statuses[station.id] = RadioStatus.LOADING
                return
            } catch (_: Throwable) {
                index += 1
                attempts[station.id] = index
            }
        }

        statuses[station.id] = RadioStatus.FAILED
    }

    DisposableEffect(playerController) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val id = playerController.currentSong?.id ?: return
                if (!statuses.containsKey(id)) return
                statuses[id] = when (state) {
                    Player.STATE_BUFFERING -> RadioStatus.LOADING
                    Player.STATE_READY -> if (playerController.isPlaying) RadioStatus.LIVE else RadioStatus.IDLE
                    Player.STATE_ENDED -> RadioStatus.IDLE
                    else -> statuses[id] ?: RadioStatus.IDLE
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val id = playerController.currentSong?.id ?: return
                if (statuses.containsKey(id)) {
                    statuses[id] = if (isPlaying) RadioStatus.LIVE else RadioStatus.IDLE
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val id = playerController.currentSong?.id ?: return
                val station = stations.firstOrNull { it.id == id } ?: return
                val next = (attempts[id] ?: 0) + 1
                if (next < station.streamUrls.size) {
                    statuses[id] = RadioStatus.LOADING
                    scope.launch { playStation(station, next) }
                } else {
                    statuses[id] = RadioStatus.FAILED
                }
            }
        }

        playerController.exoPlayer.addListener(listener)
        onDispose { playerController.exoPlayer.removeListener(listener) }
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("📻 Radio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (country == "EG") "الإذاعات المصرية — Live" else "إذاعات من حول العالم — Live",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { refreshToken++ }) { Icon(Icons.Filled.Refresh, "تحديث") }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = country == "EG",
                onClick = { country = "EG" },
                label = { Text("🇪🇬 مصر") },
                leadingIcon = { Icon(Icons.Filled.Radio, null, Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = country.isEmpty(),
                onClick = { country = "" },
                label = { Text("🌍 العالم") },
                leadingIcon = { Icon(Icons.Filled.Public, null, Modifier.size(18.dp)) }
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            placeholder = { Text("ابحث عن محطة...") },
            leadingIcon = { Icon(Icons.Filled.Search, null) }
        )

        Spacer(Modifier.height(8.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { message ->
            Card(Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WifiOff, null)
                    Spacer(Modifier.width(8.dp))
                    Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = { refreshToken++ }) { Text("إعادة المحاولة") }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxSize()) {
            items(stations, key = { it.id }) { station ->
                val status = statuses[station.id] ?: RadioStatus.IDLE
                val current = playerController.currentSong?.id == station.id
                val label = when (status) {
                    RadioStatus.LOADING -> "تحميل البث..."
                    RadioStatus.LIVE -> "LIVE • شغال الآن"
                    RadioStatus.FAILED -> "فشل التحميل"
                    RadioStatus.IDLE -> "جاهزة للتشغيل"
                }

                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (status == RadioStatus.LIVE) Icons.Filled.VolumeUp else Icons.Filled.Radio, null)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(station.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(station.codec.takeIf { it.isNotBlank() }, station.bitrate.takeIf { it > 0 }?.let { "$it kbps" })
                                    .filterNotNull().joinToString(" • ").ifBlank { station.tags.take(45).ifBlank { "بث مباشر" } },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        if (status == RadioStatus.LOADING) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        IconButton(onClick = { deckPicker = station }) { Icon(Icons.Filled.Headset, "إرسال إلى DJ Deck") }
                        FilledIconButton(onClick = {
                            if (current && status == RadioStatus.LIVE) playerController.pause()
                            else scope.launch { playStation(station) }
                        }) {
                            Icon(if (current && status == RadioStatus.LIVE) Icons.Filled.Pause else Icons.Filled.PlayArrow, "تشغيل")
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
                        val song = AudioItem(
                            station.id, "📻 ${station.name}",
                            if (station.countryCode == "EG") "إذاعة مصرية" else "Internet Radio",
                            "Live Radio", 0L, Uri.parse(station.streamUrls.first())
                        )
                        OnlineDjBridge.send(song, OnlineDeckTarget.A)
                    }) { Text("Deck A") }
                    TextButton(onClick = {
                        deckPicker = null
                        val song = AudioItem(
                            station.id, "📻 ${station.name}",
                            if (station.countryCode == "EG") "إذاعة مصرية" else "Internet Radio",
                            "Live Radio", 0L, Uri.parse(station.streamUrls.first())
                        )
                        OnlineDjBridge.send(song, OnlineDeckTarget.B)
                    }) { Text("Deck B") }
                }
            }
        )
    }
}
