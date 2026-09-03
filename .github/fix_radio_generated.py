from pathlib import Path

path = Path(__file__).resolve().parents[1] / 'app/src/main/java/com/example/studio/MusicStudioScreen.kt'
s = path.read_text(encoding='utf-8')

if 'import kotlinx.coroutines.launch' not in s:
    s = s.replace('import kotlinx.coroutines.withContext\n', 'import kotlinx.coroutines.withContext\nimport kotlinx.coroutines.launch\n')
if 'val scope = rememberCoroutineScope()' not in s:
    s = s.replace('val playerController = remember { AudioPlayerController.obtain(context) }\n', 'val playerController = remember { AudioPlayerController.obtain(context) }\n    val scope = rememberCoroutineScope()\n')
s = s.replace('''                    val next = (attempts[id] ?: 0) + 1\n                if (next < station.streamUrls.size) {\n                    attempts[id] = next\n                    stationStatus[id] = RadioStatus.LOADING\n                    val scope = androidx.compose.runtime.rememberCoroutineScope\n                } else {\n                    stationStatus[id] = RadioStatus.FAILED\n                }''', '''                val next = (attempts[id] ?: 0) + 1\n                if (next < station.streamUrls.size) {\n                    attempts[id] = next\n                    stationStatus[id] = RadioStatus.LOADING\n                    scope.launch { playStation(station, next) }\n                } else {\n                    stationStatus[id] = RadioStatus.FAILED\n                }''')
s = s.replace('''FilledIconButton(onClick = { if (status == RadioStatus.LIVE && current) playerController.pause() else androidx.compose.runtime.LaunchedEffect(Unit) { playStation(station) } })''', '''FilledIconButton(onClick = { if (status == RadioStatus.LIVE && current) playerController.pause() else scope.launch { playStation(station) } })''')
path.write_text(s, encoding='utf-8')
