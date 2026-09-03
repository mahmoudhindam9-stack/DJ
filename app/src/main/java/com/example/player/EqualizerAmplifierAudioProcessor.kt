package com.example.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.tanh

/**
 * Post-EQ digital make-up gain with a soft limiter.
 * Disabled mode is a byte-for-byte PCM pass-through.
 */
class EqualizerAmplifierAudioProcessor : AudioProcessor {
    companion object {
        private const val DEFAULT_PREAMP_DB = 6.0f
        private const val LIMITER_THRESHOLD = 0.82f
    }

    @Volatile private var enabled = false
    @Volatile private var preampDb = DEFAULT_PREAMP_DB

    private var inputFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun setPreampDb(value: Float) {
        preampDb = value.coerceIn(0f, 12f)
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (buffer.capacity() < size) {
            buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN)
        } else {
            buffer.clear()
        }
        outputBuffer = buffer
        return buffer
    }

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

        if (!enabled) {
            val output = replaceOutputBuffer(bytes)
            output.put(inputBuffer)
            output.flip()
            return
        }

        val output = replaceOutputBuffer(bytes)
        val gain = Math.pow(10.0, preampDb.toDouble() / 20.0).toFloat()
        while (inputBuffer.remaining() >= 2) {
            val sample = inputBuffer.short.toFloat() / 32768f
            val boosted = sample * gain
            val limited = softLimit(boosted)
            output.putShort((limited.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        }
        output.flip()
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
    }

    override fun reset() {
        flush()
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
        enabled = false
        preampDb = DEFAULT_PREAMP_DB
    }
}
