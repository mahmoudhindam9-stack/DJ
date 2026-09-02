package com.example.player

import kotlin.math.*

/**
 * Biquad filter implementation for 10-band EQ.
 */
class BiquadFilter {
    private var b0 = 1.0f
    private var b1 = 0.0f
    private var b2 = 0.0f
    private var a1 = 0.0f
    private var a2 = 0.0f
    
    private var z1 = 0.0f
    private var z2 = 0.0f

    fun setPeakingEQ(freq: Float, dbGain: Float, q: Float, sampleRate: Float) {
        val A = 10.0f.pow(dbGain / 40.0f)
        val w0 = 2.0f * PI.toFloat() * freq / sampleRate
        val alpha = sin(w0) / (2.0f * q)
        
        val a0 = 1.0f + alpha / A
        b0 = (1.0f + alpha * A) / a0
        b1 = (-2.0f * cos(w0)) / a0
        b2 = (1.0f - alpha * A) / a0
        a1 = (-2.0f * cos(w0)) / a0
        a2 = (1.0f - alpha / A) / a0
    }

    fun process(sample: Float): Float {
        val out = b0 * sample + z1
        z1 = b1 * sample - a1 * out + z2
        z2 = b2 * sample - a2 * out
        return out
    }
}
