from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main/java/com/example/MainActivity.kt'
MARKER = '// DJ_FX_RACK_V2'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'{label}: source block not found')
    return text.replace(old, new, 1)


def main() -> None:
    text = MAIN.read_text(encoding='utf-8')
    if MARKER in text:
        print('Mixxx-style DJ FX rack already integrated')
        return

    legacy = '''            // Audio FX Pad Toggles
            Text("Deck FX", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DJPadButton("Flanger", deck.isFlangerActive) { deck.toggleFlanger() }
                DJPadButton("Reverb", deck.isReverbActive) { deck.toggleReverb() }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DJPadButton("Echo", deck.isEchoActive) { deck.toggleEcho() }
                DJPadButton("Crush", deck.isCrushActive) { deck.toggleCrush() }
            }
        }
    }
'''

    replacement = '''            // DJ_FX_RACK_V2
            // Replace the four legacy pads with the full Mixxx-style rack so the
            // professional effects are visible directly inside each DJ deck.
            DJFxRack(deck)
        }
    }
'''

    text = replace_once(text, legacy, replacement, 'legacy DJ FX controls')
    MAIN.write_text(text, encoding='utf-8')
    print('Mixxx-style DJ FX rack mounted directly in both DJ decks')


if __name__ == '__main__':
    main()
