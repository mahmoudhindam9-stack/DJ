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
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import com.example.player.UnifiedStreamResolver
import com.example.model.AudioItem
import com.example.model.Playlist
import com.example.QueueSheet
import com.example.onlinemusic.OnlineDeckTarget
import com.example.onlinemusic.OnlineDjBridge
import com.example.onlinemusic.enqueueOnlineSong
import com.example.player.AudioPlayerController
import com.example.player.RadioStreamCatalog
import com.example.player.UnifiedPlaybackQueue
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

                    return@withContext deduplicateRadioStations(parsedStations)
                } else {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                lastException = e
                android.util.Log.w("RadioBrowserRepository", "Warning: Could not fetch from $host")
            }
        }
        
        if (lastException != null) {
            throw lastException
        }
        emptyList()
    }
}

/**
 * Radio Browser hosts are mirrors of one directory, not independent station sources.
 * Keep the first healthy record and collapse duplicate UUIDs, stream URLs and names.
 */
internal fun deduplicateRadioStations(input: List<RadioStation>): List<RadioStation> {
    val ids = HashSet<String>()
    val streams = HashSet<String>()
    val names = HashSet<String>()
    return input.filter { station ->
        val id = station.id.trim().lowercase()
        val name = station.name.trim().lowercase()
            .replace(Regex("\\s+"), " ")
        val streamKeys = station.streamUrls.map {
            it.trim().lowercase().substringBefore("#").substringBefore("?").trimEnd('/')
        }.filter { it.isNotBlank() }
        val duplicate = (id.isNotBlank() && !ids.add(id)) ||
            streamKeys.any { !streams.add(it) } ||
            (name.isNotBlank() && !names.add(name + "|" + station.countryCode.trim().uppercase()))
        !duplicate
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
    val u = url.lowercase().substringBefore('?').substringBefore('#')
    return when {
        u.endsWith(".m3u8") || c.contains("hls") -> MimeTypes.APPLICATION_M3U8
        u.endsWith(".mpd") || c.contains("dash") -> MimeTypes.APPLICATION_MPD
        u.endsWith(".mp3") || c.contains("mp3") || c.contains("mpeg") -> MimeTypes.AUDIO_MPEG
        u.endsWith(".aac") || u.endsWith(".aacp") || c.contains("aac") || c.contains("aacp") -> MimeTypes.AUDIO_AAC
        u.endsWith(".ogg") || u.endsWith(".oga") || c.contains("ogg") || c.contains("vorbis") -> MimeTypes.AUDIO_OGG
        u.endsWith(".flac") || c.contains("flac") -> MimeTypes.AUDIO_FLAC
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
        RadioStreamCatalog.register(station)
        playerController.enqueueOnlineSong(UnifiedPlaybackQueue.radioStationToAudioItem(station))
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
                            if (StationValidator.validStations.containsKey(station.id)) {
                                if (StationValidator.validStations[station.id] == true) {
                                    synchronized(validList) { validList.add(station) }
                                }
                                return@async
                            }
                            
                            var isValid = false
                            for (urlStr in station.streamUrls) {
                                val probed = UnifiedStreamResolver.probeStreamEndpoint(urlStr)
                                if (probed != null && probed.isPlayable) {
                                    isValid = true
                                    break
                                }
                            }
                            StationValidator.validStations[station.id] = isValid
                            if (isValid) {
                                synchronized(validList) { validList.add(station) }
                            }
                        }
                    }.forEach { it.await() }
                    }
                    
                    // Update UI incrementally and register streams in catalog
                    RadioStreamCatalog.registerAll(validList)
                    stations = validList.toList()
                }
            }
            RadioStreamCatalog.registerAll(validList)
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

    val isArabic by com.example.ui.locale.AppLanguageManager.isArabicFlow.collectAsState()
    val visibleStations = if (favorites.isEmpty()) stations else stations.sortedByDescending { favorites.contains(it.id) }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (isArabic) "📻 الراديو المباشر" else "📻 Live Radio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (country == "EG") {
                        if (isArabic) "الإذاعات المصرية — بث مباشر" else "Egyptian Radio Stations — Live"
                    } else {
                        if (isArabic) "إذاعات من حول العالم — بث مباشر" else "World Radio Stations — Live"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { showQueue = true }) {
                        Icon(Icons.Filled.QueueMusic, if (isArabic) "قائمة الانتظار" else "Queue")
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
                    Icon(Icons.Filled.Refresh, if (isArabic) "تحديث" else "Refresh")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = country == "EG",
                onClick = { country = "EG" },
                label = { Text(if (isArabic) "🇪🇬 مصر" else "🇪🇬 Egypt") },
                leadingIcon = { Icon(Icons.Filled.Radio, null, Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = country.isEmpty(),
                onClick = { country = "" },
                label = { Text(if (isArabic) "🌍 العالم" else "🌍 World") },
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
            placeholder = {
                Text(
                    if (country == "EG") {
                        if (isArabic) "ابحث في الإذاعات المصرية..." else "Search Egyptian radio stations..."
                    } else {
                        if (isArabic) "ابحث عن محطة..." else "Search radio stations..."
                    }
                )
            },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = if (search.isNotEmpty()) ({ IconButton(onClick = { search = "" }) { Icon(Icons.Filled.Close, if (isArabic) "مسح" else "Clear") } }) else null
        )
        Spacer(Modifier.height(10.dp))

        if (loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Spacer(Modifier.height(10.dp)) }
        if (validating) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (country == "EG") {
                        if (isArabic) "جاري التحقق من المحطات المصرية..." else "Verifying Egyptian radio stations..."
                    } else {
                        if (isArabic) "جاري التحقق من المحطات العالمية..." else "Verifying world radio stations..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        error?.let {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WifiOff, null)
                    Spacer(Modifier.width(8.dp))
                    Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = { refreshToken++ }) { Text(if (isArabic) "إعادة المحاولة" else "Retry") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxSize()) {
            items(visibleStations.size, key = { visibleStations[it].id }) { index -> val station = visibleStations[index]
                val isCurrentStation = (currentMediaId == station.id) || (playerController.currentSong?.id == station.id)
                val isStationPlaying = isCurrentStation && isPlayerPlaying
                val isStationLoading = (loadingStationId == station.id) || (isCurrentStation && isPlayerBuffering && !isStationPlaying)
                val isStationFailed = ((isCurrentStation && !isStationPlaying && !isStationLoading && !playerController.playbackErrorMessage.isNullOrBlank()) ||
                    (failedStationId == station.id && !isStationPlaying && !isStationLoading))
                val isFavorite = favorites.contains(station.id)

                val statusText = when {
                    isStationLoading -> if (isArabic) "جاري تحميل البث..." else "Buffering stream..."
                    isStationPlaying -> if (isArabic) "LIVE • يعمل الآن" else "LIVE • Playing Now"
                    isStationFailed -> if (isArabic) "فشل التحميل" else "Stream failed"
                    else -> if (isArabic) "جاهزة للتشغيل" else "Ready to play"
                }

                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (isCurrentStation) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(if (isStationPlaying) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.Radio, null, tint = MaterialTheme.colorScheme.primary) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(station.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(buildString { if (station.tags.isNotBlank()) append(station.tags.take(45)); if (station.codec.isNotBlank()) { if (isNotEmpty()) append(" • "); append(station.codec) }; if (station.bitrate > 0) { append(" • "); append(station.bitrate); append(" kbps") } }.ifBlank { if (isArabic) "بث مباشر" else "Live Stream" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                    if (isArabic) "المفضلة" else "Favorite"
                                )
                            }
                            IconButton(onClick = { addStationToQueue(station) }) {
                                Icon(Icons.Filled.QueueMusic, if (isArabic) "إضافة لقائمة الانتظار" else "Add to queue")
                            }
                            IconButton(onClick = { deckPicker = station }) {
                                Icon(Icons.Filled.Headset, if (isArabic) "إرسال إلى DJ Deck" else "Send to DJ Deck")
                            }
                            FilledIconButton(
                                modifier = Modifier,
                                onClick = {
                                    if (isStationPlaying) {
                                        playerController.pause()
                                    } else if (isCurrentStation) {
                                        playerController.togglePlayPause()
                                    } else {
                                        val queueStations = visibleStations.ifEmpty { listOf(station) }
                                        RadioStreamCatalog.registerAll(queueStations)
                                        val radioQueue = queueStations.map {
                                            UnifiedPlaybackQueue.radioStationToAudioItem(it)
                                        }
                                        val startIndex = queueStations.indexOfFirst { it.id == station.id }.coerceAtLeast(0)
                                        failedStationId = null
                                        playerController.playRadioCollection(radioQueue, startIndex)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isStationPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isStationPlaying) (if (isArabic) "إيقاف" else "Pause") else (if (isArabic) "تشغيل" else "Play")
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
            title = { Text(if (isArabic) "تشغيل الإذاعة على أي Deck؟" else "Send station to which DJ Deck?") },
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
    val isArabic by com.example.ui.locale.AppLanguageManager.isArabicFlow.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isArabic) "قائمة انتظار الراديو" else "RADIO QUEUE",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (isArabic) "${queue.size} محطة" else "${queue.size} station(s)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (queue.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text(if (isArabic) "مسح" else "Clear") }
                }
            }

            if (queue.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isArabic) "قائمة الانتظار فارغة — أضف المحطات من زر القائمة" else "Queue is empty — add stations using the Queue button")
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
                                        if (station.countryCode.equals("EG", ignoreCase = true)) (if (isArabic) "مصرية" else "Egypt") else (if (isArabic) "عالمية" else "World"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onRemove(station.id) }) {
                                    Icon(Icons.Filled.Delete, if (isArabic) "إزالة من القائمة" else "Remove from queue")
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
                        Text(if (isArabic) "حفظ كقائمة" else "Save Playlist")
                    }
                    OutlinedButton(onClick = onAddToExistingPlaylist, Modifier.weight(1f)) {
                        Icon(Icons.Filled.PlaylistAdd, null)
                        Spacer(Modifier.width(5.dp))
                        Text(if (isArabic) "إضافة لقائمة حالية" else "Add to Existing")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
