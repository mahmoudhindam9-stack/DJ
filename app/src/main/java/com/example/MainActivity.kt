package com.example

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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.example.onlinemusic.OnlineDjBridge
import com.example.onlinemusic.OnlineDeckTarget
import com.example.player.*
import com.example.studio.MusicStudioController
import com.example.studio.MusicStudioScreen
// STUDIO_CONTROLS_V1
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.MusicScanner
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            try {
                val pInfo = packageManager.getPackageInfo(packageName, 0)
                val version = pInfo.versionName ?: "1.0"
                GitHubUpdater.checkForUpdates(this@MainActivity, version, showToast = false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

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
    val prefs = remember { context.getSharedPreferences("app_session", Context.MODE_PRIVATE) }
    val activity = context as? android.app.Activity
    val intentRoute = activity?.intent?.getStringExtra("open_route")
    val initialRoute = remember { intentRoute ?: prefs.getString("last_route", "player") ?: "player" }

    // Persistent State Controllers
    val playerController = remember { AudioPlayerController.obtain(context) }
    val djMixerController = remember { DJMixerController(context) }
    val eqController = remember { EqualizerController(context) }
    val micController = remember { MicController(context) }
    val musicStudioController = remember { MusicStudioController(context) }
    val djFxController = remember { com.example.djfx.DjFxController(context) }

    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            entry.destination.route?.let { route ->
                prefs.edit().putString("last_route", route).apply()
            }
        }
    }

    LaunchedEffect(OnlineDjBridge.request?.id) {
        val request = OnlineDjBridge.request ?: return@LaunchedEffect
        playerController.pause()
        when(request.deck){ OnlineDeckTarget.A -> djMixerController.deckA.loadTrack(request.song); OnlineDeckTarget.B -> djMixerController.deckB.loadTrack(request.song) }
        navController.navigate("dj"){ popUpTo(navController.graph.findStartDestination().id){saveState=true}; launchSingleTop=true; restoreState=true }
        OnlineDjBridge.clear()
    }

    // Master Library and Playlists State & Room DB Repository
    val audioLibrary = remember { mutableStateListOf<AudioItem>().apply { addAll(PlayerLibraryStore.load(context)) } }

    // Persist the library the moment it changes (song imported, removed, etc.)
    // so it survives closing and reopening the app instead of only ever living
    // in memory. Without this, every import was lost as soon as the process died.
    LaunchedEffect(audioLibrary) {
        snapshotFlow { audioLibrary.toList() }
            .collect { snapshot ->
                PlayerLibraryStore.save(context, snapshot)
            }
    }

    val playlists = remember { mutableStateListOf<Playlist>() }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        val missingPermissions = permissionsToRequest.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, it
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            multiplePermissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }


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
            delay(50)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!playerController.isPlaying && MusicService.instance?.playerController !== playerController) {
                playerController.release()
            }
            djMixerController.release()
            eqController.release()
            musicStudioController.close()
            djFxController.release()
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
                    icon = { Icon(Icons.Filled.MusicNote, contentDescription = "Radio") },
                    label = { Text("Radio") },
                    selected = currentDestination?.route == "studio",
                    onClick = {
                        navController.navigate("studio") {
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
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Cloud, contentDescription = "Online Music") },
                    label = { Text("Online") },
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
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = initialRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
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
                    onPauseMainPlayer = { playerController.pause() }
                )
            }
            composable("equalizer") {
                EqualizerScreen(eqController = eqController)
            }
            composable("mic") {
                MicScreen(micController = micController, scope = scope)
            }
            composable("studio") {
                MusicStudioScreen(musicStudioController)
            }
            composable("full_player") {
                FullPlayerScreen(playerController = playerController, onBack = { navController.popBackStack() })
            }
            composable("online_music") {
                val repo = remember { com.example.onlinemusic.OnlineMusicRepository() }
                val vm = remember { com.example.onlinemusic.OnlineMusicViewModel(repo) }
                com.example.onlinemusic.OnlineMusicScreen(vm, playerController)
            }
        }
    }
}

// KARAOKE_MIC_PAGE_V5
// MIC_RECORDING_FORMAT_V1























