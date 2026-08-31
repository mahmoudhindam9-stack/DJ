package com.example.org

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class OrgEngine(private val context: Context) {
    data class Voice(val name: String, val category: String, val baseHz: Double, val harmonic: Double)
    data class Rhythm(val name: String, val bpm: Int, val steps: IntArray)

    val voices = listOf(
        Voice("Grand Organ", "Organ", 261.63, 0.52), Voice("Warm Organ", "Organ", 220.0, 0.34),
        Voice("Accordion", "Accordion", 293.66, 0.44), Voice("Hammond", "Organ", 174.61, 0.63),
        Voice("Strings", "Orchestral", 261.63, 0.26), Voice("Brass", "Orchestral", 196.00, 0.56),
        Voice("Sax", "Wind", 233.08, 0.68), Voice("Flute", "Wind", 329.63, 0.20),
        Voice("Synth Lead", "Synth", 261.63, 0.74), Voice("Synth Pad", "Synth", 174.61, 0.48),
        Voice("Guitar", "Keys/Strings", 196.00, 0.36), Voice("Mallet", "Percussion", 392.00, 0.24)
    )

    val rhythms = listOf(
        Rhythm("Oriental", 100, intArrayOf(0,1,0,2,0,1,0,3)),
        Rhythm("Maqsum", 105, intArrayOf(0,1,2,1,0,2,1,3)),
        Rhythm("Saeidi", 112, intArrayOf(0,2,1,3,0,2,1,1)),
        Rhythm("Darbuka", 118, intArrayOf(0,1,2,0,1,2,3,1)),
        Rhythm("Pop", 120, intArrayOf(0,1,0,1,2,1,0,1)),
        Rhythm("House", 124, intArrayOf(0,2,1,2,0,2,1,2)),
        Rhythm("Disco", 116, intArrayOf(0,1,2,1,0,1,2,3)),
        Rhythm("Ballad", 78, intArrayOf(0,0,2,0,1,0,2,0))
    )

    private var rhythmJob: Job? = null
    var rhythm: Rhythm = rhythms.first(); private set
    var bpm: Int = rhythm.bpm; private set
    var volume: Float = 0.8f
    var voiceIndex: Int = 0
    var accompanimentEnabled: Boolean = false
    var rhythmEnabled: Boolean = false

    fun setRhythm(index: Int) { rhythm = rhythms[index.coerceIn(rhythms.indices)]; bpm = rhythm.bpm }
    fun setBpm(value: Int) { bpm = value.coerceIn(50, 180) }
    fun selectVoice(index: Int) { voiceIndex = index.coerceIn(voices.indices) }

    fun startRhythm(scope: CoroutineScope) {
        rhythmJob?.cancel()
        rhythmEnabled = true
        rhythmJob = scope.launch(Dispatchers.Default) {
            var step = 0
            while (isActive && rhythmEnabled) {
                playPercussion(rhythm.steps[step % rhythm.steps.size])
                if (accompanimentEnabled && step % 2 == 0) playAccompaniment(step / 2)
                step++
                delay((60000L / bpm).coerceAtLeast(50L) / 2L)
            }
        }
    }

    fun stopRhythm() { rhythmEnabled = false; rhythmJob?.cancel(); rhythmJob = null }

    fun triggerPad(pad: Int) {
        val freq = doubleArrayOf(130.81, 164.81, 196.00, 220.00, 261.63, 329.63, 392.00, 523.25)[pad.coerceIn(0, 7)]
        playTone(freq, 0.30, 0.72, 0.18)
    }

    fun triggerVoice() {
        val voice = voices[voiceIndex]
        playTone(voice.baseHz, 0.75, volume, voice.harmonic)
    }

    private fun playAccompaniment(index: Int) {
        val roots = doubleArrayOf(130.81, 146.83, 164.81, 174.61, 196.00, 220.00, 233.08, 261.63)
        val root = roots[index % roots.size]
        playTone(root, 0.33, volume * 0.36f, 0.20)
        playTone(root * 1.25, 0.26, volume * 0.20f, 0.12)
        playTone(root * 1.50, 0.22, volume * 0.16f, 0.10)
    }

    private fun playPercussion(kind: Int) {
        when (kind) {
            0 -> noise(0.08, 0.85, 110.0)
            1 -> toneClick(110.0, 0.12, 0.75)
            2 -> noise(0.16, 0.55, 2800.0)
            else -> toneClick(65.4, 0.20, 0.72)
        }
    }

    private fun playTone(freq: Double, duration: Double, amp: Float, harmonic: Double) {
        Thread {
            val sr = 44100
            val count = max(1, (sr * duration).toInt())
            val data = ShortArray(count)
            for (i in data.indices) {
                val t = i.toDouble() / sr
                val p = i.toDouble() / count
                val env = if (p < 0.02) p / 0.02 else exp(-p * 3.0)
                val s = sin(2.0 * PI * freq * t) + harmonic * sin(2.0 * PI * freq * 2.0 * t) + harmonic * 0.42 * sin(2.0 * PI * freq * 3.0 * t)
                data[i] = (s * 12000.0 * amp * env).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(data, sr)
        }.start()
    }

    private fun toneClick(freq: Double, duration: Double, amp: Float) = playTone(freq, duration, amp, 0.08)

    private fun noise(duration: Double, amp: Double, centerHz: Double) {
        Thread {
            val sr = 44100
            val count = max(1, (sr * duration).toInt())
            val data = ShortArray(count)
            var phase = 0.0
            val step = 2.0 * PI * centerHz / sr
            for (i in data.indices) {
                val p = i.toDouble() / count
                val env = exp(-p * 6.0)
                phase += step
                val n = (Random.nextDouble() * 2.0 - 1.0) * 0.72 + sin(phase) * 0.28
                data[i] = (n * 12000.0 * amp * env).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(data, sr)
        }.start()
    }

    private fun playPcm(samples: ShortArray, sampleRate: Int) {
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(max(minBuffer, samples.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(samples, 0, samples.size); track.play(); Thread.sleep((samples.size * 1000L / sampleRate) + 35L)
        } finally { track.release() }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
class OrgOutputRecorder(private val context: Context) {
    var isRecording: Boolean = false
        private set
    var lastFile: File? = null
        private set

    private var projection: MediaProjection? = null
    private var recordJob: Job? = null

    fun start(projection: MediaProjection, scope: CoroutineScope) {
        stop()
        this.projection = projection
        val dir = File(context.getExternalFilesDir(null), "recordings").apply { mkdirs() }
        val file = File(dir, "ORG_${System.currentTimeMillis()}.wav")
        lastFile = file
        isRecording = true
        recordJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 48000
            val channelMask = AudioFormat.CHANNEL_IN_STEREO
            val config = android.media.AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build()
            val min = AudioRecord.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
            val recorder = AudioRecord.Builder()
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(channelMask).build())
                .setBufferSizeInBytes(max(min * 2, sampleRate))
                .setAudioPlaybackCaptureConfig(config)
                .build()
            val out = FileOutputStream(file)
            var totalBytes = 0L
            val buffer = ShortArray(sampleRate / 2)
            writeWavHeader(out, sampleRate, 2, 16, 0)
            try {
                recorder.startRecording()
                while (isRecording) {
                    val n = recorder.read(buffer, 0, buffer.size)
                    if (n > 0) {
                        val bytes = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until n) bytes.putShort(buffer[i])
                        out.write(bytes.array()); totalBytes += n * 2L
                    }
                }
            } finally {
                try { recorder.stop() } catch (_: Exception) { }
                recorder.release()
                out.close()
                RandomAccessWav.patchHeader(file, totalBytes, sampleRate, 2, 16)
                projection.stop()
                this@OrgOutputRecorder.projection = null
            }
        }
    }

    fun stop() { if (!isRecording && recordJob == null) return; isRecording = false; recordJob?.cancel(); recordJob = null; projection?.stop(); projection = null }

    private fun writeWavHeader(out: FileOutputStream, sampleRate: Int, channels: Int, bits: Int, dataLength: Long) {
        out.write(ByteArray(44))
    }
}

private object RandomAccessWav {
    fun patchHeader(file: File, dataLength: Long, sampleRate: Int, channels: Int, bits: Int) {
        java.io.RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.writeBytes("RIFF")
            writeLE32(raf, (36L + dataLength).toInt())
            raf.writeBytes("WAVEfmt ")
            writeLE32(raf, 16); writeLE16(raf, 1); writeLE16(raf, channels)
            writeLE32(raf, sampleRate); writeLE32(raf, sampleRate * channels * bits / 8)
            writeLE16(raf, channels * bits / 8); writeLE16(raf, bits)
            raf.writeBytes("data"); writeLE32(raf, dataLength.toInt())
        }
    }
    private fun writeLE16(raf: java.io.RandomAccessFile, v: Int) { raf.write(v and 255); raf.write((v ushr 8) and 255) }
    private fun writeLE32(raf: java.io.RandomAccessFile, v: Int) { for (s in 0..3) raf.write((v ushr (8 * s)) and 255) }
}
