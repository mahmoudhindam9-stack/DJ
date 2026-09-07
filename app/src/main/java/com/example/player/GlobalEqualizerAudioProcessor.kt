package com.example.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * App-wide 10-band PCM EQ for every Media3/ExoPlayer audio path outside the
 * DJ decks. It follows the same band frequencies and safe gain range as the DJ EQ.
 */
class GlobalEqualizerAudioProcessor : AudioProcessor {
    companion object {
        private val FREQUENCIES = floatArrayOf(
            60f, 170f, 310f, 600f, 1000f,
            3000f, 6000f, 12000f, 14000f, 16000f
        )
        private const val Q = 1f
        private const val TRANSITION_FRAMES = 256
        private const val LIMITER_THRESHOLD = 0.82f
    }

    private fun createBank() = Array(2) { Array(10) { BiquadFilter() } }
    private var active = createBank()
    private var target = createBank()
    private var inputFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false
    private var sampleRate = 44100
    private var channelCount = 2
    private var appliedVersion = -1L
    private var transitionActive = false
    private var transitionPosition = TRANSITION_FRAMES

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (buffer.capacity() < size) {
            buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN)
        } else {
            buffer.clear()
        }
        outputBuffer = buffer
        return buffer
    }

    private fun configureBank(bank: Array<Array<BiquadFilter>>, levels: FloatArray) {
        for (ch in 0 until channelCount) {
            for (i in 0 until 10) {
                bank[ch][i].setPeakingEQ(
                    FREQUENCIES[i],
                    levels.getOrElse(i) { 0f },
                    Q,
                    sampleRate.toFloat()
                )
                bank[ch][i].resetState()
            }
        }
    }

    private fun syncGlobalState() {
        val version = GlobalEqualizerState.version
        if (version == appliedVersion) return
        configureBank(target, GlobalEqualizerState.levelsDb)
        transitionPosition = 0
        transitionActive = true
        appliedVersion = version
    }

    private fun applyEq(sample: Float, ch: Int, amount: Float): Float {
        var current = sample
        var next = sample
        for (i in 0 until 10) {
            current = active[ch][i].process(current)
            next = target[ch][i].process(next)
        }
        return current * (1f - amount) + next * amount
    }

    private fun preampGain(): Float =
        Math.pow(10.0, GlobalEqualizerState.preampDb.toDouble() / 20.0).toFloat()

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
        for (bank in arrayOf(active, target)) {
            for (ch in 0 until channelCount) {
                for (i in 0 until 10) {
                    bank[ch][i].setPeakingEQ(FREQUENCIES[i], 0f, Q, sampleRate.toFloat())
                    bank[ch][i].resetState()
                }
            }
        }
        appliedVersion = -1L
        transitionActive = false
        transitionPosition = TRANSITION_FRAMES
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

        if (!GlobalEqualizerState.enabled) {
            val output = replaceOutputBuffer(bytes)
            output.put(inputBuffer)
            output.flip()
            return
        }

        syncGlobalState()

        val output = replaceOutputBuffer(bytes)
        val frames = bytes / (2 * channelCount)
        val gain = preampGain()

        for (frame in 0 until frames) {
            val amount = if (transitionActive) {
                ((transitionPosition + 1).toFloat() / TRANSITION_FRAMES.toFloat()).coerceIn(0f, 1f)
            } else 1f

            for (ch in 0 until channelCount) {
                if (!inputBuffer.hasRemaining()) break
                var sample = inputBuffer.short.toFloat() / 32768f
                sample = applyEq(sample, ch, amount)
                sample = softLimit(sample * gain).coerceIn(-1f, 1f)
                output.putShort((sample * 32767f).roundToInt().toShort())
            }

            if (transitionActive) {
                transitionPosition++
                if (transitionPosition >= TRANSITION_FRAMES) {
                    val old = active
                    active = target
                    target = old
                    transitionActive = false
                    transitionPosition = TRANSITION_FRAMES
                }
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
        for (bank in arrayOf(active, target)) {
            for (ch in 0 until channelCount) {
                for (i in 0 until 10) bank[ch][i].resetState()
            }
        }
        appliedVersion = -1L
        transitionActive = false
        transitionPosition = TRANSITION_FRAMES
    }

    override fun reset() {
        flush()
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
        sampleRate = 44100
        channelCount = 2
    }
}
