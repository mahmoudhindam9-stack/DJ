package com.example

import com.example.diagnostics.RuntimeDiagnostics
import com.example.ui.components.PremiumBackdrop

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.updater.GitHubUpdater

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
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
import com.example.onlinemusic.OnlineDjBridge
import com.example.onlinemusic.OnlineDeckTarget
import com.example.player.*
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.MusicScanner
import kotlinx.coroutines.delay

import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RuntimeDiagnostics.initialize(applicationContext)
        com.example.ui.theme.ThemeManager.init(this)
        enableEdgeToEdge()
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val pInfo = packageManager.getPackageInfo(packageName, 0)
                val version = pInfo.versionName ?: "1.0"
                GitHubUpdater.checkForUpdates(this@MainActivity, version, showToast = false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        setContent {
            val appTheme by com.example.ui.theme.ThemeManager.currentTheme.collectAsState()
            MyApplicationTheme(appTheme = appTheme) {
                MainApp()
            }
        }
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun MainApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry?.destination?.route) {
        RuntimeDiagnostics.setScreen(navBackStackEntry?.destination?.route)
    }

    var appVersion by remember { mutableStateOf("1.0") }
    LaunchedEffect(Unit) {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            appVersion = pInfo.versionName ?: "1.0"
            
        } catch (e: Exception) {}
    }

    // Persistent State Controllers
    val playerController = remember { AudioPlayerController.obtain(context).apply { activityCount++ } }
    val djMixerController = remember { DJMixerController(context) }
    val eqController = remember { EqualizerController(context) }
    val micController = remember { MicController(context) }
    val djFxController = remember { com.example.djfx.DjFxController(context) }

    LaunchedEffect(OnlineDjBridge.request?.id) {
        val request = OnlineDjBridge.request ?: return@LaunchedEffect
        playerController.pause()
        when(request.deck){ OnlineDeckTarget.A -> djMixerController.deckA.loadTrack(request.song); OnlineDeckTarget.B -> djMixerController.deckB.loadTrack(request.song) }
        navController.navigate("dj"){ popUpTo(navController.graph.findStartDestination().id){saveState=true}; launchSingleTop=true; restoreState=true }
        OnlineDjBridge.clear()
    }

    // Master Library and Playlists State & Room DB Repository
    val audioLibrary = remember { mutableStateListOf<AudioItem>() }

    LaunchedEffect(Unit) {
        val loaded = PlayerLibraryStore.load(context)
        audioLibrary.clear()
        audioLibrary.addAll(loaded)
    }

    // Persist the library the moment it changes (song imported, removed, etc.)
    // so it survives closing and reopening the app instead of only ever living
    // in memory. Without this, every import was lost as soon as the process died.
    LaunchedEffect(audioLibrary) {
        snapshotFlow { audioLibrary.toList() }
            .collect { snapshot ->
                if (snapshot.isNotEmpty() || PlayerLibraryStore.isLoaded) {
                    PlayerLibraryStore.save(context, snapshot)
                }
            }
    }

    val playlists = remember { mutableStateListOf<Playlist>() }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }


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
    }

    DisposableEffect(Unit) {
        onDispose {
            playerController.activityCount--
            playerController.checkRelease()
            djMixerController.release()
            eqController.release()
            djFxController.release()
            micController.close()
        }
    }

    PremiumBackdrop {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .shadow(24.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp)),
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = "Player") },
                    label = { Text("Player", maxLines = 1, softWrap = false) },
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
                    label = { Text("DJ", maxLines = 1, softWrap = false) },
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
                    label = { Text("EQ", maxLines = 1, softWrap = false) },
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
                    label = { Text("Mic", maxLines = 1, softWrap = false) },
                    selected = currentDestination?.route == "mic",
                    onClick = {
                        navController.navigate("mic") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Radio, contentDescription = "Radio") },
                    label = { Text("Radio", maxLines = 1, softWrap = false) },
                    selected = currentDestination?.route == "radio",
                    onClick = {
                        navController.navigate("radio") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Cloud, contentDescription = "Online Music") },
                    label = { Text("Online", maxLines = 1, softWrap = false) },
                    selected = currentDestination?.route == "online_music",
                    onClick = {
                        navController.navigate("online_music") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "player",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("weather") {
                WeatherScreen(navController = navController)
            }
            composable("player") {
                PlayerScreenV2(
                    playerController = playerController,
                    audioLibrary = audioLibrary,
                    playlists = playlists,
                    onPauseDJ = { djMixerController.pauseAll() },
                    navController = navController
                )
            }
            composable("dj") {
                DJMixerScreen(
                    djMixerController = djMixerController,
                    djFxController = djFxController,
                    audioLibrary = audioLibrary,
                    micController = micController,
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
            
            composable("radio") {
                com.example.radio.RadioScreen(
                    playerController = playerController,
                    audioLibrary = audioLibrary,
                    playlists = playlists,
                    playlistRepo = playlistRepo
                )
            }
            composable("online_music") {
                val repo = remember { com.example.onlinemusic.OnlineMusicRepository() }
                val vm = remember { com.example.onlinemusic.OnlineMusicViewModel(repo) }
                com.example.onlinemusic.OnlineMusicScreen(
                    viewModel = vm,
                    playerController = playerController,
                    playlists = playlists,
                    audioLibrary = audioLibrary,
                    playlistRepo = playlistRepo
                )
            }
        }
    }
    }
}
// KARAOKE_MIC_PAGE_V5
// MIC_RECORDING_FORMAT_V1























