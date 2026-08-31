package com.example.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * Real-time PCM effects for each DJ deck. Effects are processed inside the
 * Media3 audio sink, so the Deck FX buttons change the actual playback audio.
 */
class DeckFxAudioProcessor : AudioProcessor {
    @Volatile var flangerEnabled: Boolean = false
    @Volatile var reverbEnabled: Boolean = false
    @Volatile var echoEnabled: Boolean = false
    @Volatile var crushEnabled: Boolean = false

    private var inputFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false
    private var sampleRate = 44_100
    private var channelCount = 2
    private var delayFrames = 1
    private var delayLine = FloatArray(2)
    private var writeFrame = 0L

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
        val output = ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val frames = bytes / (2 * channelCount)

        repeat(frames) {
            for (ch in 0 until channelCount) {
                val input = inputBuffer.short.toFloat() / Short.MAX_VALUE.toFloat()
                val idx = ((writeFrame % delayFrames) * channelCount + ch).toInt()

                val echoDelay = max(1, (sampleRate * 0.24).toInt())
                val reverb1 = max(1, (sampleRate * 0.045).toInt())
                val reverb2 = max(1, (sampleRate * 0.085).toInt())
                val flangerMin = max(1, (sampleRate * 0.001).toInt())
                val flangerDepth = max(1, (sampleRate * 0.004).toInt())

                fun delayed(framesBack: Int): Float {
                    val frame = (writeFrame - framesBack).let { if (it < 0) it + delayFrames else it }
                    val p = ((frame % delayFrames) * channelCount + ch).toInt()
                    return delayLine[p]
                }

                var sample = input

                if (flangerEnabled) {
                    val lfo = (sin(2.0 * PI * (writeFrame.toDouble() / sampleRate) * 0.35) + 1.0) * 0.5
                    val d = flangerMin + (flangerDepth * lfo).roundToInt()
                    sample += delayed(d) * 0.42f
                }

                if (echoEnabled) {
                    sample += delayed(echoDelay) * 0.38f
                }

                if (reverbEnabled) {
                    sample += delayed(reverb1) * 0.22f
                    sample += delayed(reverb2) * 0.13f
                }

                if (crushEnabled) {
                    val steps = 32f
                    sample = (sample * steps).roundToInt() / steps
                }

                sample = (sample * 0.98f).coerceIn(-1f, 1f)
                delayLine[idx] = sample
                output.putShort((sample * Short.MAX_VALUE).roundToInt().toShort())
            }
            writeFrame = (writeFrame + 1L) % delayFrames
        }

        output.flip()
        outputBuffer = output
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

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
        delayLine.fill(0f)
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
