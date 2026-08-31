from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
MELODY = ROOT / "app/src/main/java/com/example/MelodyStudio.kt"
MARK = "// MELODY_STUDIO_V2"


def patch_main():
    text = MAIN.read_text(encoding="utf-8")
    if MARK in text:
        return False
    anchor = '''        Spacer(Modifier.height(12.dp))\n        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {\n            Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp))'''
    insert = '''        Spacer(Modifier.height(12.dp))\n        // MELODY_STUDIO_V2\n        MelodyStudioCard(audioLibrary, context, djMixerController = remember { DJMixerController(context) })\n\n        Spacer(Modifier.height(12.dp))\n        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {\n            Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp))'''
    if anchor not in text:
        raise SystemExit("Karaoke footer anchor not found")
    # Reuse the existing controller created by MainApp by threading it through MicScreen is cleaner,
    # but this screen currently receives only MicController. Use a lightweight local controller for melody output.
    text = text.replace(anchor, insert, 1)
    MAIN.write_text(text, encoding="utf-8")
    return True


def patch_melody():
    text = MELODY.read_text(encoding="utf-8")
    replacements = {
        '"ألّف لحن بالنقر على النغمات ثم شغّله أو احفظه كأغنية جديدة داخل المكتبة。"': '"Compose a melody by tapping notes, then play it or save it as a new song in your library."',
        '"اللحن فارغ"': '"Melody is empty"',
        '"اللحن: ${sequence.joinToString(" – ") { it.name }}"': '"Melody: ${sequence.joinToString(" – ") { it.name }}"',
        '"تشغيل"': '"Play"',
        '"عزف متكرر"': '"Loop"',
        '"مسح"': '"Clear"',
        '"حفظ اللحن في مكتبة الأغاني"': '"Save to Music Library"',
        '"تم حفظ ${item.title}"': '"Saved ${item.title}"',
    }
    changed = False
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
