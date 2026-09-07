package com.example.fx

import kotlin.math.*

class DspPluginManager {
    fun getAvailablePlugins(): List<AudioPlugin> {
        return listOf(
            FilterPlugin(),
            DelayPlugin(),
            ReverbPlugin(),
            FlangerPlugin(),
            PhaserPlugin(),
            BitcrusherPlugin(),
            DistortionPlugin(),
            CompressorPlugin()
        )
    }
}

class FilterPlugin : AudioPlugin {
    override val id = "fx_filter"
    override val name = "Filter"
    override var enabled = false
    override var amount = 0.5f

    private val lpState = FloatArray(2)
    private val hpState = FloatArray(2)

    override fun process(sample: Float, channel: Int): Float {
        val dry = sample
        val center = 0.5f
        var wet = sample

        if (amount < center) {
            // Low pass
            val cutoff = 0.05f + 0.95f * (amount / center)
            lpState[channel] += cutoff * (sample - lpState[channel])
            wet = lpState[channel]
        } else if (amount > center) {
            // High pass
            val cutoff = 0.95f * ((amount - center) / center)
            lpState[channel] += cutoff * (sample - lpState[channel])
            hpState[channel] = sample - lpState[channel]
            wet = hpState[channel]
        }
        
        return dry + (wet - dry) * abs(amount - center) * 2f
    }

    override fun reset() {
        lpState.fill(0f)
        hpState.fill(0f)
    }
}

class DelayPlugin : AudioPlugin {
    override val id = "fx_delay"
    override val name = "Delay"
    override var enabled = false
    override var amount = 0.5f

    private val delayLength = 22050 // ~500ms
    private val buffer = FloatArray(delayLength * 2)
    private var writePos = 0

    override fun process(sample: Float, channel: Int): Float {
        val readPos = (writePos - delayLength + buffer.size) % buffer.size
        val delayed = buffer[readPos / 2 * 2 + channel]
        
        val wet = sample + delayed * 0.5f
        
        // Write to buffer
        val writeIdx = writePos / 2 * 2 + channel
        buffer[writeIdx] = sample + delayed * 0.3f
        
        if (channel == 1) writePos = (writePos + 2) % buffer.size
        
        return sample * (1f - amount) + wet * amount
    }

    override fun reset() {
        buffer.fill(0f)
        writePos = 0
    }
}

class ReverbPlugin : AudioPlugin {
    override val id = "fx_reverb"
    override val name = "Reverb"
    override var enabled = false
    override var amount = 0.5f

    private val delayLength = 8820 // ~200ms
    private val buffer = FloatArray(delayLength * 2)
    private var writePos = 0

    override fun process(sample: Float, channel: Int): Float {
        val readPos = (writePos - delayLength + buffer.size) % buffer.size
        val delayed = buffer[readPos / 2 * 2 + channel]
        
        val wet = sample + delayed * 0.4f
        
        val writeIdx = writePos / 2 * 2 + channel
        buffer[writeIdx] = sample + delayed * 0.6f // high feedback for reverb wash
        
        if (channel == 1) writePos = (writePos + 2) % buffer.size
        
        return sample * (1f - amount) + wet * amount
    }

    override fun reset() {
        buffer.fill(0f)
        writePos = 0
    }
}

class FlangerPlugin : AudioPlugin {
    override val id = "fx_flanger"
    override val name = "Flanger"
    override var enabled = false
    override var amount = 0.5f

    private val maxDelay = 441 // ~10ms
    private val buffer = FloatArray(maxDelay * 2)
    private var writePos = 0
    private var lfoPhase = 0.0

    override fun process(sample: Float, channel: Int): Float {
        lfoPhase += 0.0001
        if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI
        
        val lfo = (sin(lfoPhase) + 1.0) / 2.0
        val currentDelay = (maxDelay * lfo).toInt().coerceIn(1, maxDelay - 1)
        
        val readPos = (writePos - currentDelay * 2 + buffer.size) % buffer.size
        val delayed = buffer[readPos / 2 * 2 + channel]
        
        val wet = sample + delayed * 0.7f
        
        val writeIdx = writePos / 2 * 2 + channel
        buffer[writeIdx] = sample + delayed * 0.5f
        
        if (channel == 1) writePos = (writePos + 2) % buffer.size
        
        return sample * (1f - amount) + wet * amount
    }

    override fun reset() {
        buffer.fill(0f)
        writePos = 0
        lfoPhase = 0.0
    }
}

class PhaserPlugin : AudioPlugin {
    override val id = "fx_phaser"
    override val name = "Phaser"
    override var enabled = false
    override var amount = 0.5f

    private var lfoPhase = 0.0
    private val state1 = FloatArray(2)
    
    override fun process(sample: Float, channel: Int): Float {
        lfoPhase += 0.0002
        if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI
        
        val lfo = (sin(lfoPhase) + 1.0) / 2.0
        val apf = 0.1f + 0.8f * lfo.toFloat()
        
        val wet = apf * (sample - state1[channel]) + state1[channel]
        state1[channel] = sample
        
        return sample * (1f - amount) + wet * amount
    }

    override fun reset() {
        lfoPhase = 0.0
        state1.fill(0f)
    }
}

class BitcrusherPlugin : AudioPlugin {
    override val id = "fx_bitcrush"
    override val name = "Bitcrusher"
    override var enabled = false
    override var amount = 0.5f

    private var counter = 0
    private val heldSample = FloatArray(2)

    override fun process(sample: Float, channel: Int): Float {
        val decimation = (amount * 20).toInt() + 1
        
        if (channel == 0) {
            counter++
        }
        
        if (counter >= decimation) {
            if (channel == 1) counter = 0
            
            // Bit depth reduction
            val bits = 16 - (amount * 12).toInt()
            val steps = 1.shl(bits)
            heldSample[channel] = round(sample * steps) / steps
        }
        
        return sample * (1f - amount) + heldSample[channel] * amount
    }

    override fun reset() {
        counter = 0
        heldSample.fill(0f)
    }
}

class DistortionPlugin : AudioPlugin {
    override val id = "fx_distortion"
    override val name = "Distortion"
    override var enabled = false
    override var amount = 0.5f

    override fun process(sample: Float, channel: Int): Float {
        val drive = 1f + amount * 10f
        val wet = (sample * drive).coerceIn(-1f, 1f)
        
        // Soft clipping
        val out = if (wet > 0) {
            1f - exp(-wet)
        } else {
            -1f + exp(wet)
        }
        
        return sample * (1f - amount) + out * amount
    }

    override fun reset() {}
}

class CompressorPlugin : AudioPlugin {
    override val id = "fx_compressor"
    override val name = "Compressor"
    override var enabled = false
    override var amount = 0.5f

    private val threshold = 0.2f
    private val ratio = 4f

    override fun process(sample: Float, channel: Int): Float {
        val magnitude = abs(sample)
        if (magnitude <= threshold) return sample
        
        val excess = magnitude - threshold
        val compressed = threshold + (excess / ratio)
        
        val wet = if (sample < 0f) -compressed else compressed
        return sample * (1f - amount) + wet * amount
    }

    override fun reset() {}
}
