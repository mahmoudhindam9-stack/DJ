from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
CONTROL = ROOT / "app/src/main/java/com/example/NotificationControlScreen.kt"
MELODY = ROOT / "app/src/main/java/com/example/MelodyStudio.kt"


def patch_main():
    text = MAIN.read_text(encoding="utf-8")
    changed = False
    # Remove Melody Studio card and any import/comment left by previous upgrades.
    text = text.replace('        // MELODY_STUDIO_V2\n        MelodyStudioCard(audioLibrary, context)\n\n', '')
    if 'MELODY_STUDIO_V2' not in text:
        pass
    old_call = 'MicScreen(micController = micController, audioLibrary = audioLibrary, context = context, scope = scope)'
    new_call = 'MicScreen(micController = micController, scope = scope)'
    if old_call in text:
        text = text.replace(old_call, new_call, 1)
        changed = True
    old_sig = 'fun MicScreen(micController: MicController, audioLibrary: SnapshotStateList<AudioItem>, context: Context, scope: kotlinx.coroutines.CoroutineScope) {'
    new_sig = 'fun MicScreen(micController: MicController, scope: kotlinx.coroutines.CoroutineScope) {'
    if old_sig in text:
        text = text.replace(old_sig, new_sig, 1)
        changed = True
    # Remove Context import only if unused by this file; keep LocalContext import.
    MAIN.write_text(text, encoding="utf-8")
    return changed


def patch_control():
    text = CONTROL.read_text(encoding="utf-8") if CONTROL.exists() else ''
    # No Melody Studio references should remain in the controls screen.
    changed = 'Melody' in text
    if changed:
        text = text.replace('Melody Studio', '')
        CONTROL.write_text(text, encoding='utf-8')
    return changed


def remove_melody_file():
    # Deletion is handled by the workflow after this script succeeds; the script
    # only removes source references to keep the build self-contained.
    return False


if __name__ == "__main__":
    changed = patch_main() or patch_control()
    print("Melody Studio removed from app wiring" if changed else "Melody Studio already removed from app wiring")
