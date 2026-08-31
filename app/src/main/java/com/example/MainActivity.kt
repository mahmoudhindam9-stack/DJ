package com.example

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.example.model.AudioItem
import com.example.model.Playlist
import com.example.player.*
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.MusicScanner
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val context = LocalContext.current
    val navController = rememberNavController()

    // Persistent State Controllers
    val playerController = remember { AudioPlayerController(context) }
    val djMixerController = remember { DJMixerController(context) }
    val eqController = remember { EqualizerController() }
    val micController = remember { MicController(context) }

    // Master Library and Playlists State & Room DB Repository
    val audioLibrary = remember { mutableStateListOf<AudioItem>() }
    val playlists = remember { mutableStateListOf<Playlist>() }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val db = remember { com.example.room.AppDatabase.getDatabase(context) }
    val playlistRepo = remember { com.example.room.PlaylistRepository(db.playlistDao()) }

    LaunchedEffect(Unit) {
        playlistRepo.allPlaylists.collect { entities ->
            playlists.clear()
            playlists.addAll(entities.map { entity ->
                Playlist(
                    id = entity.playlistId,
                    name = entity.name,
                    songIds = if (entity.songIdsJson.isBlank()) emptyList() else entity.songIdsJson.split(",").filter { it.isNotBlank() }
                )
            })
        }
    }

    // Synchronize progress for seekbar
    LaunchedEffect(Unit) {
        eqController.attachToSession(playerController.exoPlayer.audioSessionId)
        while (true) {
            playerController.updateProgress()
            delay(250)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playerController.release()
            djMixerController.release()
            eqController.release()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = "Player") },
                    label = { Text("Player") },
                    selected = currentDestination?.route == "player",
                    onClick = {
                        djMixerController.pauseAll()
                        navController.navigate("player") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Headset, contentDescription = "DJ Mixer") },
                    label = { Text("DJ Mixer") },
                    selected = currentDestination?.route == "dj",
                    onClick = {
                        playerController.pause()
                        navController.navigate("dj") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Tune, contentDescription = "Equalizer") },
                    label = { Text("Equalizer") },
                    selected = currentDestination?.route == "equalizer",
                    onClick = {
                        navController.navigate("equalizer") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Mic, contentDescription = "Mic/Karaoke") },
                    label = { Text("Mic") },
                    selected = currentDestination?.route == "mic",
                    onClick = {
                        navController.navigate("mic") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "player",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("player") {
                PlayerScreen(
                    playerController = playerController,
                    audioLibrary = audioLibrary,
                    playlists = playlists,
                    selectedPlaylistId = selectedPlaylistId,
                    onSelectPlaylist = { id -> selectedPlaylistId = id },
                    onPauseDJ = { djMixerController.pauseAll() },
                    navController = navController
                )
            }
            composable("dj") {
                DJMixerScreen(
                    djMixerController = djMixerController,
                    audioLibrary = audioLibrary,
                    onPauseMainPlayer = { playerController.pause() }
                )
            }
            composable("equalizer") {
                EqualizerScreen(eqController = eqController)
            }
            composable("mic") {
                MicScreen(micController = micController, scope = scope)
            }
            composable("full_player") {
                FullPlayerScreen(playerController = playerController, onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
// KARAOKE_DJ_ENGLISH_V2
fun MicScreen(micController: MicController, scope: kotlinx.coroutines.CoroutineScope) {
    val context = LocalContext.current
    var inputExpanded by remember { mutableStateOf(false) }
    var outputExpanded by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) micController.toggleMic(true, scope)
        else Toast.makeText(context, "Microphone permission is required", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Karaoke Studio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Live vocal monitor with DJ-style effects", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier.size(116.dp).clip(CircleShape)
                .background(if (micController.isMicEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    if (micController.isMicEnabled) micController.toggleMic(false, scope)
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(if (micController.isMicEnabled) Icons.Filled.Mic else Icons.Filled.MicOff, null, Modifier.size(46.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(if (micController.isMicEnabled) "LIVE MONITOR ON" else "Tap to enable microphone", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Audio Routing", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Input Device", style = MaterialTheme.typography.labelSmall)
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { inputExpanded = true }, Modifier.fillMaxWidth()) {
                        Text(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) micController.selectedInputDevice?.productName?.toString() ?: "System Default Mic" else "System Default Mic", maxLines = 1)
                    }
                    DropdownMenu(inputExpanded, { inputExpanded = false }) {
                        DropdownMenuItem(text = { Text("System Default Mic") }, onClick = { micController.selectInputDevice(null, scope); inputExpanded = false })
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) micController.inputDevices.forEach { device ->
                            DropdownMenuItem(text = { Text(device.productName?.toString()?.ifBlank { "Audio Input ${device.id}" } ?: "Audio Input ${device.id}") }, onClick = { micController.selectInputDevice(device, scope); inputExpanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Output Device", style = MaterialTheme.typography.labelSmall)
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { outputExpanded = true }, Modifier.fillMaxWidth()) {
                        Text(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) micController.selectedOutputDevice?.productName?.toString() ?: "System Default Output" else "System Default Output", maxLines = 1)
                    }
                    DropdownMenu(outputExpanded, { outputExpanded = false }) {
                        DropdownMenuItem(text = { Text("System Default Output") }, onClick = { micController.selectOutputDevice(null); outputExpanded = false })
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) micController.outputDevices.forEach { device ->
                            DropdownMenuItem(text = { Text(device.productName?.toString()?.ifBlank { "Audio Output ${device.id}" } ?: "Audio Output ${device.id}") }, onClick = { micController.selectOutputDevice(device); outputExpanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("${micController.routingStatus}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("DJ Effects", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(micController.echoFxEnabled, { micController.echoFxEnabled = !micController.echoFxEnabled }, label = { Text("Echo") }, modifier = Modifier.weight(1f))
                    FilterChip(micController.reverbFxEnabled, { micController.reverbFxEnabled = !micController.reverbFxEnabled }, label = { Text("Reverb") }, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(micController.flangerFxEnabled, { micController.flangerFxEnabled = !micController.flangerFxEnabled }, label = { Text("Flanger") }, modifier = Modifier.weight(1f))
                    FilterChip(micController.beatFxEnabled, { micController.beatFxEnabled = !micController.beatFxEnabled }, label = { Text("Beat FX") }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text("Vocal Preset", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MicFilter.values().toList()) { filter ->
                        FilterChip(filter == micController.currentFilter, { micController.currentFilter = filter }, label = { Text(filter.displayName) })
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Mix & FX Amount", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("Mic Volume: ${(micController.micVolume * 100).toInt()}%")
                Slider(micController.micVolume, { micController.micVolume = it }, valueRange = 0f..2f)
                Text("Echo: ${(micController.echoLevel * 100).toInt()}%")
                Slider(micController.echoLevel, { micController.echoLevel = it }, valueRange = 0f..1f)
                Text("Reverb: ${(micController.reverbLevel * 100).toInt()}%")
                Slider(micController.reverbLevel, { micController.reverbLevel = it }, valueRange = 0f..1f)
                Text("Flanger: ${(micController.flangerMix * 100).toInt()}%")
                Slider(micController.flangerMix, { micController.flangerMix = it }, valueRange = 0f..1f)
                Text("Filter: ${(micController.filterMix * 100).toInt()}%")
                Slider(micController.filterMix, { micController.filterMix = it }, valueRange = 0f..1f)
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Beat FX", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("BPM: ${micController.bpm.toInt()}")
                Slider(micController.bpm, { micController.bpm = it }, valueRange = 70f..180f)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(BeatFxDivision.values().toList()) { div ->
                        FilterChip(div == micController.beatFxDivision, { micController.beatFxDivision = div }, label = { Text(div.displayName) })
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // MELODY_STUDIO_V2
        MelodyStudioCard(audioLibrary, context)

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("AEC & Noise Suppression enabled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FullPlayerScreen(playerController: AudioPlayerController, onBack: () -> Unit) {
    val song = playerController.currentSong
    if (song == null) {
        onBack()
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Large Album Art / Visualizer Placeholder
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Album, contentDescription = null, modifier = Modifier.size(128.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(song.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Controls
        val maxPos = if (playerController.durationMs > 0) playerController.durationMs.toFloat() else 1f
        val currentPos = playerController.currentPositionMs.toFloat().coerceIn(0f, maxPos)

        Slider(
            value = currentPos,
            onValueChange = { playerController.seekTo(it.toLong()) },
            valueRange = 0f..maxPos,
            modifier = Modifier.fillMaxWidth()
        )
        
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(MusicScanner.formatMs(playerController.currentPositionMs))
            Text(MusicScanner.formatMs(playerController.durationMs))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
            IconButton(onClick = { playerController.toggleShuffle() }) {
                Icon(Icons.Filled.Shuffle, contentDescription = null, tint = if (playerController.isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { playerController.playPrevious() }) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = null, modifier = Modifier.size(48.dp))
            }
            FloatingActionButton(
                onClick = { playerController.togglePlayPause() },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(if (playerController.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = { playerController.playNext() }) {
                Icon(Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(48.dp))
            }
            IconButton(onClick = { playerController.toggleRepeat() }) {
                Icon(Icons.Filled.Repeat, contentDescription = null, tint = if (playerController.repeatOption != RepeatOption.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    playerController: AudioPlayerController,
    audioLibrary: SnapshotStateList<AudioItem>,
    playlists: SnapshotStateList<Playlist>,
    selectedPlaylistId: String?,
    onSelectPlaylist: (String?) -> Unit,
    onPauseDJ: () -> Unit,
    navController: androidx.navigation.NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { com.example.room.AppDatabase.getDatabase(context) }
    val playlistRepo = remember { com.example.room.PlaylistRepository(db.playlistDao()) }
    var searchQuery by remember { mutableStateOf("") }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf<AudioItem?>(null) }

    // Picker for custom audio files
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                val audio = MusicScanner.parsePickedUri(context, uri)
                if (audioLibrary.none { it.uri == uri }) {
                    audioLibrary.add(audio)
                }
            }
            Toast.makeText(context, "Added ${uris.size} track(s)!", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission launcher for scanning device
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val scanned = MusicScanner.scanMediaStoreAudio(context)
            scanned.forEach { song ->
                if (audioLibrary.none { it.id == song.id }) {
                    audioLibrary.add(song)
                }
            }
            Toast.makeText(context, "Found ${scanned.size} tracks from storage", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Storage permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    val displayedSongs = remember(audioLibrary.size, playlists.size, selectedPlaylistId, searchQuery) {
        val baseList = if (selectedPlaylistId == null) {
            audioLibrary.toList()
        } else {
            val targetPlaylist = playlists.find { it.id == selectedPlaylistId }
            val ids = targetPlaylist?.songIds ?: emptyList()
            audioLibrary.filter { it.id in ids }
        }

        if (searchQuery.isBlank()) {
            baseList
        } else {
            baseList.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist.contains(searchQuery, ignoreCase = true) ||
                        it.album.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Action Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Music Library",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${audioLibrary.size} track(s) available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                IconButton(onClick = {
                    audioPickerLauncher.launch(arrayOf("audio/*"))
                }) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = "Import Files", tint = MaterialTheme.colorScheme.primary)
                }

                IconButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                    } else {
                        permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }) {
                    Icon(Icons.Filled.Sync, contentDescription = "Scan MediaStore")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search title, artist, or album...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Playlists Horizontal Selector Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = selectedPlaylistId == null,
                    onClick = { onSelectPlaylist(null) },
                    label = { Text("All Songs (${audioLibrary.size})") }
                )
            }

            items(playlists, key = { it.id }) { playlist ->
                FilterChip(
                    selected = selectedPlaylistId == playlist.id,
                    onClick = { onSelectPlaylist(playlist.id) },
                    label = { Text("${playlist.name} (${playlist.songIds.size})") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                playlists.remove(playlist)
                                scope.launch {
                                    playlistRepo.delete(playlist.id)
                                }
                            },
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Delete Playlist")
                        }
                    }
                )
            }

            item {
                IconButton(
                    onClick = { showCreatePlaylistDialog = true },
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "New Playlist",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Song List or Empty State
        if (displayedSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (audioLibrary.isEmpty()) "No music imported yet" else "No matching tracks found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) }) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import Local Audio Files")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(displayedSongs, key = { _, song -> song.id }) { _, song ->
                    val isCurrent = playerController.currentSong?.id == song.id
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPauseDJ()
                                playerController.playSong(song, displayedSongs)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isCurrent && playerController.isPlaying) Icons.Filled.VolumeUp else Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${song.artist} • ${song.album}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = MusicScanner.formatMs(song.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = { showAddToPlaylistDialog = song }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.PlaylistAdd,
                                    contentDescription = "Add to playlist"
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Full Interactive Now Playing Card & Controls
        if (playerController.currentSong != null) {
            NowPlayingCard(
                playerController = playerController, 
                onPauseDJ = onPauseDJ,
                onClick = { navController.navigate("full_player") }
            )
        }
    }

    // Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create New Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            val newId = System.currentTimeMillis().toString()
                            val newName = playlistName.trim()
                            playlists.add(
                                Playlist(
                                    id = newId,
                                    name = newName
                                )
                            )
                            scope.launch {
                                playlistRepo.insert(
                                    com.example.room.PlaylistEntity(
                                        playlistId = newId,
                                        name = newName,
                                        songIdsJson = ""
                                    )
                                )
                            }
                            showCreatePlaylistDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Add To Playlist Dialog
    showAddToPlaylistDialog?.let { targetSong ->
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = null },
            title = { Text("Add '${targetSong.title}' to Playlist") },
            text = {
                if (playlists.isEmpty()) {
                    Text("No playlists created yet. Create a playlist first!")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(playlists, key = { it.id }) { pl ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!pl.songIds.contains(targetSong.id)) {
                                            val updated = pl.songIds + targetSong.id
                                            val index = playlists.indexOf(pl)
                                            if (index != -1) {
                                                playlists[index] = pl.copy(songIds = updated)
                                            }
                                            Toast.makeText(context, "Added to ${pl.name}", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Already in playlist", Toast.LENGTH_SHORT).show()
                                        }
                                        showAddToPlaylistDialog = null
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.QueueMusic, contentDescription = null)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(pl.name, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddToPlaylistDialog = null }) { Text("Done") }
            }
        )
    }
}

@Composable
fun NowPlayingCard(
    playerController: AudioPlayerController, 
    onPauseDJ: () -> Unit,
    onClick: () -> Unit
) {
    val song = playerController.currentSong ?: return

    // Rotating vinyl animation when playing
    val infiniteTransition = rememberInfiniteTransition(label = "VinylRotate")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Vinyl Record Visualizer
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .rotate(if (playerController.isPlaying) rotationAngle else 0f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Album,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Seekbar Slider
            val maxPos = if (playerController.durationMs > 0) playerController.durationMs.toFloat() else 1f
            val currentPos = playerController.currentPositionMs.toFloat().coerceIn(0f, maxPos)

            Slider(
                value = currentPos,
                onValueChange = { newPos ->
                    playerController.seekTo(newPos.toLong())
                },
                valueRange = 0f..maxPos,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = MusicScanner.formatMs(playerController.currentPositionMs),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = MusicScanner.formatMs(playerController.durationMs),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Playback Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Toggle
                IconButton(onClick = { playerController.toggleShuffle() }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playerController.isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Previous
                IconButton(onClick = {
                    onPauseDJ()
                    playerController.playPrevious()
                }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
                }

                // Play / Pause Toggle
                FilledIconButton(
                    onClick = {
                        onPauseDJ()
                        playerController.togglePlayPause()
                    },
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        imageVector = if (playerController.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Next
                IconButton(onClick = {
                    onPauseDJ()
                    playerController.playNext()
                }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
                }

                // Repeat Mode Toggle
                IconButton(onClick = { playerController.toggleRepeat() }) {
                    val (icon, tint) = when (playerController.repeatOption) {
                        RepeatOption.OFF -> Icons.Filled.Repeat to MaterialTheme.colorScheme.onSurface
                        RepeatOption.ALL -> Icons.Filled.Repeat to MaterialTheme.colorScheme.primary
                        RepeatOption.ONE -> Icons.Filled.RepeatOne to MaterialTheme.colorScheme.primary
                    }
                    Icon(icon, contentDescription = "Repeat", tint = tint)
                }
            }
        }
    }
}

@Composable
fun DJMixerScreen(
    djMixerController: DJMixerController,
    audioLibrary: SnapshotStateList<AudioItem>,
    onPauseMainPlayer: () -> Unit
) {
    val context = LocalContext.current
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            val audio = MusicScanner.parsePickedUri(context, uri)
            if (audioLibrary.none { it.uri == uri }) {
                audioLibrary.add(audio)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Pro DJ Mixer",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Live soundboard, instruments, crowd effects & dual deck control",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Dual Decks A & B
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Deck A
            DJDeckItem(
                modifier = Modifier.weight(1f),
                deck = djMixerController.deckA,
                audioLibrary = audioLibrary,
                onImportClicked = { pickerLauncher.launch(arrayOf("audio/*")) },
                onPlayStarted = onPauseMainPlayer
            )

            // Deck B
            DJDeckItem(
                modifier = Modifier.weight(1f),
                deck = djMixerController.deckB,
                audioLibrary = audioLibrary,
                onImportClicked = { pickerLauncher.launch(arrayOf("audio/*")) },
                onPlayStarted = onPauseMainPlayer
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Crossfader Control Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("DECK A (${((1f - djMixerController.crossfader) * 100).toInt()}%)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("CROSSFADER", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("DECK B (${(djMixerController.crossfader * 100).toInt()}%)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                Slider(
                    value = djMixerController.crossfader,
                    onValueChange = { djMixerController.updateCrossfader(it) },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Live Sound Effects & Instruments Soundboard
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val sounds = SamplerSound.values()
                val rows = sounds.toList().chunked(3)

                rows.forEach { rowSounds ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowSounds.forEach { sound ->
                            Button(
                                onClick = { djMixerController.soundPlayer.playSound(sound) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(2.dp)
                            ) {
                                Text(
                                    text = sound.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun DJDeckItem(
    modifier: Modifier = Modifier,
    deck: DJDeck,
    audioLibrary: List<AudioItem>,
    onImportClicked: () -> Unit,
    onPlayStarted: () -> Unit
) {
    var showTrackSelector by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxHeight(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = deck.deckName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Track Title
            Button(
                onClick = { showTrackSelector = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = deck.track?.title ?: "Select Track",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Play / Cue Button
            FilledIconButton(
                onClick = {
                    deck.togglePlayPause(onPlayStarted)
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (deck.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause"
                )
            }

            // Seek & Progress Control
            LaunchedEffect(deck.isPlaying) {
                while (true) {
                    deck.updateProgress()
                    kotlinx.coroutines.delay(200L)
                }
            }

            val maxPos = if (deck.durationMs > 0L) deck.durationMs.toFloat() else 1f
            var isUserSeeking by remember { mutableStateOf(false) }
            var userSeekPos by remember { mutableFloatStateOf(0f) }

            val currentPos = if (isUserSeeking) userSeekPos else deck.currentPositionMs.toFloat().coerceIn(0f, maxPos)

            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = currentPos,
                onValueChange = { newPos ->
                    isUserSeeking = true
                    userSeekPos = newPos
                    deck.seekTo(newPos.toLong())
                },
                onValueChangeFinished = {
                    deck.seekTo(userSeekPos.toLong())
                    isUserSeeking = false
                },
                valueRange = 0f..maxPos,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = com.example.utils.MusicScanner.formatMs(deck.currentPositionMs),
                    style = MaterialTheme.typography.labelSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = {
                            val newPos = (deck.currentPositionMs - 5000L).coerceAtLeast(0L)
                            deck.seekTo(newPos)
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("-5s", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = {
                            val newPos = (deck.currentPositionMs + 5000L).coerceAtMost(deck.durationMs)
                            deck.seekTo(newPos)
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("+5s", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(
                    text = com.example.utils.MusicScanner.formatMs(deck.durationMs),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pitch / Speed Slider (+/- 50%)
            Text(
                text = "Speed: ${String.format("%.2fx", deck.pitchSpeed)}",
                style = MaterialTheme.typography.labelSmall
            )
            Slider(
                value = deck.pitchSpeed,
                onValueChange = { deck.setPitchAndSpeed(it) },
                valueRange = 0.5f..1.5f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Deck Volume
            Text(
                text = "Volume: ${(deck.volume * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall
            )
            Slider(
                value = deck.volume,
                onValueChange = { deck.setDeckVolume(it) },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Audio FX Pad Toggles
            Text("Deck FX", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DJPadButton("Flanger", deck.isFlangerActive) { deck.toggleFlanger() }
                DJPadButton("Reverb", deck.isReverbActive) { deck.toggleReverb() }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DJPadButton("Echo", deck.isEchoActive) { deck.toggleEcho() }
                DJPadButton("Crush", deck.isCrushActive) { deck.toggleCrush() }
            }
        }
    }

    if (showTrackSelector) {
        AlertDialog(
            onDismissRequest = { showTrackSelector = false },
            title = { Text("Select Track for ${deck.deckName}") },
            text = {
                if (audioLibrary.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No audio tracks available in library.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            showTrackSelector = false
                            onImportClicked()
                        }) {
                            Text("Import Audio Files")
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(audioLibrary, key = { it.id }) { track ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        deck.loadTrack(track)
                                        showTrackSelector = false
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(track.title, style = MaterialTheme.typography.titleSmall)
                                    Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrackSelector = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun DJPadButton(label: String, isActive: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.size(width = 65.dp, height = 36.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EqualizerScreen(eqController: EqualizerController) {
    // SAFE_EQ_DOLBY_V3
    val context = LocalContext.current
    Column(
        modifier = Modifier
  .fillMaxSize()
  .padding(16.dp)
    ) {
        Row(
  modifier = Modifier.fillMaxWidth(),
  horizontalArrangement = Arrangement.SpaceBetween,
  verticalAlignment = Alignment.CenterVertically
        ) {
  Column {
      Text(
          text = "Detailed Equalizer",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold
      )
      Text(
          text = "10-Band Audio Frequency Processor",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
      )
  }
  Switch(
      checked = eqController.isEnabled,
      onCheckedChange = { eqController.toggleEnable() }
  )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dolby Atmos", fontWeight = FontWeight.Bold)
                    Text("Use the phone's hardware/vendor audio processing without stacking aggressive EQ.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = {
                    val packages = listOf("com.dolby.daxappui2", "com.dolby.daxappui")
                    val intent = packages.asSequence().mapNotNull { pkg -> context.packageManager.getLaunchIntentForPackage(pkg) }.firstOrNull()
                    if (intent != null) context.startActivity(intent)
                    else Toast.makeText(context, "Dolby Atmos is not available on this device", Toast.LENGTH_SHORT).show()
                }) { Text("Open Dolby") }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("EQ Presets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
  items(eqController.presets) { preset ->
      FilterChip(
          selected = eqController.selectedPreset == preset,
          onClick = { eqController.applyPreset(preset) },
          label = { Text(preset) }
      )
  }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Card(
  modifier = Modifier
      .fillMaxWidth()
      .weight(1f),
  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
  Column(
      modifier = Modifier
          .fillMaxSize()
          .padding(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
  ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
      ) {
          Text(
              text = "10-BAND EQ (dB GAIN)",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
          )
          Text(
              text = if (eqController.isEnabled) "ACTIVE" else "BYPASSED (High Quality)",
              style = MaterialTheme.typography.labelSmall,
              color = if (eqController.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
          )
      }

      Spacer(modifier = Modifier.height(12.dp))

      Row(
          modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
      ) {
          eqController.bands.forEachIndexed { index, band ->
              Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier
                      .weight(1f)
                      .fillMaxHeight(),
                  verticalArrangement = Arrangement.SpaceBetween
              ) {
                  Text(
                      text = if (band.currentLevelDb > 0) "+${band.currentLevelDb}" else "${band.currentLevelDb}",
                      style = MaterialTheme.typography.labelSmall,
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold
                  )

                  VerticalFader(
                      value = band.currentLevelDb.toFloat(),
                      onValueChange = { newVal ->
                          eqController.updateBandLevel(index, newVal.toInt())
                      },
                      modifier = Modifier
                          .weight(1f)
                          .fillMaxHeight()
                  )

                  Text(
                      text = band.name,
                      style = androidx.compose.ui.text.TextStyle(fontSize = 8.sp),
                      fontWeight = FontWeight.Bold,
                      maxLines = 1
                  )
              }
          }
      }
  }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
  Column(modifier = Modifier.padding(16.dp)) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
      ) {
          Text("BASS BOOST", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
          Text(
              "${(eqController.bassBoostLevel * 100).toInt()}%",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
          )
      }
      Slider(
          value = eqController.bassBoostLevel,
          onValueChange = { eqController.updateBassBoost(it) },
          valueRange = 0f..1f,
          modifier = Modifier.fillMaxWidth()
      )
  }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
  Column(modifier = Modifier.padding(16.dp)) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
      ) {
          Text("TREBLE BOOST", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
          Text(
              "${(eqController.trebleBoostLevel * 100).toInt()}%",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
          )
      }
      Slider(
          value = eqController.trebleBoostLevel,
          onValueChange = { eqController.updateTrebleBoost(it) },
          valueRange = 0f..1f,
          modifier = Modifier.fillMaxWidth()
      )
  }
        }
    }
}

@Composable
fun VerticalFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestValue by rememberUpdatedState(value)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(44.dp)
            .pointerInput(Unit) {
                var workingValue = latestValue
                detectVerticalDragGestures(
                    onDragStart = {
                        workingValue = latestValue
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val height = size.height.toFloat()
                        if (height > 0f) {
                            val delta = -dragAmount / height * 12f
                            workingValue = (workingValue + delta).coerceIn(-6f, 6f)
                            onValueChange(workingValue)
                        }
                    },
                    onDragEnd = {},
                    onDragCancel = {}
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val height = size.height.toFloat()
                    if (height > 0f) {
                        val fraction = 1f - (offset.y / height)
                        val newVal = (-6f + fraction * 12f).coerceIn(-12f, 12f)
                        onValueChange(newVal)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight(0.85f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(3.dp))
        )
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        )
        val fraction = ((latestValue + 6f) / 12f).coerceIn(0f, 1f)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxHeight(0.85f)
                .width(44.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            val trackH = maxHeight
            val thumbY = trackH * fraction - 14.dp
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .offset(y = -thumbY)
                    .shadow(4.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
                )
            }
        }
    }
}
