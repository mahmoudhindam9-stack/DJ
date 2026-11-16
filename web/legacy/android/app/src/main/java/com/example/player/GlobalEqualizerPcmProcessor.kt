package com.example.player

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tanh

/** Same app-wide EQ for direct AudioTrack PCM paths that do not pass through Media3. */
class GlobalEqualizerPcmProcessor {
    companion object {
        private val FREQUENCIES = floatArrayOf(
            60f, 170f, 310f, 600f, 1000f,
            3000f, 6000f, 12000f, 14000f, 16000f
        )
        private const val Q = 1f
        private const val TRANSITION_FRAMES = 256
        private const val LIMITER_THRESHOLD = 0.82f
    }

    private fun createBank() = Array(2) { Array(10) { BiquadFilter() } }
    private var active = createBank()
    private var target = createBank()
    private var appliedVersion = -1L
    private var transitionActive = false
    private var transitionPosition = TRANSITION_FRAMES
    private var configuredSampleRate = 44100
    private var configuredChannels = 2

    private fun configureBank(bank: Array<Array<BiquadFilter>>, levels: FloatArray) {
        for (ch in 0 until configuredChannels) {
            for (i in 0 until 10) {
                bank[ch][i].setPeakingEQ(
                    FREQUENCIES[i],
                    levels.getOrElse(i) { 0f },
                    Q,
                    configuredSampleRate.toFloat()
                )
                bank[ch][i].resetState()
            }
        }
    }

    private fun sync(sampleRate: Int, channels: Int) {
        configuredSampleRate = sampleRate.coerceAtLeast(1)
        configuredChannels = channels.coerceIn(1, 2)
        val version = GlobalEqualizerState.version
        if (version == appliedVersion) return
        configureBank(target, GlobalEqualizerState.levelsDb)
        transitionPosition = 0
        transitionActive = true
        appliedVersion = version
    }

    private fun applyEq(sample: Float, channel: Int, amount: Float): Float {
        var current = sample
        var next = sample
        for (i in 0 until 10) {
            current = active[channel][i].process(current)
            next = target[channel][i].process(next)
        }
        return current * (1f - amount) + next * amount
    }

    private fun softLimit(sample: Float): Float {
        val magnitude = abs(sample)
        if (magnitude <= LIMITER_THRESHOLD) return sample
        val excess = (magnitude - LIMITER_THRESHOLD) / (1f - LIMITER_THRESHOLD)
        val compressed = LIMITER_THRESHOLD + (1f - LIMITER_THRESHOLD) * tanh(excess)
        return if (sample < 0f) -compressed else compressed
    }

    fun process(buffer: ShortArray, length: Int, sampleRate: Int, channels: Int) {
        if (!GlobalEqualizerState.enabled || length <= 0) return
        sync(sampleRate, channels)

        val gain = Math.pow(10.0, GlobalEqualizerState.preampDb.toDouble() / 20.0).toFloat()
        val frames = length / configuredChannels
        for (frame in 0 until frames) {
            val amount = if (transitionActive) {
                ((transitionPosition + 1).toFloat() / TRANSITION_FRAMES.toFloat()).coerceIn(0f, 1f)
            } else 1f
            for (ch in 0 until configuredChannels) {
                val index = frame * configuredChannels + ch
                var sample = buffer[index].toFloat() / 32768f
                sample = applyEq(sample, ch, amount)
                sample = softLimit(sample * gain).coerceIn(-1f, 1f)
                buffer[index] = (sample * 32767f).roundToInt().toShort()
            }
            if (transitionActive) {
                transitionPosition++
                if (transitionPosition >= TRANSITION_FRAMES) {
                    val old = active
                    active = target
                    target = old
                    transitionActive = false
                    transitionPosition = TRANSITION_FRAMES
                }
            }
        }
    }

    fun reset() {
        appliedVersion = -1L
        transitionActive = false
        transitionPosition = TRANSITION_FRAMES
        for (bank in arrayOf(active, target)) {
            for (ch in 0 until configuredChannels) {
                for (i in 0 until 10) bank[ch][i].resetState()
            }
        }
    }
}
