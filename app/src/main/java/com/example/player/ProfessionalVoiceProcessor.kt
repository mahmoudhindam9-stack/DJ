package com.example.player

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Real-time vocal-character DSP used exclusively by the Mic page.
 *
 * It combines pitch shifting with three vocal-tract resonance bands,
 * controlled air/bass shaping, gentle compression and character-specific
 * saturation. State is preallocated so the processor can run in the
 * live microphone loop without per-sample allocations.
 */
class ProfessionalVoiceProcessor(private val sampleRate: Int = 44_100) {

    private class BandPass {
        private var b0 = 0f
        private var b1 = 0f
        private var b2 = 0f
        private var a1 = 0f
        private var a2 = 0f
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        fun configure(centerHz: Float, q: Float, sampleRate: Int) {
            val frequency = centerHz.coerceIn(40f, sampleRate * 0.42f)
            val safeQ = q.coerceIn(0.35f, 12f)
            val w0 = (2.0 * PI * frequency / sampleRate).coerceIn(0.001, PI * 0.92)
            val alpha = sin(w0) / (2.0 * safeQ)
            val cosW0 = cos(w0)
            val a0 = 1.0 + alpha
            b0 = (alpha / a0).toFloat()
            b1 = 0f
            b2 = (-alpha / a0).toFloat()
            a1 = (-2.0 * cosW0 / a0).toFloat()
            a2 = ((1.0 - alpha) / a0).toFloat()
        }

        fun process(input: Float): Float {
            val out = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = input
            y2 = y1
            y1 = out
            return out
        }

        fun reset() {
            x1 = 0f
            x2 = 0f
            y1 = 0f
            y2 = 0f
        }
    }

    private data class CharacterTuning(
        val pitch: Float,
        val formant1: Float,
        val formant2: Float,
        val formant3: Float,
        val resonanceMix: Float,
        val brightness: Float,
        val bassMix: Float,
        val saturation: Float,
        val compressor: Float,
        val airCutHz: Float,
        val ringModMix: Float = 0f,
        val ringModHz: Float = 0f
    )

    private val pitchShifter = PitchShifter(grainSize = 1024)
    private val formant1 = BandPass()
    private val formant2 = BandPass()
    private val formant3 = BandPass()
    private var lowState = 0f
    private var airLowState = 0f
    private var envelope = 0f
    private var ringPhase = 0.0
    private var lastEffect = MicVoiceEffect.NONE

    private val tunings = mapOf(
        MicVoiceEffect.WOMAN to CharacterTuning(
            pitch = 1.20f, formant1 = 900f, formant2 = 1_850f, formant3 = 3_050f,
            resonanceMix = 0.30f, brightness = 0.16f, bassMix = 0.00f,
            saturation = 0.03f, compressor = 0.30f, airCutHz = 8_800f
        ),
        MicVoiceEffect.KID to CharacterTuning(
            pitch = 1.43f, formant1 = 1_020f, formant2 = 2_150f, formant3 = 3_550f,
            resonanceMix = 0.36f, brightness = 0.23f, bassMix = 0.00f,
            saturation = 0.025f, compressor = 0.24f, airCutHz = 9_400f
        ),
        MicVoiceEffect.CHIPMUNK to CharacterTuning(
            pitch = 1.72f, formant1 = 1_260f, formant2 = 2_550f, formant3 = 4_150f,
            resonanceMix = 0.24f, brightness = 0.30f, bassMix = 0.00f,
            saturation = 0.015f, compressor = 0.16f, airCutHz = 10_200f
        ),
        MicVoiceEffect.MONSTER to CharacterTuning(
            pitch = 0.69f, formant1 = 330f, formant2 = 860f, formant3 = 1_700f,
            resonanceMix = 0.52f, brightness = -0.14f, bassMix = 0.24f,
            saturation = 0.20f, compressor = 0.44f, airCutHz = 5_200f
        ),
        MicVoiceEffect.DARK_DEMON to CharacterTuning(
            pitch = 0.58f, formant1 = 280f, formant2 = 730f, formant3 = 1_450f,
            resonanceMix = 0.62f, brightness = -0.20f, bassMix = 0.30f,
            saturation = 0.34f, compressor = 0.54f, airCutHz = 4_600f,
            ringModMix = 0.10f, ringModHz = 34f
        ),
        MicVoiceEffect.GIANT_BASS to CharacterTuning(
            pitch = 0.63f, formant1 = 250f, formant2 = 620f, formant3 = 1_250f,
            resonanceMix = 0.58f, brightness = -0.24f, bassMix = 0.36f,
            saturation = 0.12f, compressor = 0.48f, airCutHz = 4_200f
        )
    )

    init { configure(MicVoiceEffect.NONE) }

    fun setEffect(effect: MicVoiceEffect) {
        if (effect == lastEffect) return
        configure(effect)
        lastEffect = effect
    }

    private fun configure(effect: MicVoiceEffect) {
        pitchShifter.reset()
        formant1.reset()
        formant2.reset()
        formant3.reset()
        lowState = 0f
        airLowState = 0f
        envelope = 0f
        ringPhase = 0.0

        val tuning = tunings[effect] ?: return
        formant1.configure(tuning.formant1, 3.0f, sampleRate)
        formant2.configure(tuning.formant2, 4.0f, sampleRate)
        formant3.configure(tuning.formant3, 4.2f, sampleRate)
    }

    fun process(input: Float): Float {
        val tuning = tunings[lastEffect] ?: return input
        var shifted = pitchShifter.process(input, tuning.pitch)

        val lowAlpha = exp(
            (-2.0 * PI * 190.0 / sampleRate.coerceAtLeast(1)).coerceIn(-50.0, 0.0)
        ).toFloat()
        lowState = lowAlpha * lowState + (1f - lowAlpha) * shifted
        val low = lowState

        val airAlpha = exp(
            (-2.0 * PI * tuning.airCutHz / sampleRate.coerceAtLeast(1)).coerceIn(-50.0, 0.0)
        ).toFloat()
        airLowState = airAlpha * airLowState + (1f - airAlpha) * shifted
        val air = shifted - airLowState

        val vocalResonance =
            formant1.process(shifted) * 0.48f +
            formant2.process(shifted) * 0.34f +
            formant3.process(shifted) * 0.22f

        shifted += vocalResonance * tuning.resonanceMix
        shifted += air * tuning.brightness
        shifted += low * tuning.bassMix

        if (tuning.ringModMix > 0f) {
            val mod = 0.5f + 0.5f * sin(ringPhase).toFloat()
            ringPhase += 2.0 * PI * tuning.ringModHz / sampleRate
            if (ringPhase >= 2.0 * PI) ringPhase -= 2.0 * PI
            shifted = shifted * (1f - tuning.ringModMix) +
                (shifted * (0.55f + 0.45f * mod)) * tuning.ringModMix
        }

        val peak = abs(shifted)
        val coeff = if (peak > envelope) 0.18f else 0.018f
        envelope += (peak - envelope) * coeff

        val target = 0.42f
        val ratio = 4.0f
        if (envelope > target) {
            val gain = (target + (envelope - target) / ratio) / max(envelope, 1e-4f)
            shifted *= 1f - tuning.compressor + tuning.compressor * gain
        }

        if (tuning.saturation > 0f) {
            val drive = 1f + tuning.saturation * 7f
            val norm = tanh(drive.toDouble()).toFloat().coerceAtLeast(0.05f)
            shifted = tanh((shifted * drive).toDouble()).toFloat() / norm
        }

        return (input * 0.08f + shifted * 0.92f).coerceIn(-0.98f, 0.98f)
    }

    fun reset() { configure(lastEffect) }
}
