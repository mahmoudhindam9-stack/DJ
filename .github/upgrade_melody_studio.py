from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
MELODY = ROOT / "app/src/main/java/com/example/MelodyStudio.kt"


def patch_main():
    text = MAIN.read_text(encoding="utf-8")
    changed = False

    # Wire the existing library into MicScreen so composed melodies are immediately
    # available to the same Player/Library session.
    old_call = 'MicScreen(micController = micController, scope = scope)'
    new_call = 'MicScreen(micController = micController, audioLibrary = audioLibrary, context = context, scope = scope)'
    if old_call in text:
        text = text.replace(old_call, new_call, 1)
        changed = True

    old_sig = 'fun MicScreen(micController: MicController, scope: kotlinx.coroutines.CoroutineScope) {'
    new_sig = 'fun MicScreen(micController: MicController, audioLibrary: SnapshotStateList<AudioItem>, context: Context, scope: kotlinx.coroutines.CoroutineScope) {'
    if old_sig in text:
        text = text.replace(old_sig, new_sig, 1)
        changed = True

    old_melody = '        // MELODY_STUDIO_V2\n        MelodyStudioCard(audioLibrary, context)'
    if old_melody not in text:
        anchor = '        Spacer(Modifier.height(12.dp))\n        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {\n            Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp))'
        insert = '        Spacer(Modifier.height(12.dp))\n        // MELODY_STUDIO_V2\n        MelodyStudioCard(audioLibrary, context)\n\n        Spacer(Modifier.height(12.dp))\n        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {\n            Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp))'
        if anchor in text:
            text = text.replace(anchor, insert, 1)
            changed = True

    MAIN.write_text(text, encoding="utf-8")
    return changed


def patch_melody():
    text = MELODY.read_text(encoding="utf-8")
    changed = False
    if 'fun MelodyStudioCard(audioLibrary: MutableList<AudioItem>, context: Context, djMixerController: DJMixerController)' in text:
        text = text.replace(
            'fun MelodyStudioCard(audioLibrary: MutableList<AudioItem>, context: Context, djMixerController: DJMixerController)',
            'fun MelodyStudioCard(audioLibrary: MutableList<AudioItem>, context: Context)', 1)
        text = text.replace('import com.example.player.DJMixerController\n', '', 1)
        changed = True

    replacements = {
        '"ألّف لحن بالنقر على النغمات ثم شغّله أو احفظه كأغنية جديدة داخل المكتبة."': '"Compose a melody by tapping notes, then play it or save it as a new song in your library."',
        '"اللحن فارغ"': '"Melody is empty"',
        '"اللحن: ${sequence.joinToString(" – ") { it.name }}"': '"Melody: ${sequence.joinToString(" – ") { it.name }}"',
        '"تشغيل"': '"Play"',
        '"عزف متكرر"': '"Loop"',
        '"مسح"': '"Clear"',
        '"حفظ اللحن في مكتبة الأغاني"': '"Save to Music Library"',
        '"تم حفظ ${item.title}"': '"Saved ${item.title}"',
    }
    for old, new in replacements.items():
        if old in text:
            text = text.replace(old, new)
            changed = True

    MELODY.write_text(text, encoding="utf-8")
    return changed


if __name__ == "__main__":
    changed = patch_main() or False
    changed = patch_melody() or changed
    print("Melody Studio upgrade applied" if changed else "Melody Studio already up to date")
