from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main/java/com/example/MainActivity.kt'
PLAYER = ROOT / 'app/src/main/java/com/example/player/AudioPlayerController.kt'
SERVICE = ROOT / 'app/src/main/java/com/example/player/MusicService.kt'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'{label}: source block not found')
    return text.replace(old, new, 1)


def normalize_main(text: str) -> str:
    if 'AudioPlayerController.obtain(context)' not in text:
        text = replace_once(text, 'val playerController = remember { AudioPlayerController(context) }', 'val playerController = remember { AudioPlayerController.obtain(context) }', 'main controller ownership')
    old_library = 'val audioLibrary = remember { mutableStateListOf<AudioItem>() }'
    if old_library in text:
        text = replace_once(text, old_library, 'val audioLibrary = remember { mutableStateListOf<AudioItem>().apply { addAll(PlayerLibraryStore.load(context)) } }', 'persistent audio library')
    old_release = '''        onDispose {
            playerController.release()
            djMixerController.release()'''
    if old_release in text:
        text = replace_once(text, old_release, '''        onDispose {
            if (!playerController.isPlaying && MusicService.instance?.playerController !== playerController) {
                playerController.release()
            }
            djMixerController.release()''', 'player release ownership')
    old_call = '''                PlayerScreen(
                    playerController = playerController,
                    audioLibrary = audioLibrary,
                    playlists = playlists,
                    selectedPlaylistId = selectedPlaylistId,
                    onSelectPlaylist = { id -> selectedPlaylistId = id },
                    onPauseDJ = { djMixerController.pauseAll() },
                    navController = navController
                )'''
    new_call = '''                PlayerScreenV2(
                    playerController = playerController,
                    audioLibrary = audioLibrary,
                    playlists = playlists,
                    onPauseDJ = { djMixerController.pauseAll() },
                    navController = navController
                )'''
    if old_call in text:
        text = replace_once(text, old_call, new_call, 'player screen replacement')
    return text


def normalize_player(text: str) -> str:
    companion_start = '    companion object {\n'
    obtain_block = '''        @JvmStatic
        fun obtain(context: Context): AudioPlayerController {
            return activeInstance
                ?: MusicService.instance?.playerController
                ?: AudioPlayerController(context.applicationContext)
        }

'''
    if 'fun obtain(context: Context): AudioPlayerController' not in text:
        text = replace_once(text, companion_start, companion_start + obtain_block, 'player companion helper')

    old_restore = '            exoPlayer.playWhenReady = savedPlaying'
    if old_restore in text:
        text = replace_once(text, old_restore, '''            val canAutoResume = MusicService.instance?.playerController == null || MusicService.instance?.playerController === this
            exoPlayer.playWhenReady = savedPlaying && canAutoResume''', 'session resume guard')

    old_sync = '''            val serviceIntent = Intent(context, MusicService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            MusicService.instance?.playerController = this'''
    if old_sync in text:
        text = replace_once(text, old_sync, '''            val existingController = MusicService.instance?.playerController
            if (existingController != null && existingController !== this) return
            val serviceIntent = Intent(context, MusicService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            MusicService.instance?.playerController = this''', 'service ownership guard')
    return text


def normalize_service(text: str) -> str:
    text = text.replace('playerController = AudioPlayerController.activeInstance ?: AudioPlayerController(applicationContext)', 'playerController = AudioPlayerController.obtain(applicationContext)', 1)
    text = text.replace('if (playerController == null) playerController = AudioPlayerController.activeInstance ?: AudioPlayerController(applicationContext)', 'if (playerController == null) playerController = AudioPlayerController.obtain(applicationContext)', 1)
    return text


def main() -> None:
    MAIN.write_text(normalize_main(MAIN.read_text(encoding='utf-8')), encoding='utf-8')
    PLAYER.write_text(normalize_player(PLAYER.read_text(encoding='utf-8')), encoding='utf-8')
    SERVICE.write_text(normalize_service(SERVICE.read_text(encoding='utf-8')), encoding='utf-8')
    print('Player, playlist, queue and session ownership normalized')


if __name__ == '__main__':
    main()
