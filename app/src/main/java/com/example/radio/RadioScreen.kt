package com.example.radio

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.model.AudioItem
import com.example.model.Playlist
import com.example.QueueSheet
import com.example.onlinemusic.OnlineDeckTarget
import com.example.onlinemusic.OnlineDjBridge
import com.example.player.AudioPlayerController
import com.example.room.PlaylistEntity
import com.example.room.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

data class RadioStation(
    val id: String,
    val name: String,
    val streamUrls: List<String>,
    val tags: String,
    val codec: String,
    val bitrate: Int,
    val countryCode: String
)

private const val EGYPT_QURAN_STATION_ID = "builtin-eg-quran-cairo"
private val EGYPT_QURAN_STATION = RadioStation(
    id = EGYPT_QURAN_STATION_ID,
    name = "إذاعة القرآن الكريم من القاهرة",
    streamUrls = listOf(
        "https://stream.radiojar.com/8s5u5tpdtwzuv",
        "https://n0e.radiojar.com/8s5u5tpdtwzuv",
        "https://n05.radiojar.com/8s5u5tpdtwzuv"
    ),
    tags = "اسلامي • دين • قرآن",
    codec = "MP3",
    bitrate = 0,
    countryCode = "EG"
)

object RadioBrowserRepository {
    private val HOSTS = listOf(
        "all.api.radio-browser.info",
        "de1.api.radio-browser.info",
        "nl1.api.radio-browser.info",
        "at1.api.radio-browser.info"
    )

    suspend fun stations(countryCode: String?, query: String): List<RadioStation> = withContext(Dispatchers.IO) {
        val isWorld = countryCode.isNullOrBlank()
        val useWorldTopVotes = isWorld && query.isBlank()

        // World Radio is queried only when the user explicitly selects the World tab.
        // Egypt always remains country-scoped, including an empty search.
        val path = if (useWorldTopVotes) "/json/stations/topvote" else "/json/stations/search"
        val params = buildString {
            append("hidebroken=true&lastcheckok=1&order=votes&reverse=true&limit=100")
            if (!isWorld) {
                append("&countrycode=").append(URLEncoder.encode(countryCode, "UTF-8"))
            }
            if (query.isNotBlank()) {
                append("&name=").append(URLEncoder.encode(query, "UTF-8"))
            }
        }

        var lastException: Exception? = null

        for (host in HOSTS) {
            val urlString = "https://$host$path?$params"
            try {
                val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 10_000
                    setRequestProperty("User-Agent", "DJ-Music-Player/1.0")
                    setRequestProperty("Accept", "application/json")
                }
                
                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val json = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
                    connection.disconnect()
                    val parsedStations = buildList {
                        for (i in 0 until json.length()) {
                            val item = json.optJSONObject(i) ?: continue
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

                    if (countryCode.equals("EG", ignoreCase = true) &&
                        (query.isBlank() || EGYPT_QURAN_STATION.name.contains(query, ignoreCase = true))
                    ) {
                        // Always use the built-in Cairo Quran station as the canonical entry.
                        // RadioBrowser may return another record for the same station which can
                        // later fail stream validation and disappear from the UI.
                        val withoutQuranDuplicate = parsedStations.filterNot { station ->
                            station.name.contains("القرآن الكريم من القاهرة", ignoreCase = true) ||
                                station.name.contains("إذاعة القرآن الكريم", ignoreCase = true)
                        }
                        return@withContext buildList {
                            add(EGYPT_QURAN_STATION)
                            addAll(withoutQuranDuplicate)
                        }
                    }

                    return@withContext parsedStations
                } else {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                lastException = e
                android.util.Log.w("RadioBrowserRepository", "Warning: Could not fetch from $host")
            }
        }
        
        if (lastException != null) {
            if (countryCode.equals("EG", ignoreCase = true) &&
                (query.isBlank() || EGYPT_QURAN_STATION.name.contains(query, ignoreCase = true))
            ) {
                return@withContext listOf(EGYPT_QURAN_STATION)
            }
            throw lastException
        }
        if (countryCode.equals("EG", ignoreCase = true) && query.isBlank()) {
            listOf(EGYPT_QURAN_STATION)
        } else {
            emptyList()
        }
    }
}

enum class RadioStatus { IDLE, LOADING, LIVE, FAILED }


object StationValidator {
    val validStations = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
}

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
fun RadioScreen(
    playerController: AudioPlayerController = AudioPlayerController.obtain(LocalContext.current),
    audioLibrary: SnapshotStateList<AudioItem>,
    playlists: List<Playlist>,
    playlistRepo: PlaylistRepository
) {
    val context = LocalContext.current
    val crScope = rememberCoroutineScope()
    var country by remember { mutableStateOf("EG") }
    var search by remember { mutableStateOf("") }
    var refreshToken by remember { mutableIntStateOf(0) }

    var stations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var validating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deckPicker by remember { mutableStateOf<RadioStation?>(null) }
    val radioPrefs = remember {
        context.getSharedPreferences("radio_preferences", android.content.Context.MODE_PRIVATE)
    }
    var favorites by remember {
        mutableStateOf(
            radioPrefs.getStringSet("favorite_station_ids", emptySet()).orEmpty().toSet()
        )
    }
    var showQueue by remember { mutableStateOf(false) }
    var loadingStationId by remember { mutableStateOf<String?>(null) }
    var failedStationId by remember { mutableStateOf<String?>(null) }
    val attempts = remember { mutableStateMapOf<String, Int>() }

    fun toRadioAudioItems(queue: List<RadioStation>): List<AudioItem> =
        queue.map { station ->
            AudioItem(
                id = station.id,
                title = "📻 " + station.name,
                artist = if (station.countryCode.equals("EG", ignoreCase = true)) "إذاعة مصرية" else "Internet Radio",
                album = "Live Radio",
                durationMs = 0L,
                uri = Uri.parse(station.streamUrls.first())
            )
        }

    fun toRadioMediaItems(queue: List<RadioStation>): List<MediaItem> =
        queue.map { station ->
            MediaItem.Builder()
                .setMediaId(station.id)
                .setUri(station.streamUrls.first())
                .apply {
                    radioMime(station.codec, station.streamUrls.first())?.let(::setMimeType)
                }
                .build()
        }

    fun currentRadioStation(): RadioStation? {
        val current = playerController.currentSong ?: return null
        if (current.album != "Live Radio") return null
        return RadioStation(
            id = current.id,
            name = current.title.removePrefix("📻 ").trim(),
            streamUrls = listOf(current.uri.toString()),
            tags = "",
            codec = "",
            bitrate = 0,
            countryCode = if (current.artist == "إذاعة مصرية") "EG" else ""
        )
    }

    fun addStationToQueue(station: RadioStation) {
        val current = playerController.currentSong
        if (current?.album != "Live Radio") return

        val currentQueue = playerController.playlist.toList()
        if (currentQueue.any { it.id == station.id }) return

        val existingStations = currentQueue.map {
            RadioStation(
                id = it.id,
                name = it.title.removePrefix("📻 ").trim(),
                streamUrls = listOf(it.uri.toString()),
                tags = "",
                codec = "",
                bitrate = 0,
                countryCode = if (it.artist == "إذاعة مصرية") "EG" else ""
            )
        }

        val updated = (existingStations + station).distinctBy { it.id }
        playerController.setRadioQueue(
            songs = toRadioAudioItems(updated),
            mediaItems = toRadioMediaItems(updated),
            startIndex = currentQueue.indexOfFirst { it.id == current.id }.coerceAtLeast(0),
            preserveCurrent = true
        )
    }

    // Trigger recomposition on state changes
    val isPlayingTrigger = playerController.isPlaying
    val currentMediaId = playerController.exoPlayer.currentMediaItem?.mediaId
    val isPlayerPlaying = playerController.exoPlayer.isPlaying

    val isPlayerBuffering = playerController.isBuffering


    LaunchedEffect(country, search, refreshToken) {
        loading = true
        error = null
        try {
            val rawStations = RadioBrowserRepository.stations(country.ifBlank { null }, search)
            loading = false
            validating = true
            val validList = mutableListOf<RadioStation>()
            
            withContext(Dispatchers.IO) {
                // Test concurrently with limited parallelism
                val parallelism = 10
                for (chunk in rawStations.chunked(parallelism)) {
                    kotlinx.coroutines.coroutineScope {
                        chunk.map { station ->
                            async {
                            if (station.id == EGYPT_QURAN_STATION_ID) {
                                StationValidator.validStations[station.id] = true
                                synchronized(validList) { validList.add(station) }
                                return@async
                            }

                            if (StationValidator.validStations.containsKey(station.id)) {
                                if (StationValidator.validStations[station.id] == true) {
                                    synchronized(validList) { validList.add(station) }
                                }
                                return@async
                            }
                            
                            var isValid = false
                            for (urlStr in station.streamUrls) {
                                val resolvedUrl = resolvePlaylistUrl(urlStr)
                                try {
                                    val conn = URL(resolvedUrl).openConnection() as HttpURLConnection
                                    conn.connectTimeout = 5000
                                    conn.readTimeout = 5000
                                    conn.requestMethod = "GET"
                                    conn.setRequestProperty("User-Agent", "DJ-Music-Player/1.0")
                                    val code = conn.responseCode
                                    if (code in 200..299) {
                                        val contentType = conn.contentType?.lowercase() ?: ""
                                        if (contentType.contains("audio") || contentType.contains("mpeg") || contentType.contains("ogg") || contentType.contains("aac") || contentType.contains("flac") || contentType.contains("application") || contentType.contains("video/ogg")) {
                                            isValid = true
                                        }
                                    }
                                    conn.disconnect()
                                    if (isValid) break
                                } catch (e: Exception) {}
                            }
                            StationValidator.validStations[station.id] = isValid
                            if (isValid) {
                                synchronized(validList) { validList.add(station) }
                            }
                        }
                    }.forEach { it.await() }
                    }
                    
                    // Update UI incrementally
                    stations = validList.toList()
                }
            }
            stations = validList
        } catch (e: Exception) {
            error = e.localizedMessage ?: "فشل الاتصال بالخادم"
        } finally {
            loading = false
            validating = false
        }
    }


    suspend fun playStation(station: RadioStation, requestedIndex: Int = 0) {
        var index = requestedIndex.coerceIn(0, station.streamUrls.lastIndex)
        loadingStationId = station.id
        failedStationId = null
        attempts[station.id] = index
        while (index < station.streamUrls.size) {
            val resolvedUrl = resolvePlaylistUrl(station.streamUrls[index])
            val item = AudioItem(station.id, "📻 ${station.name}", if (station.countryCode.equals("EG", ignoreCase = true)) "إذاعة مصرية" else "Internet Radio", "Live Radio", 0L, Uri.parse(resolvedUrl))
            
            try {
                val media = MediaItem.Builder()
                    .setMediaId(station.id)
                    .setUri(resolvedUrl)
                    .apply {
                        radioMime(station.codec, resolvedUrl)?.let(::setMimeType)
                    }
                    .build()
                playerController.playRadio(item, media)
                
                var success = false
                for (i in 0 until 100) { // wait up to 20 seconds
                    kotlinx.coroutines.delay(200)
                    if (loadingStationId != station.id) return // User selected another station
                    
                    val error = playerController.exoPlayer.playerError
                    if (error != null) {
                        break // Failed, try next stream url
                    }
                    val state = playerController.exoPlayer.playbackState
                    if (state == androidx.media3.common.Player.STATE_READY) {
                        success = true
                        break
                    }
                }
                
                if (success) {
                    loadingStationId = null
                    return
                } else {
                    index++
                    attempts[station.id] = index
                }
            } catch (e: Exception) {
                index++
                attempts[station.id] = index
            }
        }
        playerController.recoverFromRadioFailure()
        loadingStationId = null
        failedStationId = station.id
    }

    val visibleStations = if (favorites.isEmpty()) stations else stations.sortedByDescending { favorites.contains(it.id) }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("📻 Radio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(if (country == "EG") "الإذاعات المصرية — Live" else "إذاعات من حول العالم — Live", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { showQueue = true }) {
                        Icon(Icons.Filled.QueueMusic, "الكيو")
                    }
                    if (playerController.playlist.isNotEmpty()) {
                        Badge(
                            modifier = Modifier.align(Alignment.TopEnd),
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text(playerController.playlist.size.toString())
                        }
                    }
                }
                IconButton(onClick = { refreshToken++ }) {
                    Icon(Icons.Filled.Refresh, "تحديث")
                }
            }
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
        if (validating) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
    if (country == "EG") "جاري التحقق من المحطات المصرية..." else "جاري التحقق من المحطات العالمية...",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)
            }
            Spacer(Modifier.height(10.dp))
        }

        error?.let {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.WifiOff, null); Spacer(Modifier.width(8.dp)); Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer); TextButton(onClick = { refreshToken++ }) { Text("إعادة المحاولة") } }
            }
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxSize()) {
            items(visibleStations.size, key = { visibleStations[it].id }) { index -> val station = visibleStations[index]
                val isCurrentStation = (currentMediaId == station.id)
                val isStationPlaying = isCurrentStation && isPlayerPlaying
                val isStationLoading = (loadingStationId == station.id) || (isCurrentStation && isPlayerBuffering && !isStationPlaying)
                val isStationFailed = (failedStationId == station.id) && !isStationPlaying && !isStationLoading
                val isFavorite = favorites.contains(station.id)

                val statusText = when {
                    isStationLoading -> "تحميل البث..."
                    isStationPlaying -> "LIVE • شغال الآن"
                    isStationFailed -> "فشل التحميل"
                    else -> "جاهزة للتشغيل"
                }

                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (isCurrentStation) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(if (isStationPlaying) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.Radio, null, tint = MaterialTheme.colorScheme.primary) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(station.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(buildString { if (station.tags.isNotBlank()) append(station.tags.take(45)); if (station.codec.isNotBlank()) { if (isNotEmpty()) append(" • "); append(station.codec) }; if (station.bitrate > 0) { append(" • "); append(station.bitrate); append(" kbps") } }.ifBlank { "بث مباشر" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(statusText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (isStationFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            }
                            if (isStationLoading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            IconButton(
                                onClick = {
                                    val updated =
                                        if (isFavorite) favorites - station.id else favorites + station.id
                                    favorites = updated
                                    radioPrefs.edit()
                                        .putStringSet("favorite_station_ids", updated)
                                        .apply()
                                }
                            ) {
                                Icon(
                                    if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    "المفضلة"
                                )
                            }
                            IconButton(onClick = { addStationToQueue(station) }) {
                                Icon(Icons.Filled.QueueMusic, "إضافة للكيو")
                            }
                            IconButton(onClick = { deckPicker = station }) {
                                Icon(Icons.Filled.Headset, "إرسال إلى DJ Deck")
                            }
                            FilledIconButton(modifier = Modifier,
                                onClick = {
                                    if (isStationPlaying) {
                                        playerController.pause()
                                    } else if (isCurrentStation) {
                                        playerController.exoPlayer.play()
                                    } else {
                                        val queueIndex = playerController.playlist.indexOfFirst {
                                            it.album == "Live Radio" && it.id == station.id
                                        }
                                        if (queueIndex >= 0) {
                                            playerController.play(playerController.playlist[queueIndex], null)
                                        } else {
                                            crScope.launch { playStation(station) }
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isStationPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isStationPlaying) "Pause" else "Play"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (showQueue) {
        QueueSheet(
            controller = playerController,
            playlists = playlists,
            library = audioLibrary,
            playlistRepo = playlistRepo,
            onDismiss = { showQueue = false },
            onSelect = { song ->
                playerController.play(song, null)
                showQueue = false
            }
        )
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
                        val item = AudioItem(station.id, "📻 ${station.name}", if (station.countryCode.equals("EG", ignoreCase = true)) "إذاعة مصرية" else "Internet Radio", "Live Radio", 0L, Uri.parse(station.streamUrls.first()))
                        OnlineDjBridge.send(item, OnlineDeckTarget.A)
                    }) { Text("Deck A") }
                    TextButton(onClick = {
                        deckPicker = null
                        val item = AudioItem(station.id, "📻 ${station.name}", if (station.countryCode.equals("EG", ignoreCase = true)) "إذاعة مصرية" else "Internet Radio", "Live Radio", 0L, Uri.parse(station.streamUrls.first()))
                        OnlineDjBridge.send(item, OnlineDeckTarget.B)
                    }) { Text("Deck B") }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RadioQueueSheet(
    queue: List<RadioStation>,
    onDismiss: () -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onSavePlaylist: () -> Unit,
    onAddToExistingPlaylist: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "RADIO QUEUE",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        queue.size.toString() + " station(s)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (queue.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("Clear") }
                }
            }

            if (queue.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("الكيو فارغ — أضف المحطات من زر Queue")
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    itemsIndexed(queue, key = { _, station -> station.id }) { index, station ->
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    (index + 1).toString(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        station.name,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (station.countryCode.equals("EG", ignoreCase = true)) "مصرية" else "عالمية",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onRemove(station.id) }) {
                                    Icon(Icons.Filled.Delete, "Remove from queue")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onSavePlaylist, Modifier.weight(1f)) {
                        Icon(Icons.Filled.Save, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Save Playlist")
                    }
                    OutlinedButton(onClick = onAddToExistingPlaylist, Modifier.weight(1f)) {
                        Icon(Icons.Filled.PlaylistAdd, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Add to Existing")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
