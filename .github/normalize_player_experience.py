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
    text = replace_once(
        text,
        'val playerController = remember { AudioPlayerController(context) }',
        'val playerController = remember { AudioPlayerController.obtain(context) }',
        'main controller ownership'
    ) if 'AudioPlayerController.obtain(context)' not in text else text

    old_library = 'val audioLibrary = remember { mutableStateListOf<AudioItem>() }'
    new_library = 'val audioLibrary = remember { mutableStateListOf<AudioItem>().apply { addAll(PlayerLibraryStore.load(context)) } }'
    text = replace_once(text, old_library, new_library, 'persistent audio library') if old_library in text else text

    old_release = '''        onDispose {
            playerController.release()
            djMixerController.release()'''
    new_release = '''        onDispose {
            if (!playerController.isPlaying && MusicService.instance?.playerController !== playerController) {
                playerController.release()
            }
            djMixerController.release()'''
    text = replace_once(text, old_release, new_release, 'player release ownership') if old_release in text else text

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
    text = replace_once(text, old_call, new_call, 'player screen replacement') if old_call in text else text
    return text


def normalize_player(text: str) -> str:
    if 'fun obtain(context: Context): AudioPlayerController' not in text:
        marker = '    init {\n'
        helper = '''    companion object {\n        private const val PREFS_NAME = "dj_player_session"\n        private const val KEY_QUEUE = "queue"\n        private const val KEY_INDEX = "index"\n        private const val KEY_POSITION = "position"\n        private const val KEY_PLAYING = "playing"\n        private const val KEY_SHUFFLE = "shuffle"\n        private const val KEY_REPEAT = "repeat"\n        private const val KEY_VOLUME = "volume"\n        const val KEY_TITLE = "title"\n        const val KEY_ARTIST = "artist"\n\n        @JvmStatic\n        fun obtain(context: Context): AudioPlayerController {\n            return activeInstance\n                ?: MusicService.instance?.playerController\n                ?: AudioPlayerController(context.applicationContext)\n        }\n\n        @JvmStatic\n        var activeInstance: AudioPlayerController? = null\n            private set\n\n        @JvmStatic\n        var activePreferredAudioDevice: AudioDeviceInfo? = null\n            private set\n\n        @JvmStatic\n        @OptIn(UnstableApi::class)\n        fun updateGlobalPreferredAudioDevice(device: AudioDeviceInfo?) {\n            activePreferredAudioDevice = device\n            activeInstance?.setPreferredAudioDevice(device)\n        }\n    }\n\n'''
        text = replace_once(text, marker, helper + marker, 'player companion injection')
    else:
        # Existing companion in source is kept; the injected helper is intentionally idempotent.
        pass

    old_restore = '            exoPlayer.playWhenReady = savedPlaying'
    new_restore = '''            val canAutoResume = MusicService.instance?.playerController == null || MusicService.instance?.playerController === this\n            exoPlayer.playWhenReady = savedPlaying && canAutoResume'''
    text = replace_once(text, old_restore, new_restore, 'session resume guard') if old_restore in text else text

    old_sync = '''            val serviceIntent = Intent(context, MusicService::class.java)\n            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {\n                context.startForegroundService(serviceIntent)\n            } else {\n                context.startService(serviceIntent)\n            }\n            MusicService.instance?.playerController = this'''
    new_sync = '''            val existingController = MusicService.instance?.playerController\n            if (existingController != null && existingController !== this) return\n            val serviceIntent = Intent(context, MusicService::class.java)\n            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {\n                context.startForegroundService(serviceIntent)\n            } else {\n                context.startService(serviceIntent)\n            }\n            MusicService.instance?.playerController = this'''
    text = replace_once(text, old_sync, new_sync, 'service ownership guard') if old_sync in text else text

    return text


def normalize_service(text: str) -> str:
    old = '        playerController = AudioPlayerController.activeInstance ?: AudioPlayerController(applicationContext)'
    new = '        playerController = AudioPlayerController.obtain(applicationContext)'
    text = replace_once(text, old, new, 'service controller reuse') if old in text else text
    old2 = '        if (playerController == null) playerController = AudioPlayerController.activeInstance ?: AudioPlayerController(applicationContext)'
    new2 = '        if (playerController == null) playerController = AudioPlayerController.obtain(applicationContext)'
    text = replace_once(text, old2, new2, 'service controller guard') if old2 in text else text
    return text


def main() -> None:
    MAIN.write_text(normalize_main(MAIN.read_text(encoding='utf-8')), encoding='utf-8')
    PLAYER.write_text(normalize_player(PLAYER.read_text(encoding='utf-8')), encoding='utf-8')
    SERVICE.write_text(normalize_service(SERVICE.read_text(encoding='utf-8')), encoding='utf-8')
    print('Player, playlist, queue and session ownership normalized')


if __name__ == '__main__':
    main()
