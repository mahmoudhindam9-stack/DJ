package com.example.player

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.C
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import com.example.fx.DspPluginManager
import com.example.fx.AudioPlugin

class DeckFxAudioProcessor : AudioProcessor {
    private var sampleRate = 44_100
    private var channelCount = 2
    private var inputFormat = AudioProcessor.AudioFormat.NOT_SET
    
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    var amount: Float = 0.5f
    var beatDivision: Float = 0.25f

    // We store plugin IDs here directly
    val activeEffects = mutableSetOf<String>()

    private var pluginManager: DspPluginManager? = null
    private var pluginChain = emptyList<AudioPlugin>()

    // EQ stuff
    private val eqLevels = FloatArray(10)
    var eqEnabled = false
    private val activeEqFilters = Array(2) { Array(10) { BiquadFilter() } }
    
    fun initContext(context: android.content.Context) {
        pluginManager = DspPluginManager(context)
        pluginChain = pluginManager!!.getAvailablePlugins()
    }

    /**
     * Rebuilds the plugin chain from the library. Must be called whenever the
     * user creates, imports, or deletes a custom effect from the Effects
     * Library — otherwise `pluginChain` stays frozen at whatever existed when
     * the deck first started, so brand-new effects show as "active" in the UI
     * but never actually touch the audio.
     */
    fun refreshPlugins() {
        pluginManager?.let { pluginChain = it.getAvailablePlugins() }
    }

    fun setEqLevels(levels: FloatArray, enabled: Boolean) {
        if (levels.size == 10) {
            System.arraycopy(levels, 0, eqLevels, 0, 10)
            for (ch in 0 until 2) {
                for (i in 0 until 10) {
                    activeEqFilters[ch][i].setPeakingEQ(EQ_FREQUENCIES[i], eqLevels[i], 1.2f, sampleRate.toFloat())
                }
            }
        }
        eqEnabled = enabled
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (outputBuffer.capacity() < size) {
            outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }
        return outputBuffer
    }

    private fun applyEq(sample: Float, ch: Int): Float {
        var s = sample
        for (i in 0 until 10) {
            s = activeEqFilters[ch][i].process(s)
        }
        return s
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.sampleRate <= 0 || inputAudioFormat.channelCount !in 1..2) {
            inputFormat = AudioProcessor.AudioFormat.NOT_SET
            return AudioProcessor.AudioFormat.NOT_SET
        }
        inputFormat = inputAudioFormat
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        
        for (ch in 0 until 2) {
            for (i in 0 until 10) {
                activeEqFilters[ch][i].setPeakingEQ(EQ_FREQUENCIES[i], eqLevels[i], 1.2f, sampleRate.toFloat())
                activeEqFilters[ch][i].resetState()
            }
        }
        return inputAudioFormat
    }

    override fun isActive(): Boolean = inputFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive()) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val bytes = inputBuffer.remaining()
        if (bytes <= 0) return

        if (activeEffects.isEmpty() && !eqEnabled) {
            val output = replaceOutputBuffer(bytes)
            output.put(inputBuffer)
            output.flip()
            return
        }

        val output = replaceOutputBuffer(bytes)
        val frames = bytes / (2 * channelCount)
        val fxAmount = amount.coerceIn(0.01f, 1f)

        for (f in 0 until frames) {
            for (ch in 0 until channelCount) {
                if (!inputBuffer.hasRemaining()) break
                val inputShort = inputBuffer.short
                var sample = inputShort.toFloat() / 32768.0f

                if (eqEnabled) {
                    sample = applyEq(sample, ch)
                }

                // Apply Modular FX Engine
                for (plugin in pluginChain) {
                    val targetEnabled = activeEffects.contains(plugin.id)
                    if (targetEnabled && fxAmount > 0.01f) {
                        plugin.enabled = true
                        plugin.amount = fxAmount
                        sample = plugin.process(sample, ch)
                    } else {
                        if (plugin.enabled) {
                            plugin.enabled = false
                            plugin.reset()
                        }
                    }
                }

                val outSample = sample.coerceIn(-1f, 1f)
                output.putShort((outSample * 32767.0f).roundToInt().toShort())
            }
        }
        output.flip()
    }

    override fun queueEndOfStream() { inputEnded = true }
    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return output
    }
    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER
    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        for (ch in 0 until 2) {
            for (i in 0 until 10) activeEqFilters[ch][i].resetState()
        }
        pluginChain.forEach { it.reset() }
    }
    override fun reset() {
        flush()
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
        sampleRate = 44_100
        channelCount = 2
        activeEffects.clear()
        eqLevels.fill(0f)
        eqEnabled = false
    }

    companion object {
        val EQ_FREQUENCIES = floatArrayOf(32f, 64f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    }
}
