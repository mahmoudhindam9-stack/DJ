package com.example.player

import kotlin.math.cos

/**
 * Real-time voice pitch shifter.
 *
 * Uses the classic "two overlapping, Hann-windowed taps into a short delay
 * line" technique (granular / SOLA-lite pitch shifting): each tap reads the
 * recent input at a position that drifts closer to or further from "now"
 * every sample, which re-speeds the waveform's content — i.e. changes its
 * pitch — while a second tap, offset by half a grain and crossfaded with a
 * Hann window, hides the moment each tap has to snap back to the start of
 * its grain. Because the two windows sum to a constant (the standard 50%
 * overlap-add property of a Hann window), the output level stays steady.
 *
 * pitchRatio == 1.0 → unchanged. > 1.0 raises pitch (kid, small/young
 * woman, old woman). < 1.0 lowers it (old man, giant, monster).
 *
 * This runs entirely on the live microphone signal, sample by sample, so
 * it adds a small, fixed amount of latency (roughly one grain length) but
 * no external dependency or offline processing is required.
 */
class PitchShifter(private val grainSize: Int = 1024) {
    private val ringSize = grainSize * 4
    private val ring = FloatArray(ringSize)
    private var writePos = 0
    private var delay1 = 0f
    private var delay2 = grainSize / 2f

    private fun readInterpolated(delay: Float): Float {
        val pos = ((writePos - delay) % ringSize + ringSize) % ringSize
        val i0 = pos.toInt()
        val i1 = (i0 + 1) % ringSize
        val frac = pos - i0
        return ring[i0] * (1f - frac) + ring[i1] * frac
    }

    private fun hann(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return (0.5f - 0.5f * cos(2.0 * Math.PI * t)).toFloat()
    }

    /** Feeds one dry input sample in, returns the pitch-shifted sample out. */
    fun process(input: Float, pitchRatio: Float): Float {
        ring[writePos] = input
        writePos = (writePos + 1) % ringSize

        val out1 = readInterpolated(delay1) * hann(delay1 / grainSize)
        val out2 = readInterpolated(delay2) * hann(delay2 / grainSize)

        val step = pitchRatio - 1f
        delay1 -= step
        delay2 -= step
        if (delay1 < 0f) delay1 += grainSize else if (delay1 >= grainSize) delay1 -= grainSize
        if (delay2 < 0f) delay2 += grainSize else if (delay2 >= grainSize) delay2 -= grainSize

        return out1 + out2
    }

    fun reset() {
        ring.fill(0f)
        writePos = 0
        delay1 = 0f
        delay2 = grainSize / 2f
    }
}
