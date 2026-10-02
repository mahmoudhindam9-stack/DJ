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

    // 1024-point FFT gives ~43 Hz bins at 44.1 kHz and makes kick detection much more reliable.
    private val ringBufferSize = 8192
    private val ringBufferMask = ringBufferSize - 1
    private val ringBuffer = FloatArray(ringBufferSize)
    private val writePos = AtomicInteger(0)

    private val fftSize = 1024
    private val fftHalf = fftSize / 2
    private val rawSamples = FloatArray(fftSize)
    private val fftReal = FloatArray(fftSize)
    private val fftImag = FloatArray(fftSize)
    private val magnitudes = FloatArray(fftHalf)
    private val previousMagnitudes = FloatArray(fftHalf)
    private val rawBands = FloatArray(AudioVisualizerState.BAND_COUNT)

    private val hannWindow = FloatArray(fftSize) { i ->
        (0.5 * (1.0 - cos(2.0 * Math.PI * i / (fftSize - 1)))).toFloat()
    }

    private val bitReversal = IntArray(fftSize) { i ->
        var rev = 0
        var temp = i
        for (b in 0 until 10) {
            rev = (rev shl 1) or (temp and 1)
            temp = temp shr 1
        }
        rev
    }

    private val cosTwiddle = FloatArray(fftSize / 2) { k ->
        cos(-2.0 * Math.PI * k / fftSize).toFloat()
    }
    private val sinTwiddle = FloatArray(fftSize / 2) { k ->
        sin(-2.0 * Math.PI * k / fftSize).toFloat()
    }

    private val bandStartBins = IntArray(AudioVisualizerState.BAND_COUNT)
    private val bandEndBins = IntArray(AudioVisualizerState.BAND_COUNT)

    private val smoothedBands = FloatArray(AudioVisualizerState.BAND_COUNT)
    private val smoothedWave = FloatArray(AudioVisualizerState.WAVE_COUNT)
    private var smoothedBass = 0f
    private var smoothedMid = 0f
    private var smoothedTreble = 0f
    private var smoothedEnergy = 0f
    private var smoothedPeak = 0f
    private var smoothedBeat = 0f

    private var previousEnergy = 0f
    private var previousBass = 0f
    private var fluxBaseline = 0f
    private var lastBeatTimestamp = 0L

    @Volatile
    private var inputSampleRate = 44_100

    @Volatile
    private var lastAudioFeedTimestamp = 0L

    init { initBandBoundaries() }

    private fun initBandBoundaries() {
        val minBin = 1.0
        val maxBin = (fftHalf - 1).toDouble()
        val logMin = ln(minBin)
        val logMax = ln(maxBin)
        for (i in 0 until AudioVisualizerState.BAND_COUNT) {
            val startRatio = i.toDouble() / AudioVisualizerState.BAND_COUNT
            val endRatio = (i + 1).toDouble() / AudioVisualizerState.BAND_COUNT
            var start = exp(logMin + startRatio * (logMax - logMin)).roundToInt()
            var end = exp(logMin + endRatio * (logMax - logMin)).roundToInt()
            start = start.coerceIn(1, fftHalf - 1)
            end = end.coerceIn(start + 1, fftHalf)
            bandStartBins[i] = start
            bandEndBins[i] = end
        }
    }

    fun feedPcm(buffer: ByteBuffer, channelCount: Int, sampleRate: Int, isFloat: Boolean = false) {
        if (!isEngineActive.get()) return
        inputSampleRate = sampleRate.coerceAtLeast(8_000)
        lastAudioFeedTimestamp = System.currentTimeMillis()
        var currentWrite = writePos.get()
        if (isFloat) {
            val floatBuffer = buffer.asFloatBuffer()
            while (floatBuffer.remaining() >= channelCount) {
                var sum = 0f
                for (ch in 0 until channelCount) sum += floatBuffer.get()
                ringBuffer[currentWrite] = sum / channelCount
                currentWrite = (currentWrite + 1) and ringBufferMask
            }
        } else {
            val shortBuffer = buffer.asShortBuffer()
            while (shortBuffer.remaining() >= channelCount) {
                var sum = 0f
                for (ch in 0 until channelCount) sum += shortBuffer.get()
                ringBuffer[currentWrite] = (sum / channelCount) / 32768.0f
                currentWrite = (currentWrite + 1) and ringBufferMask
            }
        }
        writePos.set(currentWrite)
    }

    private fun startAnalysisLoop() {
        if (analysisJob?.isActive == true) return
        analysisJob = scope.launch {
            while (isActive && isEngineActive.get()) {
                val recent = System.currentTimeMillis() - lastAudioFeedTimestamp < 300L && isPlaying
                if (recent) {
                    processFrame()
                    delay(22L)
                } else {
                    if (decayToZero()) delay(22L) else delay(100L)
                }
            }
        }
    }

    internal fun processFrame() {
        val head = writePos.get()
        for (i in 0 until fftSize) rawSamples[i] = ringBuffer[(head - fftSize + i) and ringBufferMask]

        var peakVal = 0f
        var sumSquares = 0.0
        for (sample in rawSamples) {
            val a = abs(sample)
            if (a > peakVal) peakVal = a
            sumSquares += sample * sample
        }
        val rmsEnergy = (sqrt(sumSquares / fftSize) * 2.6f).coerceIn(0.0, 1.0).toFloat()

        val waveStep = fftSize / AudioVisualizerState.WAVE_COUNT
        for (i in 0 until AudioVisualizerState.WAVE_COUNT) {
            val target = rawSamples[i * waveStep].coerceIn(-1f, 1f)
            smoothedWave[i] += (target - smoothedWave[i]) * 0.82f
        }

        for (i in 0 until fftSize) {
            val rev = bitReversal[i]
            fftReal[i] = rawSamples[rev] * hannWindow[rev]
            fftImag[i] = 0f
        }

        var subLen = 2
        while (subLen <= fftSize) {
            val halfSub = subLen / 2
            val twiddleStep = fftSize / subLen
            var block = 0
            while (block < fftSize) {
                for (j in 0 until halfSub) {
                    val twIdx = j * twiddleStep
                    val c = cosTwiddle[twIdx]
                    val s = sinTwiddle[twIdx]
                    val right = block + j + halfSub
                    val left = block + j
                    val tr = c * fftReal[right] - s * fftImag[right]
                    val ti = s * fftReal[right] + c * fftImag[right]
                    fftReal[right] = fftReal[left] - tr
                    fftImag[right] = fftImag[left] - ti
                    fftReal[left] += tr
                    fftImag[left] += ti
                }
                block += subLen
            }
            subLen = subLen shl 1
        }

        for (i in 0 until fftHalf) {
            val r = fftReal[i]
            val im = fftImag[i]
            magnitudes[i] = sqrt(r * r + im * im) / (fftSize / 2f)
        }

        // Spectral flux reacts to newly arriving frequency energy instead of raw loudness.
        var fluxSum = 0f
        for (i in 1 until fftHalf) {
            val delta = magnitudes[i] - previousMagnitudes[i]
            if (delta > 0f) fluxSum += delta
            previousMagnitudes[i] = magnitudes[i]
        }
        val flux = (fluxSum / fftHalf * 42f).coerceIn(0f, 1f)
        fluxBaseline += (flux - fluxBaseline) * 0.045f
        val fluxNovelty = ((flux - fluxBaseline) * 8.5f).coerceIn(0f, 1f)

        for (bandIdx in 0 until AudioVisualizerState.BAND_COUNT) {
            val start = bandStartBins[bandIdx]
            val end = bandEndBins[bandIdx]
            var sum = 0f
            for (bin in start until end) sum += magnitudes[bin]
            val avgMag = sum / (end - start).coerceAtLeast(1)
            val db = 20f * log10(avgMag.coerceAtLeast(0.0015f))
            val normalized = ((db + 56f) / 56f).coerceIn(0f, 1f)
            val weight = when {
                bandIdx < 7 -> 1.22f
                bandIdx < 20 -> 1.10f
                else -> 1.0f
            }
            val weighted = (normalized * weight).coerceIn(0f, 1f)
            rawBands[bandIdx] = weighted
            val previous = smoothedBands[bandIdx]
            smoothedBands[bandIdx] = if (weighted > previous) {
                previous + (weighted - previous) * 0.72f
            } else {
                previous * 0.82f
            }
        }

        var bassSum = 0f
        for (i in 0 until 7) bassSum += smoothedBands[i]
        val targetBass = (bassSum / 7f).coerceIn(0f, 1f)
        var midSum = 0f
        for (i in 7 until 20) midSum += smoothedBands[i]
        val targetMid = (midSum / 13f).coerceIn(0f, 1f)
        var trebleSum = 0f
        for (i in 20 until AudioVisualizerState.BAND_COUNT) trebleSum += smoothedBands[i]
        val targetTreble = (trebleSum / 12f).coerceIn(0f, 1f)

        // Kick-focused energy, roughly 35-160 Hz.
        val hzPerBin = inputSampleRate.toFloat() / fftSize
        val kickStart = max(1, floor(35f / hzPerBin).toInt())
        val kickEnd = min(fftHalf - 1, ceil(160f / hzPerBin).toInt())
        var kickSum = 0f
        var kickCount = 0
        for (bin in kickStart..kickEnd) {
            kickSum += magnitudes[bin]
            kickCount++
        }
        val kickEnergy = (kickSum / kickCount.coerceAtLeast(1) * 14f).coerceIn(0f, 1f)
        val bassTransient = ((targetBass - previousBass) * 7f).coerceIn(0f, 1f)
        val energyTransient = ((rmsEnergy - previousEnergy) * 4f).coerceIn(0f, 1f)
        previousBass = targetBass
        previousEnergy = rmsEnergy

        val beatScore = (fluxNovelty * 0.52f + bassTransient * 0.23f + kickEnergy * 0.17f + energyTransient * 0.08f).coerceIn(0f, 1f)
        val now = System.currentTimeMillis()
        val detectedBeat = if (beatScore > 0.20f && now - lastBeatTimestamp >= 95L) {
            lastBeatTimestamp = now
            beatScore
        } else 0f

        smoothedBass += (targetBass - smoothedBass) * 0.58f
        smoothedMid += (targetMid - smoothedMid) * 0.58f
        smoothedTreble += (targetTreble - smoothedTreble) * 0.58f
        smoothedEnergy += (rmsEnergy - smoothedEnergy) * 0.52f
        smoothedPeak = if (peakVal > smoothedPeak) peakVal else smoothedPeak * 0.88f
        smoothedBeat = max(detectedBeat, smoothedBeat * 0.76f)

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

    internal fun decayToZero(): Boolean {
        var active = false
        for (i in 0 until AudioVisualizerState.BAND_COUNT) {
            smoothedBands[i] *= 0.80f
            if (smoothedBands[i] > 0.005f) active = true else smoothedBands[i] = 0f
        }
        for (i in 0 until AudioVisualizerState.WAVE_COUNT) {
            smoothedWave[i] *= 0.80f
            if (abs(smoothedWave[i]) > 0.005f) active = true else smoothedWave[i] = 0f
        }
        for (i in previousMagnitudes.indices) previousMagnitudes[i] *= 0.72f
        smoothedBass *= 0.80f
        smoothedMid *= 0.80f
        smoothedTreble *= 0.80f
        smoothedEnergy *= 0.80f
        smoothedPeak *= 0.80f
        smoothedBeat *= 0.66f
        previousEnergy *= 0.80f
        previousBass *= 0.80f
        fluxBaseline *= 0.92f
        if (smoothedBass > 0.005f || smoothedEnergy > 0.005f || smoothedBeat > 0.005f) active = true

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
        return active
    }

    fun release() {
        isEngineActive.set(false)
        analysisJob?.cancel()
        scope.cancel()
        _state.value = AudioVisualizerState.EMPTY
    }

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
            isFloat = encoding == C.ENCODING_PCM_FLOAT
            return inputAudioFormat
        }

        override fun isActive(): Boolean = inputFormat != AudioProcessor.AudioFormat.NOT_SET

        override fun queueInput(inputBuffer: ByteBuffer) {
            val remaining = inputBuffer.remaining()
            if (remaining <= 0 || !isActive()) return
            engine.feedPcm(inputBuffer.duplicate(), channelCount, sampleRate, isFloat)
            val output = replaceOutputBuffer(remaining)
            output.put(inputBuffer)
            output.flip()
        }

        private fun replaceOutputBuffer(size: Int): ByteBuffer {
            if (outputBuffer.capacity() < size) outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder()) else outputBuffer.clear()
            return outputBuffer
        }

        override fun queueEndOfStream() { inputEnded = true }
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
