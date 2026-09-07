package com.example.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import com.example.player.fx.DspPluginManager
import com.example.player.fx.AudioPlugin
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Real-time PCM DSP effects processor for DJ decks.
 *
 * Authoritative audio path:
 * PCM -> 10-band EQ -> Preamp -> Soft Limiter -> DJ FX -> Output.
 * Android's platform Equalizer is intentionally not used in parallel.
 */
class DeckFxAudioProcessor : AudioProcessor {
    enum class Effect {
        FILTER, FILTER_ROLL, NOISE, FLANGER, REVERB, ECHO, DELAY,
        PHASER, TREMOLO, CHOPPA, MUTE, FADER_TONE, ROLL, STUTTER,
        GATE, BITCRUSH, TELEPHONE, VINYL, ROBOT, RING_MOD, AUTO_PAN,
        LOW_PASS, HIGH_PASS, SPACE, PITCH_ECHO, TAPE_STOP, TRANSFORM,
        SLICE, BEAT_REPEAT
    }

    companion object {
        private val EQ_FREQUENCIES = floatArrayOf(
            60f, 170f, 310f, 600f, 1000f,
            3000f, 6000f, 12000f, 14000f, 16000f
        )
        private const val EQ_Q = 1.0f
        private const val EQ_TRANSITION_FRAMES = 256
        private const val DEFAULT_PREAMP_DB = 0.0f
        private const val MAX_PREAMP_DB = 12.0f
        private const val LIMITER_THRESHOLD = 0.98f

        @Volatile
        private var globalPreampDb = DEFAULT_PREAMP_DB

        fun setGlobalPreampDb(value: Float) {
            globalPreampDb = value.coerceIn(0f, MAX_PREAMP_DB)
        }
    }

    private val activeEffects = mutableSetOf<Effect>()
    private val pluginChain = DspPluginManager.createChain()

    var flangerEnabled: Boolean
        get() = activeEffects.contains(Effect.FLANGER)
        set(value) { setEffect(Effect.FLANGER, value) }

    var reverbEnabled: Boolean
        get() = activeEffects.contains(Effect.REVERB)
        set(value) { setEffect(Effect.REVERB, value) }

    var echoEnabled: Boolean
        get() = activeEffects.contains(Effect.ECHO)
        set(value) { setEffect(Effect.ECHO, value) }

    var crushEnabled: Boolean
        get() = activeEffects.contains(Effect.BITCRUSH)
        set(value) { setEffect(Effect.BITCRUSH, value) }

    @Volatile var amount = 0.65f
    @Volatile var beatDivision = 0.25f

    private var inputFormat = AudioProcessor.AudioFormat.NOT_SET
    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false
    private var sampleRate = 44_100
    private var channelCount = 2

    // EQ: two complete banks allow click-free live coefficient changes.
    // Updates are coalesced while a transition is already running so the
    // active/target filter state is never reset from the audio thread.
    private var activeEqFilters = createEqBank()
    private var transitionEqFilters = createEqBank()
    private var eqLevels = FloatArray(10)
    @Volatile private var needsEqUpdate = true
    @Volatile var eqEnabled = false
    private var eqTransitionActive = false
    private var eqTransitionPosition = EQ_TRANSITION_FRAMES

    // Kept for backwards-compatible callers. The authoritative preamp is the
    // shared value controlled by EqualizerController.
    @Volatile private var preampDb = DEFAULT_PREAMP_DB

    // Delay lines & states
    private var maxDelayFrames = 44_100
    private var delayLine = FloatArray(44_100 * 2)
    private var writeFrame = 0
    private var lfoPhase = 0.0
    private var tapeStopPhase = 0.0

    // Filter states per channel
    private val lpState = FloatArray(2)
    private val hpState = FloatArray(2)
    private val bpState = FloatArray(2)
    private val phaserState = Array(4) { FloatArray(2) }

    // Roll / Stutter loop buffer
    private var rollBuffer = FloatArray(44_100 * 2)
    private var rollWritePos = 0
    private var rollActive = false
    private var rollLengthFrames = 44_100 / 4

    // Bitcrush decimation counter
    private var crushCounter = 0
    private val crushHeldSample = FloatArray(2)

    private fun createEqBank(): Array<Array<BiquadFilter>> =
        Array(2) { Array(10) { BiquadFilter() } }

    private fun replaceOutputBuffer(count: Int): ByteBuffer {
        if (buffer.capacity() < count) {
            buffer = ByteBuffer.allocateDirect(count).order(ByteOrder.LITTLE_ENDIAN)
        } else {
            buffer.clear()
        }
        outputBuffer = buffer
        return buffer
    }

    @Synchronized
    fun setEffect(effect: Effect, active: Boolean) {
        if (active) {
            activeEffects.add(effect)
            if (effect == Effect.ROLL || effect == Effect.STUTTER || effect == Effect.BEAT_REPEAT) {
                rollActive = true
                rollLengthFrames = max(256, (sampleRate * beatDivision).toInt())
                rollWritePos = 0
            }
            if (effect == Effect.TAPE_STOP) {
                tapeStopPhase = 0.0
            }
        } else {
            activeEffects.remove(effect)
            if (effect == Effect.ROLL || effect == Effect.STUTTER || effect == Effect.BEAT_REPEAT) {
                rollActive = false
            }
        }
    }

    fun isEffectEnabled(effect: Effect): Boolean = activeEffects.contains(effect)

    /** Updates the PCM EQ controls. Values are copied/clamped for audio-thread use. */
    fun setEqLevels(levels: FloatArray, enabled: Boolean) {
        val next = FloatArray(10)
        for (i in 0 until min(10, levels.size)) {
            next[i] = levels[i].coerceIn(-12f, 12f)
        }
        eqLevels = next
        eqEnabled = enabled
        if (!enabled) {
            eqTransitionActive = false
            eqTransitionPosition = EQ_TRANSITION_FRAMES
            needsEqUpdate = false
        } else {
            needsEqUpdate = true
        }
    }

    /** Independent make-up gain. EQ preamp never acts when EQ is OFF. */
    fun setEqPreampDb(value: Float) {
        preampDb = value.coerceIn(0f, MAX_PREAMP_DB)
        setGlobalPreampDb(preampDb)
    }

    fun getEqPreampDb(): Float = globalPreampDb

    private fun prepareEqTransition() {
        // Only the inactive bank is reconfigured. It is reset once here,
        // before it becomes the target bank; the running bank is never reset.
        for (ch in 0 until channelCount) {
            for (i in 0 until 10) {
                transitionEqFilters[ch][i].setPeakingEQ(
                    EQ_FREQUENCIES[i],
                    eqLevels[i],
                    EQ_Q,
                    sampleRate.toFloat()
                )
                transitionEqFilters[ch][i].resetState()
            }
        }
        eqTransitionPosition = 0
        eqTransitionActive = true
        needsEqUpdate = false
    }

    private fun beginPendingEqUpdateIfNeeded() {
        if (eqEnabled && needsEqUpdate && !eqTransitionActive) {
            prepareEqTransition()
        }
    }

    private fun applyEq(sample: Float, ch: Int, transitionAmount: Float): Float {
        if (!eqEnabled) return sample

        val current = activeEqFilters[ch]
        if (!eqTransitionActive) {
            var result = sample
            for (i in 0 until 10) result = current[i].process(result)
            return result
        }

        val next = transitionEqFilters[ch]
        var currentOut = sample
        var nextOut = sample
        for (i in 0 until 10) {
            currentOut = current[i].process(currentOut)
            nextOut = next[i].process(nextOut)
        }
        return currentOut * (1f - transitionAmount) + nextOut * transitionAmount
    }

    private fun preampLinearGain(): Float =
        Math.pow(10.0, (globalPreampDb.coerceIn(0f, MAX_PREAMP_DB)).toDouble() / 20.0).toFloat()

    private fun softLimit(sample: Float): Float {
        val magnitude = abs(sample)
        if (magnitude <= LIMITER_THRESHOLD) return sample
        val excess = (magnitude - LIMITER_THRESHOLD) / (1f - LIMITER_THRESHOLD)
        val compressed = LIMITER_THRESHOLD + (1f - LIMITER_THRESHOLD) * tanh(excess)
        return if (sample < 0f) -compressed else compressed
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.sampleRate <= 0 ||
            inputAudioFormat.channelCount !in 1..2
        ) {
            inputFormat = AudioProcessor.AudioFormat.NOT_SET
            return AudioProcessor.AudioFormat.NOT_SET
        }

        inputFormat = inputAudioFormat
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        maxDelayFrames = sampleRate * 2
        delayLine = FloatArray(maxDelayFrames * channelCount)
        rollBuffer = FloatArray(maxDelayFrames * channelCount)

        for (bank in arrayOf(activeEqFilters, transitionEqFilters)) {
            for (ch in 0 until channelCount) {
                for (i in 0 until 10) {
                    bank[ch][i].setPeakingEQ(EQ_FREQUENCIES[i], 0f, EQ_Q, sampleRate.toFloat())
                    bank[ch][i].resetState()
                }
            }
        }
        eqTransitionActive = false
        eqTransitionPosition = EQ_TRANSITION_FRAMES
        needsEqUpdate = true
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

        // EQ OFF + no FX = exact PCM pass-through. No preamp or limiter is applied.
        if (activeEffects.isEmpty() && !eqEnabled) {
            val output = replaceOutputBuffer(bytes)
            output.put(inputBuffer)
            output.flip()
            return
        }

        beginPendingEqUpdateIfNeeded()

        val output = replaceOutputBuffer(bytes)
        val frames = bytes / (2 * channelCount)
        val fxAmount = amount.coerceIn(0.05f, 1f)
        val div = beatDivision.coerceIn(0.0625f, 1f)
        val preampGain = preampLinearGain()

        fun readDelay(framesBack: Int, ch: Int): Float {
            if (maxDelayFrames <= 0) return 0f
            val safeBack = framesBack.coerceIn(1, maxDelayFrames - 1)
            var pos = writeFrame - safeBack
            if (pos < 0) pos += maxDelayFrames
            val idx = pos * channelCount + ch
            return if (idx in delayLine.indices) delayLine[idx] else 0f
        }

        val rollFrames = max(256, (sampleRate * div).toInt())

        for (f in 0 until frames) {
            lfoPhase += 1.0 / sampleRate
            if (lfoPhase > 100.0) lfoPhase -= 100.0

            val lfoVal = (sin(2.0 * PI * 0.45 * lfoPhase) + 1.0) * 0.5
            val tremVal = (0.5 + 0.5 * sin(2.0 * PI * 6.0 * lfoPhase)).toFloat()
            val panVal = (0.5 + 0.5 * sin(2.0 * PI * 0.8 * lfoPhase)).toFloat()

            val gateWindow = max(1, (sampleRate * div).toInt())
            val gateOffset = (writeFrame % gateWindow)
            val isGateOn = gateOffset < (gateWindow * 0.55f)

            val transitionAmount = if (eqTransitionActive) {
                ((eqTransitionPosition + 1).toFloat() / EQ_TRANSITION_FRAMES.toFloat()).coerceIn(0f, 1f)
            } else {
                1f
            }

            for (ch in 0 until channelCount) {
                if (!inputBuffer.hasRemaining()) break
                val inputShort = inputBuffer.short
                var sample = inputShort.toFloat() / 32768.0f

                // 10-band EQ -> independent preamp -> soft limiter.
                if (eqEnabled) {
                    sample = applyEq(sample, ch, transitionAmount)
                    sample *= preampGain
                    sample = softLimit(sample)
                }

                
                // MODULAR FX ENGINE
                // Processing each active plugin sequentially
                for (plugin in pluginChain) {
                    val targetEffect = mapEffectToPlugin(plugin.id)
                    val targetEnabled = targetEffect != null && activeEffects.contains(targetEffect)
                    
                    if (targetEnabled && fxAmount > 0.01f) {
                        plugin.enabled = true
                        plugin.amount = fxAmount
                        sample = plugin.process(sample, ch)
                    } else {
                        // Reset plugin states if they were just disabled
                        if (plugin.enabled) {
                            plugin.enabled = false
                            plugin.reset()
                        }
                    }
                }
                
                // Final safety limit
                val outSample = softLimit(sample).coerceIn(-1f, 1f)
                
                val delayIdx = writeFrame * channelCount + ch
                if (delayIdx in delayLine.indices) delayLine[delayIdx] = outSample

                output.putShort((outSample * 32767.0f).roundToInt().toShort())
            }

            writeFrame = (writeFrame + 1) % maxDelayFrames
            rollWritePos++
            crushCounter++

            // Transition progress is per audio frame, not per channel. If the
            // UI changed again during this transition, the new target waits
            // until the current transition completes and is applied next block.
            if (eqTransitionActive) {
                eqTransitionPosition++
                if (eqTransitionPosition >= EQ_TRANSITION_FRAMES) {
                    val oldActive = activeEqFilters
                    activeEqFilters = transitionEqFilters
                    transitionEqFilters = oldActive
                    for (ch in 0 until channelCount) {
                        for (i in 0 until 10) {
                            transitionEqFilters[ch][i].resetState()
                        }
                    }
                    eqTransitionActive = false
                    eqTransitionPosition = EQ_TRANSITION_FRAMES
        pluginChain.forEach { it.reset() }
    }
            }
        }

        output.flip()
    }


    private fun mapEffectToPlugin(id: String): Effect? {
        return when (id) {
            "fx_filter" -> Effect.FILTER
            "fx_delay" -> Effect.DELAY
            "fx_reverb" -> Effect.REVERB
            "fx_flanger" -> Effect.FLANGER
            "fx_phaser" -> Effect.PHASER
            "fx_bitcrush" -> Effect.BITCRUSH
            "fx_distortion" -> Effect.TRANSFORM // Map distortion to something existing in UI, e.g. TRANSFORM
            "fx_compressor" -> Effect.GATE      // Map compressor to GATE in UI
            else -> null
        }
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
        writeFrame = 0
        lfoPhase = 0.0
        tapeStopPhase = 0.0
        delayLine.fill(0f)
        lpState.fill(0f)
        hpState.fill(0f)
        bpState.fill(0f)
        rollBuffer.fill(0f)
        crushHeldSample.fill(0f)
        crushCounter = 0
        rollWritePos = 0
        for (ch in 0 until 2) {
            for (i in 0 until 10) {
                activeEqFilters[ch][i].resetState()
                transitionEqFilters[ch][i].resetState()
            }
        }
        eqTransitionActive = false
        eqTransitionPosition = EQ_TRANSITION_FRAMES
        pluginChain.forEach { it.reset() }
    }

    override fun reset() {
        flush()
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
        sampleRate = 44_100
        channelCount = 2
        activeEffects.clear()
        eqLevels = FloatArray(10)
        eqEnabled = false
        needsEqUpdate = true
        preampDb = DEFAULT_PREAMP_DB
    }
}
