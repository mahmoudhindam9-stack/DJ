from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DSP = ROOT / 'app/src/main/java/com/example/player/DeckFxAudioProcessor.kt'
MARKER = '// EQUALIZER_FUNCTIONALITY_V1'

text = DSP.read_text(encoding='utf-8')
if MARKER not in text:
    old = '''        if (activeEffects.isEmpty()) {\n            val output = replaceOutputBuffer(bytes)\n            output.put(inputBuffer)\n            output.flip()\n            return\n        }'''
    new = '''        // EQUALIZER_FUNCTIONALITY_V1\n        // EQ is itself an audio effect. Never bypass the PCM processing path\n        // merely because no DJ FX button is active. Otherwise the equalizer\n        // has no audible effect during normal playback.\n        if (activeEffects.isEmpty() && !eqEnabled) {\n            val output = replaceOutputBuffer(bytes)\n            output.put(inputBuffer)\n            output.flip()\n            return\n        }'''
    if text.count(old) != 1:
        raise SystemExit(f'Expected EQ bypass block once, found {text.count(old)}')
    DSP.write_text(text.replace(old, new, 1), encoding='utf-8')
else:
    print('Equalizer DSP already fixed.')

SCREEN = ROOT / 'app/src/main/java/com/example/onlinemusic/OnlineMusicScreen.kt'
screen = SCREEN.read_text(encoding='utf-8')
replacements = {
    'Text(it, style = MaterialTheme.typography.bodySmall, Modifier.padding(16.dp))': 'Text(text = it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))',
    'Text(it, style = MaterialTheme.typography.bodySmall, Modifier.padding(10.dp))': 'Text(text = it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))',
    'Text(link.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)': 'Text(text = link.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)'
}
changed = False
for old, new in replacements.items():
    if old in screen:
        screen = screen.replace(old, new)
        changed = True
if changed:
    SCREEN.write_text(screen, encoding='utf-8')
    print('Online Music Kotlin compile errors fixed.')
else:
    print('Online Music compile fixes already applied.')
