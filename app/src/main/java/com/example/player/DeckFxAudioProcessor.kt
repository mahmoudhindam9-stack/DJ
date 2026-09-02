package com.example.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Real-time PCM DSP effects processor for DJ decks.
 * High-performance, click-free audio processing with 29 professional live effects.
 */
class DeckFxAudioProcessor : AudioProcessor {
    enum class Effect {
        FILTER, FILTER_ROLL, NOISE, FLANGER, REVERB, ECHO, DELAY,
        PHASER, TREMOLO, CHOPPA, MUTE, FADER_TONE, ROLL, STUTTER,
        GATE, BITCRUSH, TELEPHONE, VINYL, ROBOT, RING_MOD, AUTO_PAN,
        LOW_PASS, HIGH_PASS, SPACE, PITCH_ECHO, TAPE_STOP, TRANSFORM,
        SLICE, BEAT_REPEAT
    }

    private val activeEffects = mutableSetOf<Effect>()

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

    private fun applyEq(sample: Float, ch: Int): Float {
        var s = sample
        if (needsEqUpdate) {
            val freqs = floatArrayOf(60f, 170f, 310f, 600f, 1000f, 3000f, 6000f, 12000f, 14000f, 16000f)
            for (c in 0 until channelCount) {
                for (i in 0 until 10) {
                    eqFilters[c][i].setPeakingEQ(freqs[i], eqLevels[i], 1.0f, sampleRate.toFloat())
                }
            }
            needsEqUpdate = false
        }
        for (i in 0 until 10) {
            s = eqFilters[ch][i].process(s)
        }
        return s
    }

    private fun applyLimiter(sample: Float): Float {
        return sample.coerceIn(-1.0f, 1.0f)
    }

    private var inputFormat = AudioProcessor.AudioFormat.NOT_SET
    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false
    private var sampleRate = 44_100
    private var channelCount = 2

    // EQ
    private val eqFilters = Array(2) { Array(10) { BiquadFilter() } }
    private var eqLevels = FloatArray(10)
    @Volatile private var needsEqUpdate = true
    @Volatile var eqEnabled = false

    fun setEqLevels(levels: FloatArray, enabled: Boolean) {
        eqLevels = levels
        eqEnabled = enabled
        needsEqUpdate = true
    }

    // Delay lines & states
    private var maxDelayFrames = 44100
    private var delayLine = FloatArray(44100 * 2)
    private var writeFrame = 0
    private var lfoPhase = 0.0
    private var tapeStopPhase = 0.0

    // Filter states per channel
    private val lpState = FloatArray(2)
    private val hpState = FloatArray(2)
    private val bpState = FloatArray(2)
    private val phaserState = Array(4) { FloatArray(2) }

    // Roll / Stutter loop buffer
    private var rollBuffer = FloatArray(44100 * 2)
    private var rollWritePos = 0
    private var rollActive = false
    private var rollLengthFrames = 44100 / 4

    // Bitcrush decimation counter
    private var crushCounter = 0
    private val crushHeldSample = FloatArray(2)

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

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.sampleRate <= 0 || inputAudioFormat.channelCount !in 1..2) {
            inputFormat = AudioProcessor.AudioFormat.NOT_SET
            return AudioProcessor.AudioFormat.NOT_SET
        }
        inputFormat = inputAudioFormat
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        maxDelayFrames = sampleRate * 2 // 2 seconds
        delayLine = FloatArray(maxDelayFrames * channelCount)
        rollBuffer = FloatArray(maxDelayFrames * channelCount)
        
        // Initialize EQ filters
        val freqs = floatArrayOf(60f, 170f, 310f, 600f, 1000f, 3000f, 6000f, 12000f, 14000f, 16000f)
        for (ch in 0 until channelCount) {
            for (i in 0 until 10) {
                eqFilters[ch][i].setPeakingEQ(freqs[i], 0f, 1.0f, sampleRate.toFloat())
            }
        }
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

        if (activeEffects.isEmpty()) {
            val output = replaceOutputBuffer(bytes)
            output.put(inputBuffer)
            output.flip()
            return
        }

        val output = replaceOutputBuffer(bytes)
        val frames = bytes / (2 * channelCount)
        val fxAmount = amount.coerceIn(0.05f, 1f)
        val div = beatDivision.coerceIn(0.0625f, 1f)

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

            // Beat gate/choppa window
            val gateWindow = max(1, (sampleRate * div).toInt())
            val gateOffset = (writeFrame % gateWindow)
            val isGateOn = gateOffset < (gateWindow * 0.55f)

            for (ch in 0 until channelCount) {
                if (!inputBuffer.hasRemaining()) break
                val inputShort = inputBuffer.short
                var sample = inputShort.toFloat() / 32768.0f
                if (eqEnabled) {
                    sample = applyEq(sample, ch)
                }

                // 1. Roll / Stutter / Beat Repeat (Buffer capture and repeat)
                if (activeEffects.contains(Effect.ROLL) || activeEffects.contains(Effect.STUTTER) || activeEffects.contains(Effect.BEAT_REPEAT)) {
                    val rollIdx = (rollWritePos % rollFrames) * channelCount + ch
                    if (rollIdx in rollBuffer.indices) {
                        sample = rollBuffer[rollIdx]
                    }
                } else {
                    val rollIdx = (rollWritePos % maxDelayFrames) * channelCount + ch
                    if (rollIdx in rollBuffer.indices) {
                        rollBuffer[rollIdx] = sample
                    }
                }

                // 2. Low Pass / Filter
                if (activeEffects.contains(Effect.FILTER) || activeEffects.contains(Effect.LOW_PASS)) {
                    val cutoff = 0.03f + 0.65f * (1f - fxAmount)
                    lpState[ch] += cutoff * (sample - lpState[ch])
                    sample = lpState[ch] * (1f + 0.2f * fxAmount)
                }

                // 3. High Pass
                if (activeEffects.contains(Effect.HIGH_PASS)) {
                    val cutoff = 0.05f + 0.70f * fxAmount
                    hpState[ch] += cutoff * (sample - hpState[ch])
                    sample = (sample - hpState[ch]) * (1f + 0.3f * fxAmount)
                }

                // 4. Filter Roll
                if (activeEffects.contains(Effect.FILTER_ROLL)) {
                    val modCutoff = if (isGateOn) 0.15f + 0.65f * fxAmount else 0.03f
                    lpState[ch] += modCutoff * (sample - lpState[ch])
                    sample = lpState[ch]
                }

                // 5. Flanger
                if (activeEffects.contains(Effect.FLANGER)) {
                    val delaySamples = (sampleRate * (0.001 + 0.004 * lfoVal)).toInt().coerceIn(1, maxDelayFrames - 1)
                    val delayed = readDelay(delaySamples, ch)
                    sample = sample * (1f - 0.4f * fxAmount) + delayed * 0.7f * fxAmount
                }

                // 6. Phaser
                if (activeEffects.contains(Effect.PHASER)) {
                    val phaserDelay = (sampleRate * (0.0015 + 0.0025 * lfoVal)).toInt().coerceIn(1, maxDelayFrames - 1)
                    val phaserDelayed = readDelay(phaserDelay, ch)
                    sample = sample * 0.6f + phaserDelayed * 0.6f * fxAmount
                }

                // 7. Reverb & Space
                if (activeEffects.contains(Effect.REVERB) || activeEffects.contains(Effect.SPACE)) {
                    val r1 = readDelay((sampleRate * 0.032).toInt(), ch)
                    val r2 = readDelay((sampleRate * 0.065).toInt(), ch)
                    val r3 = readDelay((sampleRate * 0.095).toInt(), ch)
                    val rev = (r1 * 0.35f + r2 * 0.25f + r3 * 0.20f) * fxAmount
                    sample = sample + rev
                }

                // 8. Echo & Delay
                if (activeEffects.contains(Effect.ECHO) || activeEffects.contains(Effect.DELAY)) {
                    val delayTimeSec = if (activeEffects.contains(Effect.DELAY)) (div * 0.5f).coerceIn(0.08f, 0.65f) else 0.24f
                    val delayFramesCount = (sampleRate * delayTimeSec).toInt().coerceIn(1, maxDelayFrames - 1)
                    val echo = readDelay(delayFramesCount, ch) * (0.45f + 0.35f * fxAmount)
                    sample = sample + echo
                }

                // 9. Pitch Echo
                if (activeEffects.contains(Effect.PITCH_ECHO)) {
                    val delay1 = readDelay((sampleRate * 0.14).toInt(), ch)
                    val delay2 = readDelay((sampleRate * 0.28).toInt(), ch)
                    sample = sample + (delay1 * 0.4f + delay2 * 0.25f) * fxAmount
                }

                // 10. Tremolo
                if (activeEffects.contains(Effect.TREMOLO)) {
                    sample *= (1f - fxAmount) + fxAmount * tremVal
                }

                // 11. Choppa & Slice & Transform
                if (activeEffects.contains(Effect.CHOPPA) || activeEffects.contains(Effect.SLICE) || activeEffects.contains(Effect.TRANSFORM)) {
                    val mult = if (isGateOn) 1f else (1f - fxAmount)
                    sample *= mult
                }

                // 12. Mute
                if (activeEffects.contains(Effect.MUTE)) {
                    sample = 0f
                }

                // 13. Fader Tone
                if (activeEffects.contains(Effect.FADER_TONE)) {
                    val tone = abs(cos(2.0 * PI * 1.5 * lfoPhase)).toFloat()
                    sample *= (0.25f + 0.75f * tone)
                }

                // 14. Gate
                if (activeEffects.contains(Effect.GATE)) {
                    val threshold = 0.05f + 0.15f * fxAmount
                    if (abs(sample) < threshold) {
                        sample *= 0.1f
                    }
                }

                // 15. Bitcrush
                if (activeEffects.contains(Effect.BITCRUSH)) {
                    val decimate = max(1, (1 + (12 * fxAmount)).toInt())
                    if (crushCounter % decimate == 0) {
                        val steps = max(4f, 48f - 40f * fxAmount)
                        crushHeldSample[ch] = (sample * steps).roundToInt() / steps
                    }
                    sample = crushHeldSample[ch]
                }

                // 16. Telephone
                if (activeEffects.contains(Effect.TELEPHONE)) {
                    bpState[ch] += 0.25f * (sample - bpState[ch])
                    sample = (bpState[ch] * 2.2f).coerceIn(-0.75f, 0.75f) * 1.3f
                }

                // 17. Vinyl
                if (activeEffects.contains(Effect.VINYL)) {
                    val wow = sin(2.0 * PI * 0.55 * lfoPhase).toFloat() * 0.001f
                    val d = (sampleRate * (0.01 + wow)).toInt().coerceIn(1, maxDelayFrames - 1)
                    val vinylWobble = readDelay(d, ch)
                    val crackle = if (Math.random() < 0.008) (Math.random().toFloat() * 2f - 1f) * 0.15f else 0f
                    sample = sample * 0.85f + vinylWobble * 0.15f + crackle * fxAmount
                }

                // 18. Robot
                if (activeEffects.contains(Effect.ROBOT)) {
                    val carrier = sin(2.0 * PI * 160.0 * lfoPhase).toFloat()
                    sample = (sample * carrier * 1.6f).coerceIn(-1f, 1f)
                }

                // 19. Ring Mod
                if (activeEffects.contains(Effect.RING_MOD)) {
                    val ringCarrier = sin(2.0 * PI * (350.0 + 400.0 * fxAmount) * lfoPhase).toFloat()
                    sample = (sample * ringCarrier * 1.4f).coerceIn(-1f, 1f)
                }

                // 20. Auto Pan
                if (activeEffects.contains(Effect.AUTO_PAN) && channelCount > 1) {
                    sample *= if (ch == 0) (1f - panVal * fxAmount) else (0.3f + panVal * 0.7f * fxAmount)
                }

                // 21. Noise
                if (activeEffects.contains(Effect.NOISE)) {
                    val n = (Math.random().toFloat() * 2f - 1f) * 0.12f * fxAmount
                    sample += n
                }

                // 22. Tape Stop
                if (activeEffects.contains(Effect.TAPE_STOP)) {
                    tapeStopPhase += 1.0 / sampleRate
                    val decay = exp(-tapeStopPhase * 3.5).toFloat()
                    sample *= decay
                }

                val outSample = applyLimiter(sample)

                // Save to delay line
                val delayIdx = writeFrame * channelCount + ch
                if (delayIdx in delayLine.indices) {
                    delayLine[delayIdx] = outSample
                }

                output.putShort((outSample * 32767.0f).roundToInt().toShort())
            }

            writeFrame = (writeFrame + 1) % maxDelayFrames
            rollWritePos++
            crushCounter++
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
        writeFrame = 0
        lfoPhase = 0.0
        tapeStopPhase = 0.0
        delayLine.fill(0f)
        lpState.fill(0f)
        hpState.fill(0f)
        bpState.fill(0f)
        rollBuffer.fill(0f)
    }

    override fun reset() {
        flush()
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
        sampleRate = 44_100
        channelCount = 2
        activeEffects.clear()
    }
}
