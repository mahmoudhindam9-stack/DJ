package com.example.visualizer

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

@OptIn(UnstableApi::class)
class AudioVisualizerEngine {

    val audioProcessor = VisualizerAudioProcessor(this)

    private val _state = MutableStateFlow(AudioVisualizerState.EMPTY)
    val state: StateFlow<AudioVisualizerState> = _state.asStateFlow()

    @Volatile
    var isPlaying: Boolean = false
        set(value) {
            field = value
            if (value) startAnalysisLoop()
        }

    private val isEngineActive = AtomicBoolean(true)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var analysisJob: Job? = null

    // Circular PCM buffer (Power of 2 for fast bitwise masking)
    private val ringBufferSize = 4096
    private val ringBufferMask = ringBufferSize - 1
    private val ringBuffer = FloatArray(ringBufferSize)
    private val writePos = AtomicInteger(0)

    // Pre-allocated analysis buffers (Zero allocations in hot loop)
    private val fftSize = 512
    private val fftHalf = fftSize / 2
    private val rawSamples = FloatArray(fftSize)
    private val fftReal = FloatArray(fftSize)
    private val fftImag = FloatArray(fftSize)
    private val magnitudes = FloatArray(fftHalf)
    private val rawBands = FloatArray(AudioVisualizerState.BAND_COUNT)

    // Pre-calculated Hann window
    private val hannWindow = FloatArray(fftSize) { i ->
        (0.5 * (1.0 - cos(2.0 * Math.PI * i / (fftSize - 1)))).toFloat()
    }

    // Pre-calculated Bit-reversal indices for 512-point FFT
    private val bitReversal = IntArray(fftSize) { i ->
        var rev = 0
        var temp = i
        for (b in 0 until 9) { // 2^9 = 512
            rev = (rev shl 1) or (temp and 1)
            temp = temp shr 1
        }
        rev
    }

    // Pre-calculated Twiddle factors
    private val cosTwiddle = FloatArray(fftSize / 2) { k ->
        cos(-2.0 * Math.PI * k / fftSize).toFloat()
    }
    private val sinTwiddle = FloatArray(fftSize / 2) { k ->
        sin(-2.0 * Math.PI * k / fftSize).toFloat()
    }

    // Band bin boundaries (32 logarithmically distributed bands between bin 1 and 255)
    private val bandStartBins = IntArray(AudioVisualizerState.BAND_COUNT)
    private val bandEndBins = IntArray(AudioVisualizerState.BAND_COUNT)

    // Smoothed state accumulators
    private val smoothedBands = FloatArray(AudioVisualizerState.BAND_COUNT)
    private val smoothedWave = FloatArray(AudioVisualizerState.WAVE_COUNT)
    private var smoothedBass = 0f
    private var smoothedMid = 0f
    private var smoothedTreble = 0f
    private var smoothedEnergy = 0f
    private var smoothedPeak = 0f
    private var smoothedBeat = 0f
    private var previousEnergy = 0f

    @Volatile
    private var lastAudioFeedTimestamp = 0L

    init {
        initBandBoundaries()
    }

    private fun initBandBoundaries() {
        // Map 255 FFT bins into 32 bands logarithmically
        val minBin = 1.0
        val maxBin = (fftHalf - 1).toDouble() // 255
        val logMin = ln(minBin)
        val logMax = ln(maxBin)
        val bandCount = AudioVisualizerState.BAND_COUNT

        for (i in 0 until bandCount) {
            val startRatio = i.toDouble() / bandCount
            val endRatio = (i + 1).toDouble() / bandCount

            var start = exp(logMin + startRatio * (logMax - logMin)).roundToInt()
            var end = exp(logMin + endRatio * (logMax - logMin)).roundToInt()

            if (start < 1) start = 1
            if (end <= start) end = start + 1
            if (end > fftHalf) end = fftHalf

            bandStartBins[i] = start
            bandEndBins[i] = end
        }
    }

    /**
     * Called from the audio processing thread (ExoPlayer AudioSink).
     * Must be non-blocking and allocate zero memory.
     */
    fun feedPcm(buffer: ByteBuffer, channelCount: Int, sampleRate: Int, isFloat: Boolean = false) {
        if (!isEngineActive.get()) return

        lastAudioFeedTimestamp = System.currentTimeMillis()
        var currentWrite = writePos.get()

        if (isFloat) {
            val floatBuffer = buffer.asFloatBuffer()
            while (floatBuffer.remaining() >= channelCount) {
                var sum = 0f
                for (ch in 0 until channelCount) {
                    sum += floatBuffer.get()
                }
                val mono = sum / channelCount
                ringBuffer[currentWrite] = mono
                currentWrite = (currentWrite + 1) and ringBufferMask
            }
        } else {
            val shortBuffer = buffer.asShortBuffer()
            while (shortBuffer.remaining() >= channelCount) {
                var sum = 0f
                for (ch in 0 until channelCount) {
                    sum += shortBuffer.get()
                }
                val mono = (sum / channelCount) / 32768.0f
                ringBuffer[currentWrite] = mono
                currentWrite = (currentWrite + 1) and ringBufferMask
            }
        }
        writePos.set(currentWrite)
    }

    private fun startAnalysisLoop() {
        if (analysisJob?.isActive == true) return
        analysisJob = scope.launch {
            while (isActive && isEngineActive.get()) {
                val now = System.currentTimeMillis()
                val hasRecentAudio = (now - lastAudioFeedTimestamp) < 300L && isPlaying

                if (hasRecentAudio) {
                    processFrame()
                    delay(22L) // ~45 FPS update rate for fluid animation
                } else {
                    // Decay smoothly to zero when playback is paused or stopped
                    if (decayToZero()) {
                        delay(22L)
                    } else {
                        // Fully decayed, sleep longer until new audio arrives
                        delay(100L)
                    }
                }
            }
        }
    }

    /**
     * Read recent samples from the ring buffer and perform FFT & state computation.
     */
    internal fun processFrame() {
        val head = writePos.get()

        // Read last 512 samples
        for (i in 0 until fftSize) {
            val idx = (head - fftSize + i) and ringBufferMask
            rawSamples[i] = ringBuffer[idx]
        }

        // 1. Calculate Peak and RMS Energy from raw samples
        var peakVal = 0f
        var sumSquares = 0.0
        for (i in 0 until fftSize) {
            val s = rawSamples[i]
            val absS = abs(s)
            if (absS > peakVal) peakVal = absS
            sumSquares += (s * s)
        }
        val rmsEnergy = (sqrt(sumSquares / fftSize) * 2.2f).coerceIn(0.0, 1.0).toFloat()

        // 2. Waveform points (downsample to 64 points)
        val step = fftSize / AudioVisualizerState.WAVE_COUNT
        for (i in 0 until AudioVisualizerState.WAVE_COUNT) {
            val targetWave = rawSamples[i * step].coerceIn(-1f, 1f)
            val prevWave = smoothedWave[i]
            // Light smoothing on waveform
            smoothedWave[i] = prevWave + (targetWave - prevWave) * 0.75f
        }

        // 3. Apply Hann window and bit-reversal reordering
        for (i in 0 until fftSize) {
            val rev = bitReversal[i]
            fftReal[i] = rawSamples[rev] * hannWindow[rev]
            fftImag[i] = 0f
        }

        // 4. In-Place Radix-2 Cooley-Tukey FFT
        var subLen = 2
        while (subLen <= fftSize) {
            val halfSub = subLen / 2
            val twiddleStep = fftSize / subLen
            var i = 0
            while (i < fftSize) {
                var k = 0
                for (j in 0 until halfSub) {
                    val twIdx = j * twiddleStep
                    val c = cosTwiddle[twIdx]
                    val s = sinTwiddle[twIdx]

                    val tr = c * fftReal[i + j + halfSub] - s * fftImag[i + j + halfSub]
                    val ti = s * fftReal[i + j + halfSub] + c * fftImag[i + j + halfSub]

                    fftReal[i + j + halfSub] = fftReal[i + j] - tr
                    fftImag[i + j + halfSub] = fftImag[i + j] - ti
                    fftReal[i + j] += tr
                    fftImag[i + j] += ti
                }
                i += subLen
            }
            subLen = subLen shl 1
        }

        // 5. Compute Magnitudes for first half (0 until 256)
        for (i in 0 until fftHalf) {
            val r = fftReal[i]
            val im = fftImag[i]
            magnitudes[i] = sqrt(r * r + im * im) / (fftSize / 2f)
        }

        // 6. Aggregate into 32 Frequency Bands with attack/decay
        for (bandIdx in 0 until AudioVisualizerState.BAND_COUNT) {
            val start = bandStartBins[bandIdx]
            val end = bandEndBins[bandIdx]
            var sum = 0f
            val count = (end - start).coerceAtLeast(1)
            for (bin in start until end) {
                sum += magnitudes[bin]
            }
            val avgMag = sum / count

            // Convert to dB scale with perceptual floor (-54dB to 0dB)
            val db = 20f * log10(avgMag.coerceAtLeast(0.002f))
            val normalized = ((db + 54f) / 54f).coerceIn(0f, 1f)

            // Perceptual frequency weighting: slight boost to lower/mid bands
            val weight = if (bandIdx < 8) 1.25f else if (bandIdx < 20) 1.15f else 1.0f
            val weighted = (normalized * weight).coerceIn(0f, 1f)

            rawBands[bandIdx] = weighted

            val prev = smoothedBands[bandIdx]
            if (weighted > prev) {
                // Fast attack
                smoothedBands[bandIdx] = prev + (weighted - prev) * 0.65f
            } else {
                // Smooth decay
                smoothedBands[bandIdx] = prev * 0.84f
            }
        }

        // 7. Aggregate Bass, Mid, Treble
        var bassSum = 0f
        for (i in 0 until 6) bassSum += smoothedBands[i]
        val targetBass = (bassSum / 6f).coerceIn(0f, 1f)

        var midSum = 0f
        for (i in 6 until 20) midSum += smoothedBands[i]
        val targetMid = (midSum / 14f).coerceIn(0f, 1f)

        var trebleSum = 0f
        for (i in 20 until AudioVisualizerState.BAND_COUNT) trebleSum += smoothedBands[i]
        val targetTreble = (trebleSum / 12f).coerceIn(0f, 1f)

        // Smooth metrics
        val energyDelta = (rmsEnergy - previousEnergy).coerceAtLeast(0f)
        previousEnergy = rmsEnergy
        val beatTarget = ((energyDelta * 7f) + (targetBass * 0.35f)).coerceIn(0f, 1f)

        smoothedBass = smoothedBass + (targetBass - smoothedBass) * 0.6f
        smoothedMid = smoothedMid + (targetMid - smoothedMid) * 0.6f
        smoothedTreble = smoothedTreble + (targetTreble - smoothedTreble) * 0.6f
        smoothedEnergy = smoothedEnergy + (rmsEnergy - smoothedEnergy) * 0.6f
        smoothedPeak = if (peakVal > smoothedPeak) peakVal else smoothedPeak * 0.88f
        smoothedBeat = max(beatTarget, smoothedBeat * 0.72f)

        // Emit immutable state snapshot
        _state.value = AudioVisualizerState(
            bass = smoothedBass,
            mid = smoothedMid,
            treble = smoothedTreble,
            energy = smoothedEnergy,
            peak = smoothedPeak,
            beat = smoothedBeat,
            bands = smoothedBands.copyOf(),
            wave = smoothedWave.copyOf(),
            isPlaying = isPlaying
        )
    }

    /**
     * Decays current state towards zero when paused or silence.
     * Returns true if non-zero values remain, false if fully zeroed.
     */
    internal fun decayToZero(): Boolean {
        var hasActiveValues = false
        val decayFactor = 0.80f

        for (i in 0 until AudioVisualizerState.BAND_COUNT) {
            smoothedBands[i] *= decayFactor
            if (smoothedBands[i] > 0.005f) hasActiveValues = true else smoothedBands[i] = 0f
        }
        for (i in 0 until AudioVisualizerState.WAVE_COUNT) {
            smoothedWave[i] *= decayFactor
            if (abs(smoothedWave[i]) > 0.005f) hasActiveValues = true else smoothedWave[i] = 0f
        }

        smoothedBass *= decayFactor
        smoothedMid *= decayFactor
        smoothedTreble *= decayFactor
        smoothedEnergy *= decayFactor
        smoothedPeak *= decayFactor
        smoothedBeat *= 0.68f
        previousEnergy *= decayFactor

        if (smoothedBass > 0.005f || smoothedEnergy > 0.005f || smoothedBeat > 0.005f) hasActiveValues = true

        _state.value = AudioVisualizerState(
            bass = if (smoothedBass > 0.005f) smoothedBass else 0f,
            mid = if (smoothedMid > 0.005f) smoothedMid else 0f,
            treble = if (smoothedTreble > 0.005f) smoothedTreble else 0f,
            energy = if (smoothedEnergy > 0.005f) smoothedEnergy else 0f,
            peak = if (smoothedPeak > 0.005f) smoothedPeak else 0f,
            beat = if (smoothedBeat > 0.005f) smoothedBeat else 0f,
            bands = smoothedBands.copyOf(),
            wave = smoothedWave.copyOf(),
            isPlaying = isPlaying
        )

        return hasActiveValues
    }

    fun release() {
        isEngineActive.set(false)
        analysisJob?.cancel()
        scope.cancel()
        _state.value = AudioVisualizerState.EMPTY
    }

    /**
     * Media3 AudioProcessor that taps into the main player PCM stream transparently.
     */
    class VisualizerAudioProcessor(private val engine: AudioVisualizerEngine) : AudioProcessor {
        private var inputFormat = AudioProcessor.AudioFormat.NOT_SET
        private var outputBuffer = AudioProcessor.EMPTY_BUFFER
        private var inputEnded = false
        private var channelCount = 2
        private var sampleRate = 44_100
        private var isFloat = false

        override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
            val encoding = inputAudioFormat.encoding
            if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
                inputFormat = AudioProcessor.AudioFormat.NOT_SET
                return AudioProcessor.AudioFormat.NOT_SET
            }
            if (inputAudioFormat.sampleRate <= 0 || inputAudioFormat.channelCount !in 1..2) {
                inputFormat = AudioProcessor.AudioFormat.NOT_SET
                return AudioProcessor.AudioFormat.NOT_SET
            }
            inputFormat = inputAudioFormat
            channelCount = inputAudioFormat.channelCount
            sampleRate = inputAudioFormat.sampleRate
            isFloat = (encoding == C.ENCODING_PCM_FLOAT)
            return inputAudioFormat
        }

        override fun isActive(): Boolean = inputFormat != AudioProcessor.AudioFormat.NOT_SET

        override fun queueInput(inputBuffer: ByteBuffer) {
            val remaining = inputBuffer.remaining()
            if (remaining <= 0 || !isActive()) return

            // Feed samples to engine using a duplicate buffer (preserves inputBuffer position)
            engine.feedPcm(inputBuffer.duplicate(), channelCount, sampleRate, isFloat)

            // Direct pass-through to output buffer
            val output = replaceOutputBuffer(remaining)
            output.put(inputBuffer)
            output.flip()
        }

        private fun replaceOutputBuffer(size: Int): ByteBuffer {
            if (outputBuffer.capacity() < size) {
                outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
            } else {
                outputBuffer.clear()
            }
            return outputBuffer
        }

        override fun queueEndOfStream() {
            inputEnded = true
        }

        override fun getOutput(): ByteBuffer {
            val out = outputBuffer
            outputBuffer = AudioProcessor.EMPTY_BUFFER
            return out
        }

        override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

        override fun flush() {
            outputBuffer = AudioProcessor.EMPTY_BUFFER
            inputEnded = false
        }

        override fun reset() {
            flush()
            inputFormat = AudioProcessor.AudioFormat.NOT_SET
            channelCount = 2
            sampleRate = 44_100
            isFloat = false
        }
    }
}
