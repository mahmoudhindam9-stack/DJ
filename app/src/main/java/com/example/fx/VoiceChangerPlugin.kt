package com.example.fx

import com.example.player.PitchShifter
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.tanh

/**
 * Real-time voice character processor used by the DJ deck.
 *
 * This class is intentionally instantiated per DeckFxAudioProcessor so its
 * pitch-shifter and filter state belong only to that deck's audio output.
 */
class VoiceChangerPlugin(
    override val id: String,
    override val name: String,
    private val pitchRatio: Float,
    private val brightness: Float,
    private val toneCutoffHz: Float,
    private val drive: Float
) : AudioPlugin {
    override var enabled = false
    override var amount = 0.5f
    override var sampleRate = 44100
    override var channelCount = 2

    private val shifters = Array(2) { PitchShifter(grainSize = 1024) }
    private val toneLow = FloatArray(2)

    private fun lowPass(input: Float, channel: Int, cutoffHz: Float): Float {
        val safeCutoff = cutoffHz.coerceIn(120f, sampleRate * 0.45f)
        val alpha = exp(
            (-2.0 * PI * safeCutoff / sampleRate.coerceAtLeast(1)).coerceIn(-50.0, 0.0)
        ).toFloat()
        val out = alpha * toneLow[channel] + (1f - alpha) * input
        toneLow[channel] = out
        return out
    }

    private fun saturate(value: Float, saturation: Float): Float {
        if (saturation <= 0f) return value
        val driveGain = 1f + saturation * 6f
        return tanh(value * driveGain) / tanh(driveGain)
    }

    override fun process(sample: Float, channel: Int): Float {
        val ch = channel.coerceIn(0, shifters.lastIndex)
        val shifted = shifters[ch].process(sample, pitchRatio)
        val low = lowPass(shifted, ch, toneCutoffHz)
        val high = shifted - low
        val brightened = (shifted + high * brightness).coerceIn(-1.5f, 1.5f)
        val shaped = saturate(brightened, drive)
        val wet = shaped.coerceIn(-1f, 1f)
        return sample * (1f - amount) + wet * amount
    }

    override fun reset() {
        shifters.forEach { it.reset() }
        toneLow.fill(0f)
    }
}
