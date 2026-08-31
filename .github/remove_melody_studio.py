from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
MELODY = ROOT / "app/src/main/java/com/example/MelodyStudio.kt"

text = MAIN.read_text(encoding="utf-8")
text = text.replace("import androidx.compose.runtime.snapshots.SnapshotStateList\n", "")
text = text.replace("import com.example.model.AudioItem\n", "import com.example.model.AudioItem\n")
text = text.replace('audioLibrary = audioLibrary, context = context, scope = scope', 'context = context, scope = scope')
text = text.replace('MicScreen(micController = micController, audioLibrary = audioLibrary, context = context, scope = scope)', 'MicScreen(micController = micController, context = context, scope = scope)')
text = text.replace('fun MicScreen(micController: MicController, audioLibrary: SnapshotStateList<AudioItem>, context: Context, scope: kotlinx.coroutines.CoroutineScope) {', 'fun MicScreen(micController: MicController, context: Context, scope: kotlinx.coroutines.CoroutineScope) {')
text = text.replace('        // MELODY_STUDIO_V2\n        MelodyStudioCard(audioLibrary, context)\n\n', '')
text = text.replace('import androidx.compose.runtime.snapshots.SnapshotStateList\n', '')
MAIN.write_text(text, encoding="utf-8")

if MELODY.exists():
    MELODY.unlink()

print("Melody Studio removed from source and Karaoke UI")
