from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
DECK = ROOT / "app/src/main/java/com/example/player/DJDeckController.kt"

EFFECT_ENUM = '''enum class DJEffect(val displayName: String) {
    FILTER("Filter"), FILTER_ROLL("Filter Roll"), NOISE("Noise"), FLANGER("Flanger"),
    REVERB("Reverb"), ECHO("Echo"), DELAY("Delay"), PHASER("Phaser"), TREMOLO("Tremolo"),
    CHOPPA("Choppa"), MUTE("Mute"), FADER_TONE("Fader Tone"), ROLL("Roll"), STUTTER("Stutter"),
    GATE("Gate"), BITCRUSH("Bit Crush"), TELEPHONE("Telephone"), VINYL("Vinyl"), ROBOT("Robot"),
    RING_MOD("Ring Mod"), AUTO_PAN("Auto Pan"), LOW_PASS("Low Pass"), HIGH_PASS("High Pass"),
    SPACE("Space"), PITCH_ECHO("Pitch Echo"), TAPE_STOP("Tape Stop"), TRANSFORM("Transform"),
    SLICE("Slice"), BEAT_REPEAT("Beat Repeat")
}'''

text = DECK.read_text(encoding="utf-8")
start = text.index("enum class DJEffect")
end = text.index("\n\nenum class SamplerSound", start)
text = text[:start] + EFFECT_ENUM + text[end:]

# Normalize the DJDeck fields without creating duplicates.
text = text.replace(
    "class DJDeck(context: Context, val deckName: String) {\n    private val fxProcessor = DeckFxAudioProcessor()",
    "class DJDeck(context: Context, val deckName: String) {\n    val fxProcessor = DeckFxAudioProcessor()\n    val effectStates = mutableStateMapOf<DJEffect, Boolean>()",
    1,
)
if "val fxProcessor = DeckFxAudioProcessor()\n    val effectStates = mutableStateMapOf<DJEffect, Boolean>()" not in text:
    raise SystemExit("DJDeck control fields could not be normalized")

# Strip all copies previously injected by this script, then insert one canonical block.
method_block = re.compile(
    r'\n    fun toggleEffect\(effect: DJEffect\) \{.*?\n    fun setEffectBeatDivision\(value: Float\) \{.*?\n    \}\n',
    re.DOTALL,
)
text = method_block.sub("\n", text)

needle = '''    fun toggleCrush() {
        isCrushActive = !isCrushActive
        fxProcessor.crushEnabled = isCrushActive
    }
'''
insert = needle + '''
    fun toggleEffect(effect: DJEffect) {
        val next = !(effectStates[effect] ?: false)
        effectStates[effect] = next
        fxProcessor.setEffect(DeckFxAudioProcessor.Effect.valueOf(effect.name), next)
    }

    fun isEffectActive(effect: DJEffect): Boolean = effectStates[effect] ?: false

    fun setEffectAmount(value: Float) {
        fxProcessor.amount = value.coerceIn(0f, 1f)
    }

    fun setEffectBeatDivision(value: Float) {
        fxProcessor.beatDivision = value.coerceIn(0.0625f, 1f)
    }
'''
if needle not in text:
    raise SystemExit("DJDeck effect toggle anchor not found")
text = text.replace(needle, insert, 1)

DECK.write_text(text, encoding="utf-8")
print("29-effect DJ deck control layer normalized")
