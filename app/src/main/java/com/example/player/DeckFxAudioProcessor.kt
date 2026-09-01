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
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Real-time PCM effects for each DJ deck.
 * All effects are OFF by default; with every effect OFF the processor returns
 * the incoming PCM unchanged so the music path stays clean.
 */
class DeckFxAudioProcessor : AudioProcessor {
    enum class Effect {
        FILTER, FILTER_ROLL, NOISE, FLANGER, REVERB, ECHO, DELAY,
        PHASER, TREMOLO, CHOPPA, MUTE, FADER_TONE, ROLL, STUTTER,
        GATE, BITCRUSH, TELEPHONE, VINYL, ROBOT, RING_MOD, AUTO_PAN,
        LOW_PASS, HIGH_PASS, SPACE, PITCH_ECHO, TAPE_STOP, TRANSFORM,
        SLICE, BEAT_REPEAT
    }

    private val enabled = mutableMapOf<Effect, Boolean>()
    @Volatile var flangerEnabled = false
    @Volatile var reverbEnabled = false
    @Volatile var echoEnabled = false
    @Volatile var crushEnabled = false

    @Volatile var amount = 0.65f
    @Volatile var beatDivision = 0.25f

    private var inputFormat = AudioProcessor.AudioFormat.NOT_SET
    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false
    private var sampleRate = 44_100
    private var channelCount = 2
    private var delayFrames = 22050
    private var delayLine = FloatArray(44100)
    private var writeFrame = 0L
    private var phase = 0.0
    private var filterState = FloatArray(2)
    private var hpState = FloatArray(2)
    private var previous = FloatArray(2)

    private fun replaceOutputBuffer(count: Int): ByteBuffer {
        if (buffer.capacity() < count) {
            buffer = ByteBuffer.allocateDirect(count).order(ByteOrder.LITTLE_ENDIAN)
        } else {
            buffer.clear()
        }
        outputBuffer = buffer
        return buffer
    }

    fun setEffect(effect: Effect, active: Boolean) {
        enabled[effect] = active
        when (effect) {
            Effect.FLANGER -> flangerEnabled = active
            Effect.REVERB -> reverbEnabled = active
            Effect.ECHO, Effect.DELAY, Effect.PITCH_ECHO -> echoEnabled = active
            Effect.BITCRUSH -> crushEnabled = active
            else -> Unit
        }
    }

    fun isEffectEnabled(effect: Effect): Boolean = when (effect) {
        Effect.FLANGER -> flangerEnabled
        Effect.REVERB -> reverbEnabled
        Effect.ECHO, Effect.DELAY, Effect.PITCH_ECHO -> echoEnabled
        Effect.BITCRUSH -> crushEnabled
        else -> enabled[effect] == true
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.sampleRate <= 0 || inputAudioFormat.channelCount !in 1..2) {
            inputFormat = AudioProcessor.AudioFormat.NOT_SET
            return AudioProcessor.AudioFormat.NOT_SET
        }
        inputFormat = inputAudioFormat
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        delayFrames = max(1, sampleRate / 2)
        delayLine = FloatArray(delayFrames * channelCount)
        filterState = FloatArray(channelCount)
        hpState = FloatArray(channelCount)
        previous = FloatArray(channelCount)
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

        val hasFx = flangerEnabled || reverbEnabled || echoEnabled || crushEnabled ||
            enabled.values.any { it }

        if (!hasFx) {
            val output = replaceOutputBuffer(bytes)
            output.put(inputBuffer)
            output.flip()
            return
        }

        val output = replaceOutputBuffer(bytes)
        val frames = bytes / (2 * channelCount)
        val fxAmount = amount.coerceIn(0f, 1f)

        fun delayed(framesBack: Int, ch: Int): Float {
            if (delayFrames <= 0) return 0f
            val safeBack = framesBack.coerceIn(1, delayFrames - 1)
            var framePos = ((writeFrame - safeBack) % delayFrames).toInt()
            if (framePos < 0) framePos += delayFrames
            val idx = framePos * channelCount + ch
            return if (idx in delayLine.indices) delayLine[idx] else 0f
        }

        for (f in 0 until frames) {
            val rollWindow = max(1, (sampleRate * beatDivision.coerceIn(0.0625f, 1f)).toInt())
            val rollOffset = (writeFrame % rollWindow).toInt()
            val rollGate = if (rollOffset < rollWindow / 2) 1f else 0f
            val trem = (0.5 + 0.5 * sin(2.0 * PI * 5.0 * writeFrame / sampleRate.toDouble())).toFloat()
            val pan = (0.5 + 0.5 * sin(phase)).toFloat()

            for (ch in 0 until channelCount) {
                if (!inputBuffer.hasRemaining()) break
                val inputShort = inputBuffer.short
                var sample = inputShort.toFloat() / 32768.0f

                if (isEffectEnabled(Effect.FILTER) || isEffectEnabled(Effect.LOW_PASS)) {
                    val alpha = 0.05f + 0.45f * (1f - fxAmount)
                    filterState[ch] += alpha * (sample - filterState[ch])
                    sample = filterState[ch]
                }
                if (isEffectEnabled(Effect.HIGH_PASS)) {
                    val alpha = 0.05f + 0.45f * (1f - fxAmount)
                    filterState[ch] += alpha * (sample - filterState[ch])
                    sample = sample - filterState[ch]
                }
                if (isEffectEnabled(Effect.FILTER_ROLL)) {
                    val alpha = if (rollGate > 0f) 0.08f + 0.7f * fxAmount else 0.01f
                    filterState[ch] += alpha * (sample - filterState[ch])
                    sample = filterState[ch]
                }
                if (flangerEnabled || isEffectEnabled(Effect.FLANGER)) {
                    val lfo = (sin(2.0 * PI * (writeFrame.toDouble() / sampleRate) * 0.35) + 1.0) * 0.5
                    val d = max(1, (sampleRate * (0.001 + 0.004 * lfo)).toInt())
                    sample += delayed(d, ch) * 0.45f * fxAmount
                }
                if (reverbEnabled || isEffectEnabled(Effect.REVERB) || isEffectEnabled(Effect.SPACE)) {
                    sample += delayed(max(1, (sampleRate * 0.035).toInt()), ch) * 0.25f * fxAmount
                    sample += delayed(max(1, (sampleRate * 0.075).toInt()), ch) * 0.18f * fxAmount
                }
                if (echoEnabled || isEffectEnabled(Effect.ECHO) || isEffectEnabled(Effect.DELAY) || isEffectEnabled(Effect.PITCH_ECHO)) {
                    val delay = max(1, (sampleRate * if (isEffectEnabled(Effect.PITCH_ECHO)) 0.12 else 0.24).toInt())
                    sample += delayed(delay, ch) * (0.28f + 0.18f * fxAmount)
                }
                if (isEffectEnabled(Effect.PHASER)) {
                    val d = max(1, (sampleRate * (0.002 + 0.003 * (0.5 + 0.5 * sin(phase)))).toInt())
                    sample += delayed(d, ch) * 0.32f * fxAmount
                }
                if (isEffectEnabled(Effect.TREMOLO)) sample *= (1f - fxAmount) + fxAmount * trem
                if (isEffectEnabled(Effect.CHOPPA) || isEffectEnabled(Effect.ROLL) || isEffectEnabled(Effect.BEAT_REPEAT)) {
                    sample *= rollGate
                }
                if (isEffectEnabled(Effect.MUTE)) sample = 0f
                if (isEffectEnabled(Effect.FADER_TONE)) {
                    val tone = abs(cos(phase)).toFloat()
                    sample *= 0.35f + 0.65f * tone
                }
                if (isEffectEnabled(Effect.STUTTER)) {
                    val stutterFrames = max(1, (sampleRate * 0.08).toInt())
                    sample = delayed((writeFrame % stutterFrames).toInt(), ch)
                }
                if (isEffectEnabled(Effect.GATE)) {
                    sample = if (abs(sample) >= 0.075f) sample else sample * 0.12f
                }
                if (crushEnabled || isEffectEnabled(Effect.BITCRUSH)) {
                    val steps = max(4f, 64f - 56f * fxAmount)
                    sample = (sample * steps).roundToInt() / steps
                }
                if (isEffectEnabled(Effect.TELEPHONE)) {
                    filterState[ch] += 0.2f * (sample - filterState[ch])
                    sample = (sample - filterState[ch]) * 1.3f
                }
                if (isEffectEnabled(Effect.VINYL)) sample += delayed(max(1, (sampleRate * 0.011).toInt()), ch) * 0.10f * fxAmount
                if (isEffectEnabled(Effect.ROBOT)) sample *= if (((writeFrame / 18) % 2L) == 0L) 1f else -1f
                if (isEffectEnabled(Effect.RING_MOD)) sample *= sin(phase * 6.0).toFloat()
                if (isEffectEnabled(Effect.AUTO_PAN) && channelCount > 1) sample *= if (ch == 0) (1f - pan * fxAmount) else (0.5f + pan * fxAmount)
                if (isEffectEnabled(Effect.TRANSFORM) || isEffectEnabled(Effect.SLICE)) {
                    val sliceLen = max(1, (sampleRate * 0.125 * beatDivision).toInt())
                    val isSliceOn = ((writeFrame / sliceLen) % 2L) == 0L
                    if (!isSliceOn) sample *= (1f - fxAmount)
                }
                if (isEffectEnabled(Effect.NOISE)) {
                    sample += ((Math.random() * 2.0 - 1.0) * 0.12 * fxAmount).toFloat()
                }
                if (isEffectEnabled(Effect.TAPE_STOP)) {
                    sample *= exp(-0.0008 * (writeFrame % sampleRate)).toFloat()
                }

                val out = sample.coerceIn(-1f, 1f)
                val idx = ((writeFrame % delayFrames) * channelCount + ch).toInt()
                if (idx in delayLine.indices) {
                    delayLine[idx] = out
                }
                output.putShort((out * 32767.0f).roundToInt().toShort())
            }
            writeFrame = (writeFrame + 1L) % delayFrames
            phase += 2.0 * PI * 0.9 / sampleRate.toDouble()
            if (phase > 2.0 * PI) phase -= 2.0 * PI
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
        writeFrame = 0L
        phase = 0.0
        delayLine.fill(0f)
        filterState.fill(0f)
        hpState.fill(0f)
        previous.fill(0f)
    }

    override fun reset() {
        flush()
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
        sampleRate = 44_100
        channelCount = 2
        delayFrames = 1
        delayLine = FloatArray(2)
    }
}
