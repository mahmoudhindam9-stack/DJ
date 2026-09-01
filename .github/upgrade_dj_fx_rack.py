from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main/java/com/example/MainActivity.kt'
MARKER = '// DJ_FX_RACK_V1'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'{label}: source block not found')
    return text.replace(old, new, 1)


def main() -> None:
    text = MAIN.read_text(encoding='utf-8')
    if MARKER in text:
        print('DJ FX rack already normalized')
        return

    old = '''                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {\n                DJPadButton("Flanger", deck.isFlangerActive) { deck.toggleFlanger() }\n                DJPadButton("Reverb", deck.isReverbActive) { deck.toggleReverb() }\n            }\n            Spacer(modifier = Modifier.height(4.dp))\n            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {\n                DJPadButton("Echo", deck.isEchoActive) { deck.toggleEcho() }\n                DJPadButton("Crush", deck.isCrushActive) { deck.toggleCrush() }\n            }\n        }\n    }\n'''
    # The source currently uses the same visual structure but may have indentation normalized;
    # use the exact block found in the current DJDeckItem implementation.
    if old not in text:
        old = '''            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {\n                DJPadButton("Flanger", deck.isFlangerActive) { deck.toggleFlanger() }\n                DJPadButton("Reverb", deck.isReverbActive) { deck.toggleReverb() }\n            }\n            Spacer(modifier = Modifier.height(4.dp))\n            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {\n                DJPadButton("Echo", deck.isEchoActive) { deck.toggleEcho() }\n                DJPadButton("Crush", deck.isCrushActive) { deck.toggleCrush() }\n            }\n        }\n    }\n'''

    new = old.replace('''            }\n        }\n    }\n''', '''            }\n\n            // DJ_FX_RACK_V1\n            DJFxRack(deck)\n        }\n    }\n''', 1)
    text = replace_once(text, old, new, 'DJ FX rack mount point')
    MAIN.write_text(text, encoding='utf-8')
    print('DJ FX rack integrated into existing DJ deck')


if __name__ == '__main__':
    main()
