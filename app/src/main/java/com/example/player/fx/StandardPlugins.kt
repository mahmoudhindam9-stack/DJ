package com.example.player.fx

import kotlin.math.*

abstract class BasePlugin(
    override val id: String,
    override val name: String,
    override val version: String = "1.0.0"
) : AudioPlugin {
    override var enabled: Boolean = false
    override var amount: Float = 0f
    protected var sampleRate: Int = 44100
    protected var channels: Int = 2

    override fun init(sampleRate: Int, channels: Int) {
        this.sampleRate = sampleRate
        this.channels = channels
    }
}

class FilterPlugin : BasePlugin("fx_filter", "Filter") {
    private var lpState = FloatArray(2)
    private var hpState = FloatArray(2)

    override fun process(input: Float, channel: Int): Float {
        if (!enabled || amount == 0.5f) return input
        // amount < 0.5 = Low Pass, amount > 0.5 = High Pass
        if (amount < 0.5f) {
            val cut = (amount * 2f).coerceIn(0.01f, 1.0f)
            val alpha = cut * cut
            lpState[channel] += alpha * (input - lpState[channel])
            return lpState[channel]
        } else {
            val cut = ((amount - 0.5f) * 2f).coerceIn(0.0f, 0.99f)
            val alpha = 1f - (cut * cut)
            val output = alpha * (hpState[channel] + input)
            hpState[channel] = output - input
            return output
        }
    }

    override fun reset() {
        lpState.fill(0f)
        hpState.fill(0f)
    }
}

class DelayPlugin : BasePlugin("fx_delay", "Delay/Echo") {
    private val maxDelayFrames = 88200
    private var delayLine = FloatArray(maxDelayFrames * 2)
    private var writePos = 0

    override fun process(input: Float, channel: Int): Float {
        if (!enabled || amount <= 0f) return input
        val delaySec = 0.35f
        val delayFrames = (sampleRate * delaySec).toInt().coerceIn(1, maxDelayFrames - 1)
        var readPos = writePos - delayFrames
        if (readPos < 0) readPos += maxDelayFrames
        val delayed = delayLine[readPos * channels + channel]
        
        val wet = input + delayed * 0.45f * amount
        delayLine[writePos * channels + channel] = input + delayed * 0.3f
        
        if (channel == channels - 1) {
            writePos = (writePos + 1) % maxDelayFrames
        }
        return (input * (1f - amount * 0.5f)) + (wet * amount * 0.5f)
    }

    override fun reset() {
        delayLine.fill(0f)
        writePos = 0
    }
}

class ReverbPlugin : BasePlugin("fx_reverb", "Reverb") {
    private val maxDelayFrames = 44100
    private var delayLine = FloatArray(maxDelayFrames * 2)
    private var writePos = 0

    private fun readDelay(frames: Int, ch: Int): Float {
        var readPos = writePos - frames
        if (readPos < 0) readPos += maxDelayFrames
        return delayLine[readPos * channels + ch]
    }

    override fun process(input: Float, channel: Int): Float {
        if (!enabled || amount <= 0f) return input
        
        val r1 = readDelay((sampleRate * 0.032).toInt(), channel)
        val r2 = readDelay((sampleRate * 0.065).toInt(), channel)
        val r3 = readDelay((sampleRate * 0.095).toInt(), channel)
        
        val wet = (r1 * 0.35f + r2 * 0.25f + r3 * 0.20f)
        
        delayLine[writePos * channels + channel] = input + wet * 0.1f
        if (channel == channels - 1) {
            writePos = (writePos + 1) % maxDelayFrames
        }
        
        return input * (1f - amount * 0.3f) + wet * amount
    }

    override fun reset() {
        delayLine.fill(0f)
        writePos = 0
    }
}

class FlangerPlugin : BasePlugin("fx_flanger", "Flanger") {
    private val maxDelayFrames = 4410
    private var delayLine = FloatArray(maxDelayFrames * 2)
    private var writePos = 0
    private var lfoPhase = 0.0

    override fun process(input: Float, channel: Int): Float {
        if (!enabled || amount <= 0f) return input
        
        val lfo = (sin(2.0 * Math.PI * lfoPhase) + 1.0) / 2.0 // 0 to 1
        val delaySamples = (sampleRate * (0.001 + 0.004 * lfo)).toInt().coerceIn(1, maxDelayFrames - 1)
        
        var readPos = writePos - delaySamples
        if (readPos < 0) readPos += maxDelayFrames
        val delayed = delayLine[readPos * channels + channel]
        
        val out = input * (1f - 0.4f * amount) + delayed * 0.7f * amount
        delayLine[writePos * channels + channel] = input + delayed * 0.3f
        
        if (channel == channels - 1) {
            writePos = (writePos + 1) % maxDelayFrames
            lfoPhase += 0.25 / sampleRate
            if (lfoPhase >= 1.0) lfoPhase -= 1.0
        }
        return out
    }

    override fun reset() {
        delayLine.fill(0f)
        writePos = 0
        lfoPhase = 0.0
    }
}

class PhaserPlugin : BasePlugin("fx_phaser", "Phaser") {
    private val maxDelayFrames = 4410
    private var delayLine = FloatArray(maxDelayFrames * 2)
    private var writePos = 0
    private var lfoPhase = 0.0

    override fun process(input: Float, channel: Int): Float {
        if (!enabled || amount <= 0f) return input
        
        val lfo = (sin(2.0 * Math.PI * lfoPhase) + 1.0) / 2.0
        val phaserDelay = (sampleRate * (0.0015 + 0.0025 * lfo)).toInt().coerceIn(1, maxDelayFrames - 1)
        
        var readPos = writePos - phaserDelay
        if (readPos < 0) readPos += maxDelayFrames
        val delayed = delayLine[readPos * channels + channel]
        
        delayLine[writePos * channels + channel] = input
        
        if (channel == channels - 1) {
            writePos = (writePos + 1) % maxDelayFrames
            lfoPhase += 0.5 / sampleRate
            if (lfoPhase >= 1.0) lfoPhase -= 1.0
        }
        return input * (1f - amount * 0.5f) + delayed * 0.6f * amount
    }

    override fun reset() {
        delayLine.fill(0f)
        writePos = 0
        lfoPhase = 0.0
    }
}

class BitCrusherPlugin : BasePlugin("fx_bitcrush", "Bit Crusher") {
    private var crushCounter = 0
    private var crushHeldSample = FloatArray(2)

    override fun process(input: Float, channel: Int): Float {
        if (!enabled || amount <= 0f) return input
        val decimate = max(1, (1 + (12 * amount)).toInt())
        if (crushCounter % decimate == 0) {
            val steps = max(4f, 48f - 40f * amount)
            crushHeldSample[channel] = Math.round(input * steps) / steps
        }
        if (channel == channels - 1) {
            crushCounter++
        }
        return crushHeldSample[channel]
    }

    override fun reset() {
        crushCounter = 0
        crushHeldSample.fill(0f)
    }
}

class DistortionPlugin : BasePlugin("fx_distortion", "Distortion") {
    override fun process(input: Float, channel: Int): Float {
        if (!enabled || amount <= 0f) return input
        val drive = 1f + amount * 10f
        val distorted = tanh(input * drive)
        // Gain compensate
        val compensate = 1f / tanh(drive)
        return input * (1f - amount) + (distorted * compensate) * amount
    }
    override fun reset() {}
}

class CompressorPlugin : BasePlugin("fx_compressor", "Compressor") {
    private var envelope = 0f
    
    override fun process(input: Float, channel: Int): Float {
        if (!enabled || amount <= 0f) return input
        val threshold = 1f - (amount * 0.8f) // 1.0 to 0.2
        val ratio = 1f + (amount * 4f) // 1:1 to 5:1
        val attack = 0.01f
        val release = 0.002f
        
        val absIn = abs(input)
        envelope = if (absIn > envelope) {
            envelope + attack * (absIn - envelope)
        } else {
            envelope + release * (absIn - envelope)
        }
        
        var gain = 1f
        if (envelope > threshold) {
            val overshoot = envelope - threshold
            val compressed = overshoot / ratio
            gain = (threshold + compressed) / envelope
        }
        
        val makeupGain = 1f + (amount * 0.5f)
        return (input * gain * makeupGain).coerceIn(-1f, 1f)
    }
    
    override fun reset() {
        envelope = 0f
    }
}
