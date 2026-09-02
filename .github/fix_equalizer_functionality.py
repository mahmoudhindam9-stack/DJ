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
    print('Equalizer DSP bypass fixed.')
else:
    print('Equalizer DSP already fixed.')

# Kotlin compiler compatibility fix for the Online Music card rows.
# Keep the same UI/behavior, but use explicit named arguments so Compose
# resolves Text() deterministically with the current Material3/Kotlin setup.
SCREEN = ROOT / 'app/src/main/java/com/example/onlinemusic/OnlineMusicScreen.kt'
screen = SCREEN.read_text(encoding='utf-8')
old_text = 'Text(link.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)'
new_text = 'Text(text = link.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)'
if old_text in screen:
    SCREEN.write_text(screen.replace(old_text, new_text), encoding='utf-8')
    print('Online Music Text() compile regression fixed.')
else:
    print('Online Music Text() compile fix already applied.')
