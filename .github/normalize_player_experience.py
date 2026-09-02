from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main/java/com/example/MainActivity.kt'
PLAYER = ROOT / 'app/src/main/java/com/example/player/AudioPlayerController.kt'
SERVICE = ROOT / 'app/src/main/java/com/example/player/MusicService.kt'
PLAYER_UI = ROOT / 'app/src/main/java/com/example/MainPlayerExperience.kt'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'{label}: source block not found')
    return text.replace(old, new, 1)


def dedupe_player_guards(text: str) -> str:
    guard = '''            val existingController = MusicService.instance?.playerController\n            if (existingController != null && existingController !== this) return\n'''
    text = re.sub(r'(?:' + re.escape(guard) + r'){2,}', guard, text)
    resume = '            val canAutoResume = MusicService.instance?.playerController == null || MusicService.instance?.playerController === this\n'
    text = re.sub(r'(?:' + re.escape(resume) + r'){2,}', resume, text)
    text = re.sub(r'            exoPlayer\.playWhenReady = savedPlaying(?: && canAutoResume)+\n', '            exoPlayer.playWhenReady = savedPlaying && canAutoResume\n', text, count=1)
    return text


def remove_controls_navigation(text: str) -> str:
    block = '''                NavigationBarItem(\n                    icon = { Icon(Icons.Filled.NotificationsActive, contentDescription = "Controls") },\n                    label = { Text("Controls") },\n                    selected = currentDestination?.route == "controls",\n                    onClick = {\n                        navController.navigate("controls") {\n                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }\n                            launchSingleTop = true\n                            restoreState = true\n                        }\n                    }\n                )\n'''
    text = text.replace(block, '', 1)
    route = '''            composable("controls") {\n                NotificationControlScreen(context = context)\n            }\n'''
    text = text.replace(route, '', 1)
    return text


def normalize_main(text: str) -> str:
    if 'AudioPlayerController.obtain(context)' not in text:
        text = replace_once(text, 'val playerController = remember { AudioPlayerController(context) }', 'val playerController = remember { AudioPlayerController.obtain(context) }', 'main controller ownership')
    old_library = 'val audioLibrary = remember { mutableStateListOf<AudioItem>() }'
    if old_library in text:
        text = replace_once(text, old_library, 'val audioLibrary = remember { mutableStateListOf<AudioItem>().apply { addAll(PlayerLibraryStore.load(context)) } }', 'persistent audio library')
    old_release = '''        onDispose {\n            playerController.release()\n            djMixerController.release()'''
    if old_release in text:
        text = replace_once(text, old_release, '''        onDispose {\n            if (!playerController.isPlaying && MusicService.instance?.playerController !== playerController) {\n                playerController.release()\n            }\n            djMixerController.release()''', 'player release ownership')
    old_call = '''                PlayerScreen(\n                    playerController = playerController,\n                    audioLibrary = audioLibrary,\n                    playlists = playlists,\n                    selectedPlaylistId = selectedPlaylistId,\n                    onSelectPlaylist = { id -> selectedPlaylistId = id },\n                    onPauseDJ = { djMixerController.pauseAll() },\n                    navController = navController\n                )'''
    new_call = '''                PlayerScreenV2(\n                    playerController = playerController,\n                    audioLibrary = audioLibrary,\n                    playlists = playlists,\n                    onPauseDJ = { djMixerController.pauseAll() },\n                    navController = navController\n                )'''
    if old_call in text:
        text = replace_once(text, old_call, new_call, 'player screen replacement')
    return remove_controls_navigation(text)


def normalize_player_ui(text: str) -> str:
    marker = '    val scanDevice = {\n'
    start = text.find(marker)
    if start == -1:
        return text
    end_marker = '\n\n    if (showNowPlaying && playerController.currentSong != null) {'
    end = text.find(end_marker, start)
    if end == -1:
        raise SystemExit('device scan block: end marker not found')
    replacement = '''    fun runDeviceScan() {\n        scope.launch {\n            val songs = withContext(Dispatchers.IO) { MusicScanner.scanMediaStoreAudio(context) }\n            addToLibrary(audioLibrary, songs, context)\n            infoMessage = "Scanned ${songs.size} device song(s)"\n        }\n    }\n\n    val scanPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->\n        if (granted) runDeviceScan()\n        else infoMessage = "Storage permission denied; device music was not scanned"\n    }\n\n    val scanDevice = {\n        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {\n            Manifest.permission.READ_MEDIA_AUDIO\n        } else {\n            Manifest.permission.READ_EXTERNAL_STORAGE\n        }\n        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {\n            runDeviceScan()\n        } else {\n            scanPermissionLauncher.launch(permission)\n        }\n    }'''
    return text[:start] + replacement + text[end:]


def normalize_player(text: str) -> str:
    text = dedupe_player_guards(text)
    companion_start = '    companion object {\n'
    obtain_block = '''        @JvmStatic\n        fun obtain(context: Context): AudioPlayerController {\n            return activeInstance\n                ?: MusicService.instance?.playerController\n                ?: AudioPlayerController(context.applicationContext)\n        }\n\n'''
    if 'fun obtain(context: Context): AudioPlayerController' not in text:
        text = replace_once(text, companion_start, companion_start + obtain_block, 'player companion helper')
    if 'val canAutoResume' not in text:
        old_restore = '            exoPlayer.playWhenReady = savedPlaying\n'
        if old_restore in text:
            text = replace_once(text, old_restore, '''            val canAutoResume = MusicService.instance?.playerController == null || MusicService.instance?.playerController === this\n            exoPlayer.playWhenReady = savedPlaying && canAutoResume\n''', 'session resume guard')
    old_sync = '''            val serviceIntent = Intent(context, MusicService::class.java)\n            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {\n                context.startForegroundService(serviceIntent)\n            } else {\n                context.startService(serviceIntent)\n            }\n            MusicService.instance?.playerController = this'''
    if old_sync in text and 'val existingController = MusicService.instance?.playerController' not in text:
        text = replace_once(text, old_sync, '''            val existingController = MusicService.instance?.playerController\n            if (existingController != null && existingController !== this) return\n            val serviceIntent = Intent(context, MusicService::class.java)\n            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {\n                context.startForegroundService(serviceIntent)\n            } else {\n                context.startService(serviceIntent)\n            }\n            MusicService.instance?.playerController = this''', 'service ownership guard')
    return dedupe_player_guards(text)


def normalize_service(text: str) -> str:
    text = text.replace('playerController = AudioPlayerController.activeInstance ?: AudioPlayerController(applicationContext)', 'playerController = AudioPlayerController.obtain(applicationContext)', 1)
    text = text.replace('if (playerController == null) playerController = AudioPlayerController.activeInstance ?: AudioPlayerController(applicationContext)', 'if (playerController == null) playerController = AudioPlayerController.obtain(applicationContext)', 1)
    return text


def main() -> None:
    MAIN.write_text(normalize_main(MAIN.read_text(encoding='utf-8')), encoding='utf-8')
    PLAYER_UI.write_text(normalize_player_ui(PLAYER_UI.read_text(encoding='utf-8')), encoding='utf-8')
    PLAYER.write_text(normalize_player(PLAYER.read_text(encoding='utf-8')), encoding='utf-8')
    SERVICE.write_text(normalize_service(SERVICE.read_text(encoding='utf-8')), encoding='utf-8')
    print('Player, playlist, queue, device scan and session ownership normalized')


if __name__ == '__main__':
    main()
