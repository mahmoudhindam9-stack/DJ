from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
MELODY = ROOT / "app/src/main/java/com/example/MelodyStudio.kt"
MARK = "// MELODY_STUDIO_V2"


def patch_main():
    text = MAIN.read_text(encoding="utf-8")
    if MARK in text:
        return False
    anchor = '''        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp))'''
    insert = '''        Spacer(Modifier.height(12.dp))
        // MELODY_STUDIO_V2
        MelodyStudioCard(audioLibrary, context)

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp))'''
    if anchor not in text:
        raise SystemExit("Karaoke footer anchor not found")
    text = text.replace(anchor, insert, 1)
    MAIN.write_text(text, encoding="utf-8")
    return True


def patch_melody():
    text = MELODY.read_text(encoding="utf-8")
    text = text.replace(
        'fun MelodyStudioCard(audioLibrary: MutableList<AudioItem>, context: Context, djMixerController: DJMixerController) {',
        'fun MelodyStudioCard(audioLibrary: MutableList<AudioItem>, context: Context) {',
        1,
    )
    text = text.replace('import com.example.player.DJMixerController\n', '', 1)
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
        text = text.replace(old, new)
    MELODY.write_text(text, encoding="utf-8")
    return True


if __name__ == "__main__":
    changed = patch_main() or False
    changed = patch_melody() or changed
    print("Melody Studio upgrade applied" if changed else "Melody Studio already up to date")
