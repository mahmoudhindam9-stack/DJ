package com.example

import android.Manifest
import android.content.Context
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

    val playerController = remember { AudioPlayerController(context) }
    val djMixerController = remember { DJMixerController(context) }
    val eqController = remember { EqualizerController() }
    val micController = remember { MicController(context) }

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
                    icon = { Icon(Icons.Filled.NotificationsActive, contentDescription = "Controls") },
                    label = { Text("Controls") },
                    selected = currentDestination?.route == "controls",
                    onClick = {
                        navController.navigate("controls") {
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
                MicScreen(micController = micController, audioLibrary = audioLibrary, context = context, scope = scope)
            }
            composable("controls") {
                NotificationControlScreen(context = context)
            }
            composable("full_player") {
                FullPlayerScreen(playerController = playerController, onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
// KARAOKE_DJ_ENGLISH_V2
fun MicScreen(micController: MicController, audioLibrary: SnapshotStateList<AudioItem>, context: Context, scope: kotlinx.coroutines.CoroutineScope) {
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
                        Text(micController.selectedInputDevice?.displayName() ?: "System Default Mic", maxLines = 1)
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
                        Text(micController.selectedOutputDevice?.displayName() ?: "System Default Output", maxLines = 1)
                    }
                    DropdownMenu(outputExpanded, { outputExpanded = false }) {
                        DropdownMenuItem(text = { Text("System Default Output") }, onClick = { micController.selectOutputDevice(null); outputExpanded = false })
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) micController.outputDevices.forEach { device ->
                            DropdownMenuItem(text = { Text(device.productName?.toString()?.ifBlank { "Audio Output ${device.id}" } ?: "Audio Output ${device.id}") }, onClick = { micController.selectOutputDevice(device); outputExpanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Spacer(Modifier.height(8.dp))
                Button(onClick = { micController.refreshDevices() }, Modifier.fillMaxWidth()) {
                    Text("Refresh connected devices")
                }
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
    }
}
