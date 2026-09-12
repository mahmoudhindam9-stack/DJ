package com.example.fx

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

/**
 * The DJ app's real-time DSP effects library.
 *
 * [getAvailablePlugins] returns one [AudioPlugin] per entry in the library:
 * the 8 built-in engines below, plus every preset the user has saved from
 * the in-app "Effects Library" screen. Nothing about the library is
 * hard-coded into the UI — adding, editing, or deleting a preset here is
 * immediately reflected the next time the deck rebuilds its plugin chain,
 * so the user can grow their own effects collection without touching code.
 */
class DspPluginManager(private val context: Context) {

    fun getAvailablePlugins(): List<AudioPlugin> {
        val builtIns = listOf(
            FilterPlugin(),
            DelayPlugin(),
            ReverbPlugin(),
            FlangerPlugin(),
            PhaserPlugin(),
            BitcrusherPlugin(),
            DistortionPlugin(),
            CompressorPlugin()
        )
        return builtIns + loadCustomPlugins()
    }

    // ---------------------------------------------------------------------
    // User-authored library (persisted in SharedPreferences as JSON so it
    // survives app restarts and can later be exported/synced if needed).
    // ---------------------------------------------------------------------

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** One row of the user's library: a chosen engine plus up to two knobs. */
    data class CustomPreset(
        val id: String,
        val name: String,
        val engineType: String,
        val param1: Float,
        val param2: Float
    )

    fun getCustomPresets(): List<CustomPreset> {
        val jsonStr = prefs().getString(KEY_PRESETS, "[]") ?: "[]"
        val out = mutableListOf<CustomPreset>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                out.add(
                    CustomPreset(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        engineType = obj.optString("type", "filter"),
                        param1 = obj.optDouble("param1", 0.5).toFloat(),
                        param2 = obj.optDouble("param2", 0.5).toFloat()
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return out
    }

    /** Saves a new preset to the library. Returns its generated id. */
    fun addCustomPreset(name: String, engineType: String, param1: Float, param2: Float): String {
        val id = "custom_${System.currentTimeMillis()}"
        val current = JSONArray(prefs().getString(KEY_PRESETS, "[]") ?: "[]")
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("type", engineType)
        obj.put("param1", param1.toDouble())
        obj.put("param2", param2.toDouble())
        current.put(obj)
        prefs().edit().putString(KEY_PRESETS, current.toString()).apply()
        return id
    }

    fun deleteCustomPreset(id: String) {
        val current = JSONArray(prefs().getString(KEY_PRESETS, "[]") ?: "[]")
        val kept = JSONArray()
        for (i in 0 until current.length()) {
            val obj = current.getJSONObject(i)
            if (obj.optString("id") != id) kept.put(obj)
        }
        prefs().edit().putString(KEY_PRESETS, kept.toString()).apply()
    }

    private fun loadCustomPlugins(): List<AudioPlugin> =
        getCustomPresets().map { preset ->
            buildEngine(preset.engineType, preset.id, preset.name, preset.param1, preset.param2)
        }

    companion object {
        private const val PREFS_NAME = "modular_fx"
        private const val KEY_PRESETS = "plugins"

        /** (engineKey, displayLabel) pairs the "create effect" UI can offer. */
        val ENGINE_TYPES: List<Pair<String, String>> = listOf(
            "filter" to "Filter",
            "delay" to "Delay",
            "reverb" to "Reverb",
            "flanger" to "Flanger",
            "phaser" to "Phaser",
            "bitcrush" to "Bitcrusher",
            "distortion" to "Distortion",
            "compressor" to "Compressor"
        )

        fun buildEngine(type: String, id: String, name: String, param1: Float, param2: Float): AudioPlugin =
            when (type) {
                "delay" -> CustomDelayPlugin(id, name, param1)
                "reverb" -> CustomReverbPlugin(id, name, param1)
                "flanger" -> CustomFlangerPlugin(id, name, param1)
                "phaser" -> CustomPhaserPlugin(id, name, param1)
                "bitcrush" -> CustomBitcrusherPlugin(id, name, param1)
                "distortion" -> CustomDistortionPlugin(id, name, param1)
                "compressor" -> CustomCompressorPlugin(id, name, param1, param2)
                else -> CustomFilterPlugin(id, name, param1)
            }
    }
}

class FilterPlugin : AudioPlugin {
    override val id = "fx_filter"
    override val name = "Filter"
    override var enabled = false
    override var amount = 0.5f
    override var sampleRate = 44100
    private val lpState = FloatArray(2)
    private val hpState = FloatArray(2)
    override fun process(sample: Float, channel: Int): Float {
        val dry = sample
        val center = 0.5f
        var wet = sample
        if (amount < center) {
            val cutoff = 0.05f + 0.95f * (amount / center)
            lpState[channel] += cutoff * (sample - lpState[channel])
            wet = lpState[channel]
        } else if (amount > center) {
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
    override var sampleRate = 44100
    private val maxFrames = 96000
    private val buffer = FloatArray(maxFrames * 2)
    private var writePos = 0
    override fun process(sample: Float, channel: Int): Float {
        val delayLength = (sampleRate * 0.5).toInt().coerceIn(1, maxFrames)
        val readPos = (writePos - delayLength * 2 + buffer.size) % buffer.size
        val delayed = buffer[readPos / 2 * 2 + channel]
        val wet = sample + delayed * 0.5f
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
    override var sampleRate = 44100
    private val maxFrames = 96000
    private val buffer = FloatArray(maxFrames * 2)
    private var writePos = 0
    override fun process(sample: Float, channel: Int): Float {
        val delayLength = (sampleRate * 0.2).toInt().coerceIn(1, maxFrames)
        val readPos = (writePos - delayLength * 2 + buffer.size) % buffer.size
        val delayed = buffer[readPos / 2 * 2 + channel]
        val wet = sample + delayed * 0.4f
        val writeIdx = writePos / 2 * 2 + channel
        buffer[writeIdx] = sample + delayed * 0.6f 
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
    override var sampleRate = 44100
    private var buffer = FloatArray(44100 * 2)
    private var writePos = 0
    private var lfoPhase = 0.0
    override fun process(sample: Float, channel: Int): Float {
        val maxDelay = (sampleRate * 0.01).toInt() // 10ms max delay
        if (buffer.size != maxDelay * 2) {
            buffer = FloatArray(maxDelay * 2)
            writePos = 0
        }
        if (channel == 0) {
            lfoPhase += 0.0001 * (44100.0 / sampleRate)
            if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI
        }
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
    override var sampleRate = 44100
    private var lfoPhase = 0.0
    private val state1 = FloatArray(2)
    override fun process(sample: Float, channel: Int): Float {
        if (channel == 0) {
            lfoPhase += 0.0002 * (44100.0 / sampleRate)
            if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI
        }
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
    override var sampleRate = 44100
    private var counter = 0
    private var isUpdatingFrame = false
    private val heldSample = FloatArray(2)
    override fun process(sample: Float, channel: Int): Float {
        val decimation = (amount * 20).toInt() + 1
        if (channel == 0) {
            counter++
            isUpdatingFrame = (counter >= decimation)
            if (isUpdatingFrame) counter = 0
        }
        if (isUpdatingFrame) {
            val bits = (16 - (amount * 12).toInt()).coerceIn(1, 16)
            val steps = 1.shl(bits)
            heldSample[channel] = round(sample * steps) / steps
        }
        return sample * (1f - amount) + heldSample[channel] * amount
    }
    override fun reset() {
        counter = 0
        isUpdatingFrame = false
        heldSample.fill(0f)
    }
}

class DistortionPlugin : AudioPlugin {
    override val id = "fx_distortion"
    override val name = "Distortion"
    override var enabled = false
    override var amount = 0.5f
    override var sampleRate = 44100
    override fun process(sample: Float, channel: Int): Float {
        val drive = 1f + amount * 10f
        val wet = (sample * drive).coerceIn(-1f, 1f)
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
    override var sampleRate = 44100
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

class CustomDelayPlugin(override val id: String, override val name: String, private val lengthParam: Float) : AudioPlugin {
    override var enabled = false
    override var amount = 0.5f
    override var sampleRate = 44100
    private val maxFrames = 96000
    private val buffer = FloatArray(maxFrames * 2)
    private var writePos = 0
    override fun process(sample: Float, channel: Int): Float {
        val delayLength = (sampleRate * lengthParam).toInt().coerceIn(1, maxFrames)
        val readPos = (writePos - delayLength * 2 + buffer.size) % buffer.size
        val delayed = buffer[readPos / 2 * 2 + channel]
        val wet = sample + delayed * 0.5f
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

class CustomReverbPlugin(override val id: String, override val name: String, private val sizeParam: Float) : AudioPlugin {
    override var enabled = false
    override var amount = 0.5f
    override var sampleRate = 44100
    private val maxFrames = 96000
    private val buffer = FloatArray(maxFrames * 2)
    private var writePos = 0
    override fun process(sample: Float, channel: Int): Float {
        val delayLength = (sampleRate * (0.04 + sizeParam * 0.4)).toInt().coerceIn(1, maxFrames)
        val readPos = (writePos - delayLength * 2 + buffer.size) % buffer.size
        val delayed = buffer[readPos / 2 * 2 + channel]
        val wet = sample + delayed * 0.4f
        val writeIdx = writePos / 2 * 2 + channel
        buffer[writeIdx] = sample + delayed * 0.6f
        if (channel == 1) writePos = (writePos + 2) % buffer.size
        return sample * (1f - amount) + wet * amount
    }
    override fun reset() {
        buffer.fill(0f)
        writePos = 0
    }
}

class CustomFlangerPlugin(override val id: String, override val name: String, private val rateParam: Float) : AudioPlugin {
    override var enabled = false
    override var amount = 0.5f
    override var sampleRate = 44100
    private var buffer = FloatArray(44100 * 2)
    private var writePos = 0
    private var lfoPhase = 0.0
    override fun process(sample: Float, channel: Int): Float {
        val maxDelay = (sampleRate * 0.01).toInt() // 10ms max delay
        if (buffer.size != maxDelay * 2) {
            buffer = FloatArray(maxDelay * 2)
            writePos = 0
        }
        val lfoSpeed = 0.00002 + rateParam * 0.0003
        if (channel == 0) {
            lfoPhase += lfoSpeed * (44100.0 / sampleRate)
            if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI
        }
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

class CustomPhaserPlugin(override val id: String, override val name: String, private val rateParam: Float) : AudioPlugin {
    override var enabled = false
    override var amount = 0.5f
    override var sampleRate = 44100
    private var lfoPhase = 0.0
    private val state1 = FloatArray(2)
    override fun process(sample: Float, channel: Int): Float {
        val lfoSpeed = 0.00005 + rateParam * 0.0006
        if (channel == 0) {
            lfoPhase += lfoSpeed * (44100.0 / sampleRate)
            if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI
        }
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

class CustomBitcrusherPlugin(override val id: String, override val name: String, private val harshnessParam: Float) : AudioPlugin {
    override var enabled = false
    override var amount = 0.5f
    override var sampleRate = 44100
    private var counter = 0
    private var isUpdatingFrame = false
    private val heldSample = FloatArray(2)
    override fun process(sample: Float, channel: Int): Float {
        val decimation = (harshnessParam * 20).toInt() + 1
        if (channel == 0) {
            counter++
            isUpdatingFrame = (counter >= decimation)
            if (isUpdatingFrame) counter = 0
        }
        if (isUpdatingFrame) {
            val bits = (16 - (harshnessParam * 12).toInt()).coerceIn(1, 16)
            val steps = 1.shl(bits)
            heldSample[channel] = round(sample * steps) / steps
        }
        return sample * (1f - amount) + heldSample[channel] * amount
    }
    override fun reset() {
        counter = 0
        isUpdatingFrame = false
        heldSample.fill(0f)
    }
}

class CustomDistortionPlugin(override val id: String, override val name: String, private val driveParam: Float) : AudioPlugin {
    override var enabled = false
    override var amount = 0.5f
    override var sampleRate = 44100
    override fun process(sample: Float, channel: Int): Float {
        val drive = 1f + (amount * driveParam) * 15f
        val wet = (sample * drive).coerceIn(-1f, 1f)
        val out = if (wet > 0) 1f - exp(-wet) else -1f + exp(wet)
        return sample * (1f - amount) + out * amount
    }
    override fun reset() {}
}

class CustomCompressorPlugin(
    override val id: String,
    override val name: String,
    private val thresholdParam: Float,
    private val ratioParam: Float
) : AudioPlugin {
    override var enabled = false
    override var amount = 0.5f
    override var sampleRate = 44100
    override fun process(sample: Float, channel: Int): Float {
        val threshold = 0.05f + thresholdParam * 0.6f
        val ratio = 1f + ratioParam * 9f
        val magnitude = abs(sample)
        if (magnitude <= threshold) return sample
        val excess = magnitude - threshold
        val compressed = threshold + (excess / ratio)
        val wet = if (sample < 0f) -compressed else compressed
        return sample * (1f - amount) + wet * amount
    }
    override fun reset() {}
}

class CustomFilterPlugin(override val id: String, override val name: String, private val cutoffParam: Float) : AudioPlugin {
    override var enabled = false
    override var amount = 0.5f
    override var sampleRate = 44100
    private val lpState = FloatArray(2)
    override fun process(sample: Float, channel: Int): Float {
        val cutoff = 0.05f + 0.95f * (amount * cutoffParam)
        lpState[channel] += cutoff * (sample - lpState[channel])
        val wet = lpState[channel]
        return sample * (1f - amount) + wet * amount
    }
    override fun reset() {
        lpState.fill(0f)
    }
}
