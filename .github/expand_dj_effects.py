from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DECK = ROOT / "app/src/main/java/com/example/player/DJDeckController.kt"

EFFECT_ENUM = '''enum class DJEffect(val displayName: String) {
    FILTER("Filter"),
    FILTER_ROLL("Filter Roll"),
    NOISE("Noise"),
    FLANGER("Flanger"),
    REVERB("Reverb"),
    ECHO("Echo"),
    DELAY("Delay"),
    PHASER("Phaser"),
    TREMOLO("Tremolo"),
    CHOPPA("Choppa"),
    MUTE("Mute"),
    FADER_TONE("Fader Tone"),
    ROLL("Roll"),
    STUTTER("Stutter"),
    GATE("Gate"),
    BITCRUSH("Bit Crush"),
    TELEPHONE("Telephone"),
    VINYL("Vinyl"),
    ROBOT("Robot"),
    RING_MOD("Ring Mod"),
    AUTO_PAN("Auto Pan"),
    LOW_PASS("Low Pass"),
    HIGH_PASS("High Pass"),
    SPACE("Space"),
    PITCH_ECHO("Pitch Echo"),
    TAPE_STOP("Tape Stop"),
    TRANSFORM("Transform"),
    SLICE("Slice"),
    BEAT_REPEAT("Beat Repeat")
}'''

text = DECK.read_text(encoding="utf-8")
start = text.index("enum class DJEffect")
end = text.index("\n\nenum class SamplerSound", start)
text = text[:start] + EFFECT_ENUM + text[end:]

old = '''class DJDeck(context: Context, val deckName: String) {
    private val fxProcessor = DeckFxAudioProcessor()'''
new = '''class DJDeck(context: Context, val deckName: String) {
    val fxProcessor = DeckFxAudioProcessor()
    val effectStates = mutableStateMapOf<DJEffect, Boolean>()
'''
text = text.replace(old, new, 1)

needle = '''    fun toggleCrush() {
        isCrushActive = !isCrushActive
        fxProcessor.crushEnabled = isCrushActive
    }
'''
insert = needle + '''
    fun toggleEffect(effect: DJEffect) {
        val next = !(effectStates[effect] ?: false)
        effectStates[effect] = next
        val processorEffect = DeckFxAudioProcessor.Effect.valueOf(effect.name)
        fxProcessor.setEffect(processorEffect, next)
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
print("29-effect DJ deck control layer applied")
