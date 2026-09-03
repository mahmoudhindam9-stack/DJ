package com.example.studio

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.example.model.AudioItem
import com.example.player.AudioPlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

private data class RadioStation(val id: String, val name: String, val streamUrl: String, val tags: String, val codec: String, val bitrate: Int, val countryCode: String)

private object RadioBrowserRepository {
    private const val BASE = "https://de1.api.radio-browser.info/json/stations/search"
    suspend fun stations(countryCode: String?, query: String): List<RadioStation> = withContext(Dispatchers.IO) {
        val params = buildString {
            append("hidebroken=true&order=votes&reverse=true&limit=80")
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
                    val stream = item.optString("url_resolved").ifBlank { item.optString("url") }
                    if (stream.isBlank()) continue
                    add(RadioStation(
                        id = item.optString("stationuuid").ifBlank { stream },
                        name = item.optString("name").ifBlank { "Radio" },
                        streamUrl = stream,
                        tags = item.optString("tags"),
                        codec = item.optString("codec").uppercase(),
                        bitrate = item.optInt("bitrate", 0),
                        countryCode = item.optString("countrycode")
                    ))
                }
            }
        } finally { connection.disconnect() }
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

    LaunchedEffect(country, search, refreshToken) {
        loading = true
        error = null
        try {
            val result = RadioBrowserRepository.stations(country, search.trim())
            stations = result
            if (result.isEmpty()) error = "لم يتم العثور على إذاعات متاحة حالياً"
        } catch (_: Exception) {
            stations = emptyList()
            error = "تعذر تحميل الإذاعات. تحقق من اتصال الإنترنت ثم حاول مرة أخرى."
        } finally { loading = false }
    }

    val visibleStations = if (favorites.isEmpty()) stations else stations.sortedByDescending { favorites.contains(it.id) }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("📻 Radio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(if (country == "EG") "الإذاعات المصرية — الافتراضي" else "إذاعات من حول العالم", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { refreshToken++ }) { Icon(Icons.Filled.Refresh, "تحديث") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = country == "EG", onClick = { country = "EG" }, label = { Text("🇪🇬 مصر") }, leadingIcon = { Icon(Icons.Filled.Radio, null, Modifier.size(18.dp)) })
            FilterChip(selected = country.isEmpty(), onClick = { country = "" }, label = { Text("🌍 العالم") }, leadingIcon = { Icon(Icons.Filled.Public, null, Modifier.size(18.dp)) })
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(24.dp), placeholder = { Text(if (country == "EG") "ابحث في الإذاعات المصرية..." else "ابحث عن محطة...") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = if (search.isNotEmpty()) ({ IconButton(onClick = { search = "" }) { Icon(Icons.Filled.Close, "مسح") } }) else null
        )
        Spacer(Modifier.height(10.dp))
        if (loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Spacer(Modifier.height(10.dp)) }
        error?.let {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WifiOff, null); Spacer(Modifier.width(8.dp)); Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = { refreshToken++ }) { Text("إعادة المحاولة") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxSize()) {
            itemsIndexed(visibleStations, key = { _, station -> station.id }) { _, station ->
                val isCurrent = playerController.currentSong?.id == station.id
                val isPlaying = isCurrent && playerController.isPlaying
                val isFavorite = favorites.contains(station.id)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(if (isPlaying) Icons.Filled.VolumeUp else Icons.Filled.Radio, null, tint = MaterialTheme.colorScheme.primary) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(station.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(buildString { if (station.tags.isNotBlank()) append(station.tags.take(45)); if (station.codec.isNotBlank()) { if (isNotEmpty()) append(" • "); append(station.codec) }; if (station.bitrate > 0) { append(" • "); append(station.bitrate); append(" kbps") } }.ifBlank { "بث مباشر" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { favorites = if (isFavorite) favorites - station.id else favorites + station.id }) { Icon(if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, "المفضلة") }
                        FilledIconButton(onClick = {
                            val item = AudioItem(station.id, "📻 ${station.name}", if (station.countryCode == "EG") "إذاعة مصرية" else "Internet Radio", "Live Radio", 0L, Uri.parse(station.streamUrl))
                            if (isPlaying) playerController.pause() else playerController.playSong(item, listOf(item))
                        }) { Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (isPlaying) "إيقاف" else "تشغيل") }
                    }
                }
            }
        }
    }
}
