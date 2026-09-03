from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
SAMPLER = ROOT / "app/src/main/java/com/example/ProfessionalSampler.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"{label}: expected source block not found")
    return text.replace(old, new, 1)


def patch_main() -> None:
    s = MAIN.read_text(encoding="utf-8")

    # Crossfader: UI-only compact horizontal presentation, preserving the existing
    # live binding to DJMixerController.updateCrossfader().
    old = '''        // Crossfader Control Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("DECK A (${((1f - djMixerController.crossfader) * 100).toInt()}%)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("CROSSFADER", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("DECK B (${(djMixerController.crossfader * 100).toInt()}%)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                Slider(
                    value = djMixerController.crossfader,
                    onValueChange = { djMixerController.updateCrossfader(it) },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Maqamat & Oriental Taqasim Section
        MaqamatSection(djMixerController.maqamPlayer)

        Spacer(modifier = Modifier.height(16.dp))

        // DJ_SAMPLER_CC0_V1
        // The former generated/legacy sound buttons are removed completely.
        // This is the only sampler surface shown in the DJ page.
        ProfessionalSamplerBoard()
'''
    new = '''        // DJ_UI_POLISH_V2: Crossfader directly below Deck A/B, horizontal bar.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("A", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Slider(
                        value = djMixerController.crossfader,
                        onValueChange = { djMixerController.updateCrossfader(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f)
                    )
                    Text("B", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("DECK A ${((1f - djMixerController.crossfader) * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("CROSSFADER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("DECK B ${(djMixerController.crossfader * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // DJ_UI_SONG_COUNTER_V2: compact horizontal counter with existing library information.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(19.dp))
                    Text("SONG COUNTER", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Text("${audioLibrary.size} SONGS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // DJ_UI_SAMPLER_ORDER_V2: professional real-sample board above Maqamat.
        ProfessionalSamplerBoard()

        Spacer(modifier = Modifier.height(10.dp))

        // Maqamat & Oriental Taqasim Section
        MaqamatSection(djMixerController.maqamPlayer)
'''
    s = replace_once(s, old, new, "MainActivity DJ layout")
    MAIN.write_text(s, encoding="utf-8")


def patch_sampler() -> None:
    s = SAMPLER.read_text(encoding="utf-8")
    marker = '''    SampleBank("Soulful Vintage", listOf(
'''
    if marker not in s:
        raise SystemExit("ProfessionalSampler: Soulful Vintage bank marker not found")

    # Insert a dedicated real-world Eastern bank before the existing final bank.
    bank = '''    SampleBank("Oriental Real", listOf(
        SamplePad("Tabla • Real", "https://raw.githubusercontent.com/sonic-pi-net/sonic-pi/dev/etc/samples/tabla_tas1.flac"),
        SamplePad("Tabla • Bol", "https://raw.githubusercontent.com/sonic-pi-net/sonic-pi/dev/etc/samples/tabla_ghe1.flac"),
        SamplePad("Darbuka • Doom", "https://raw.githubusercontent.com/freepats/world-percussion/main/samples/Darbuka/doom_01_01.flac"),
        SamplePad("Darbuka • Tak", "https://raw.githubusercontent.com/freepats/world-percussion/main/samples/Darbuka/tak_02_01.flac"),
        SamplePad("Riq • Egyptian", "https://upload.wikimedia.org/wikipedia/commons/d/db/Riq_demo.ogg"),
        SamplePad("Riq • Frame", "https://raw.githubusercontent.com/freepats/world-percussion/main/samples/Tambourine/fast_03.wav"),
        SamplePad("Sagat • Finger Cymbal", "https://bigsoundbank.com/UPLOAD/mp3/2274.mp3"),
        SamplePad("Sagat • Cymbal", "https://bigsoundbank.com/UPLOAD/mp3/2274.mp3"),
        SamplePad("Laugh • Real", "https://raw.githubusercontent.com/code4fukui/sound-cc0/main/wahaha.wav"),
        SamplePad("Laugh • Crowd", "https://raw.githubusercontent.com/code4fukui/sound-cc0/main/wahaha.wav"),
        SamplePad("Zaghareet • Oriental", "https://www.tosound.com/sound/sound-5dJWRoOr/translate-false"),
        SamplePad("Zaghareet • Ululation", "https://www.tosound.com/sound/sound-5dJWRoOr/translate-false")
    )),
'''
    if 'SampleBank("Oriental Real"' not in s:
        s = s.replace(marker, bank + marker, 1)

    s = s.replace("Real WAV one-shots • CC0 • 3 banks × 16 pads", "Real audio one-shots • CC0/open sources • 4 banks", 1)
    s = s.replace("Source: Boochi44/free-drum-samples — CC0 1.0", "Sources: CC0/open real recordings (FreePats, Sonic Pi, sound-cc0, BigSoundBank) + Egyptian Riq recording", 1)
    SAMPLER.write_text(s, encoding="utf-8")


if __name__ == "__main__":
    patch_main()
    patch_sampler()
    print("DJ UI polish + Oriental real-sample bank applied")
