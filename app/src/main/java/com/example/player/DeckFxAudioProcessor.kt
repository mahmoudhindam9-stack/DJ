package com.example.player

import com.example.diagnostics.RuntimeDiagnostics

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.C
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import com.example.fx.DspPluginManager
import com.example.fx.AudioPlugin

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DeckFxAudioProcessor : AudioProcessor {
    var diagnosticsLabel: String = "DSP"
    private var sampleRate = 44_100
    private var diagnosticsLastReportAt = 0L
    private var diagnosticsInputSquares = 0.0
    private var diagnosticsOutputSquares = 0.0
    private var diagnosticsInputPeak = 0f
    private var diagnosticsOutputPeak = 0f
    private var diagnosticsSamples = 0L
    private var channelCount = 2
    private var inputFormat = AudioProcessor.AudioFormat.NOT_SET
    
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    @Volatile var amount: Float = 0.5f
    @Volatile var beatDivision: Float = 0.25f

    // We store plugin IDs here directly, using an immutable set swapped atomically
    @Volatile var activeEffects: Set<String> = emptySet()

    private var pluginManager: DspPluginManager? = null
    @Volatile private var pluginChain = emptyList<AudioPlugin>()
    private val pluginCache = mutableMapOf<String, AudioPlugin>()

    // EQ stuff
    private var eqEnabled = false
    private val activeEqFilters = Array(2) { Array(12) { BiquadFilter() } }
    private var appliedEqVersion = -1L
    
    fun initContext(context: android.content.Context) {
        pluginManager = DspPluginManager(context)
    }

    /**
     * Rebuilds the plugin chain from the library. Must be called whenever the
     * user creates, imports, or deletes a custom effect from the Effects
     * Library — otherwise `pluginChain` stays frozen at whatever existed when
     * the deck first started, so brand-new effects show as "active" in the UI
     * but never actually touch the audio.
     */
    fun refreshPlugins() {
        // Clear cache for custom plugins so they can be rebuilt if they changed
        val keysToRemove = pluginCache.keys.filter { it.startsWith("custom_") }
        keysToRemove.forEach { pluginCache.remove(it) }
        
        // Re-evaluate the active effects to rebuild the chain
        val currentEffects = activeEffects
        activeEffects = emptySet()
        updateActiveEffects(currentEffects)
    }
    
    fun updateActiveEffects(newActiveEffects: Set<String>) {
        if (activeEffects == newActiveEffects) return
        
        val newChain = mutableListOf<AudioPlugin>()
        for (effectId in newActiveEffects) {
            if (effectId.startsWith("voice_")) continue
            var plugin = pluginCache[effectId]
            if (plugin == null) {
                plugin = pluginManager?.createPlugin(effectId)
                if (plugin != null) {
                    plugin.sampleRate = sampleRate
                    pluginCache[effectId] = plugin
                }
            }
            if (plugin != null) {
                newChain.add(plugin)
            }
        }
        
        activeEffects = newActiveEffects
        pluginChain = newChain
    }

    private fun syncGlobalState() {
        val version = GlobalEqualizerState.version
        if (version == appliedEqVersion) return
        val levels = GlobalEqualizerState.levelsDb
        eqEnabled = GlobalEqualizerState.enabled
        for (ch in 0 until channelCount) {
            for (i in 0 until 10) {
                activeEqFilters[ch][i].setPeakingEQ(EQ_FREQUENCIES[i], levels.getOrElse(i) { 0f }, 1.0f, sampleRate.toFloat())
            }
            activeEqFilters[ch][10].setLowShelf(80f, GlobalEqualizerState.bassBoostDb, sampleRate.toFloat())
            activeEqFilters[ch][11].setHighShelf(10000f, GlobalEqualizerState.trebleBoostDb, sampleRate.toFloat())
        }
        appliedEqVersion = version
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
        for (i in 0 until 12) {
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
        
        appliedEqVersion = -1L
        for (ch in 0 until 2) {
            for (i in 0 until 12) {
                activeEqFilters[ch][i].resetState()
            }
        }
        return inputAudioFormat
    }

    override fun isActive(): Boolean = inputFormat != AudioProcessor.AudioFormat.NOT_SET

    private fun softLimit(sample: Float): Float {
        val LIMITER_THRESHOLD = 0.82f
        val magnitude = abs(sample)
        if (magnitude <= LIMITER_THRESHOLD) return sample
        val excess = (magnitude - LIMITER_THRESHOLD) / (1f - LIMITER_THRESHOLD)
        val compressed = LIMITER_THRESHOLD + (1f - LIMITER_THRESHOLD) * tanh(excess)
        return if (sample < 0f) -compressed else compressed
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive()) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val bytes = inputBuffer.remaining()
        if (bytes <= 0) return
        
        syncGlobalState()
        
        if (activeEffects.isEmpty() && !eqEnabled) {
            val output = replaceOutputBuffer(bytes)
            output.put(inputBuffer)
            output.flip()
            return
        }
        
        val output = replaceOutputBuffer(bytes)
        val frames = bytes / (2 * channelCount)
        val fxAmount = amount.coerceIn(0.01f, 1f)
        val gain = if (eqEnabled) Math.pow(10.0, GlobalEqualizerState.preampDb.toDouble() / 20.0).toFloat() else 1f
        
        for (f in 0 until frames) {
            for (ch in 0 until channelCount) {
                if (!inputBuffer.hasRemaining()) break
                val inputShort = inputBuffer.short
                val originalSample = inputShort.toFloat() / 32768.0f
                var sample = originalSample
                
                if (eqEnabled) {
                    sample = applyEq(sample, ch)
                }
                
                for (plugin in pluginChain) {
                    if (fxAmount > 0.01f) {
                        if (!plugin.enabled) plugin.enabled = true
                        plugin.amount = fxAmount
                        if (plugin.sampleRate != sampleRate) plugin.sampleRate = sampleRate
                        sample = plugin.process(sample, ch)
                    } else {
                        if (plugin.enabled) {
                            plugin.enabled = false
                            plugin.reset()
                        }
                    }
                }
                
                if (eqEnabled) {
                    sample *= gain
                }
                sample = softLimit(sample)
                diagnosticsInputSquares += (originalSample * originalSample).toDouble()
                diagnosticsOutputSquares += (sample * sample).toDouble()
                diagnosticsInputPeak = maxOf(diagnosticsInputPeak, kotlin.math.abs(originalSample))
                diagnosticsOutputPeak = maxOf(diagnosticsOutputPeak, kotlin.math.abs(sample))
                diagnosticsSamples++
                
                val outSample = sample.coerceIn(-1f, 1f)
                output.putShort((outSample * 32767.0f).roundToInt().toShort())
            }
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (diagnosticsSamples > 0L && now - diagnosticsLastReportAt >= 1000L) {
            val count = diagnosticsSamples.toDouble()
            val eqDemand = maxOf(
                GlobalEqualizerState.levelsDb.maxOfOrNull { kotlin.math.abs(it) } ?: 0f,
                kotlin.math.abs(GlobalEqualizerState.preampDb),
                GlobalEqualizerState.bassBoostDb,
                GlobalEqualizerState.trebleBoostDb
            )
            RuntimeDiagnostics.updateAudioDspMetrics(
                diagnosticsLabel,
                kotlin.math.sqrt(diagnosticsInputSquares / count).toFloat(),
                kotlin.math.sqrt(diagnosticsOutputSquares / count).toFloat(),
                diagnosticsInputPeak,
                diagnosticsOutputPeak,
                diagnosticsSamples,
                GlobalEqualizerState.enabled,
                eqDemand,
                pluginChain.size
            )
            diagnosticsLastReportAt = now
            diagnosticsInputSquares = 0.0
            diagnosticsOutputSquares = 0.0
            diagnosticsInputPeak = 0f
            diagnosticsOutputPeak = 0f
            diagnosticsSamples = 0L
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
            for (i in 0 until 12) activeEqFilters[ch][i].resetState()
        }
        val currentPlugins = pluginChain
        currentPlugins.forEach { it.reset() }
    }
    override fun reset() {
        flush()
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
        sampleRate = 44_100
        channelCount = 2
        updateActiveEffects(emptySet())
        eqEnabled = false
        appliedEqVersion = -1L
    }

    companion object {
        val EQ_FREQUENCIES = floatArrayOf(60f, 170f, 310f, 600f, 1000f, 3000f, 6000f, 12000f, 14000f, 16000f)
    }
}
