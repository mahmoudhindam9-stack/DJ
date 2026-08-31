package com.example.org

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

class OrgEngine(private val context: Context) {
    data class Voice(
        val name: String,
        val category: String,
        val baseHz: Double,
        val harmonic: Double,
        val character: Character
    )

    enum class Character { ORGAN, OUD, QANUN, NAY, VIOLIN, BRASS, SAX, FLUTE, PIANO, SYNTH, BASS, MALLET }

    data class Rhythm(val name: String, val bpm: Int, val steps: IntArray)

    val voices = listOf(
        // Western bank
        Voice("Grand Piano", "Western • Keys", 261.63, 0.30, Character.PIANO),
        Voice("Stage Piano", "Western • Keys", 220.00, 0.22, Character.PIANO),
        Voice("Hammond B3", "Western • Organ", 174.61, 0.63, Character.ORGAN),
        Voice("Jazz Organ", "Western • Organ", 196.00, 0.48, Character.ORGAN),
        Voice("Strings", "Western • Strings", 261.63, 0.26, Character.VIOLIN),
        Voice("Solo Violin", "Western • Strings", 293.66, 0.38, Character.VIOLIN),
        Voice("Trumpet", "Western • Brass", 196.00, 0.58, Character.BRASS),
        Voice("Sax", "Western • Wind", 233.08, 0.68, Character.SAX),
        Voice("Flute", "Western • Wind", 329.63, 0.20, Character.FLUTE),
        Voice("Acoustic Bass", "Western • Bass", 110.00, 0.52, Character.BASS),
        Voice("Synth Lead", "Western • Synth", 261.63, 0.74, Character.SYNTH),
        Voice("Synth Pad", "Western • Synth", 174.61, 0.48, Character.SYNTH),
        Voice("Electric Guitar", "Western • Strings", 196.00, 0.36, Character.PIANO),
        Voice("Mallet", "Western • Percussion", 392.00, 0.24, Character.MALLET),
        // Oriental / Arabic bank
        Voice("Oud", "Oriental • Strings", 220.00, 0.70, Character.OUD),
        Voice("Oud Warm", "Oriental • Strings", 196.00, 0.54, Character.OUD),
        Voice("Qanun", "Oriental • Plucked", 329.63, 0.64, Character.QANUN),
        Voice("Nay", "Oriental • Wind", 293.66, 0.28, Character.NAY),
        Voice("Arabic Violin", "Oriental • Strings", 246.94, 0.50, Character.VIOLIN),
        Voice("Mizmar", "Oriental • Wind", 220.00, 0.76, Character.SAX),
        Voice("Accordion Oriental", "Oriental • Keys", 293.66, 0.46, Character.ORGAN),
        Voice("Darbuka Hit", "Oriental • Percussion", 92.50, 0.08, Character.MALLET),
        Voice("Riqq", "Oriental • Percussion", 130.81, 0.05, Character.MALLET),
        Voice("Deep Duh", "Oriental • Percussion", 65.41, 0.02, Character.BASS)
    )

    val rhythms = listOf(
        Rhythm("Maqsum", 105, intArrayOf(0,1,2,1,0,2,1,3)),
        Rhythm("Saeidi", 112, intArrayOf(0,2,1,3,0,2,1,1)),
        Rhythm("Malfouf", 126, intArrayOf(0,2,1,2,0,2,3,2)),
        Rhythm("Baladi", 96, intArrayOf(0,1,1,2,0,1,3,1)),
        Rhythm("Khaleeji", 110, intArrayOf(0,1,2,0,3,1,2,1)),
        Rhythm("Darbuka", 118, intArrayOf(0,1,2,0,1,2,3,1)),
        Rhythm("Oriental Pop", 120, intArrayOf(0,1,0,2,0,1,3,1)),
        Rhythm("House", 124, intArrayOf(0,2,1,2,0,2,1,2)),
        Rhythm("Disco", 116, intArrayOf(0,1,2,1,0,1,2,3)),
        Rhythm("Ballad", 78, intArrayOf(0,0,2,0,1,0,2,0))
    )

    val effects = listOf("Clean", "Reverb", "Echo", "Chorus", "Delay", "Distortion", "Tremolo", "Octave")

    private var rhythmJob: Job? = null
    var rhythm: Rhythm by mutableStateOf(rhythms.first())
        private set
    var bpm: Int by mutableStateOf(rhythm.bpm)
        private set
    var volume: Float by mutableStateOf(0.8f)
    var voiceIndex: Int by mutableStateOf(0)
    var accompanimentEnabled: Boolean by mutableStateOf(false)
    var rhythmEnabled: Boolean by mutableStateOf(false)
    var effectIndex: Int by mutableStateOf(0)

    fun setRhythm(index: Int) {
        rhythm = rhythms[index.coerceIn(rhythms.indices)]
        bpm = rhythm.bpm
    }

    fun updateBpm(value: Int) {
        bpm = value.coerceIn(50, 180)
    }

    fun selectVoice(index: Int) {
        voiceIndex = index.coerceIn(voices.indices)
    }

    fun selectEffect(index: Int) {
        effectIndex = index.coerceIn(effects.indices)
    }

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

    fun stopRhythm() {
        rhythmEnabled = false
        rhythmJob?.cancel()
        rhythmJob = null
    }

    fun triggerPad(pad: Int) {
        val freq = doubleArrayOf(130.81, 164.81, 196.00, 220.00, 261.63, 329.63, 392.00, 523.25)[pad.coerceIn(0, 7)]
        playTone(freq, 0.30, 0.72f, 0.18, 5)
    }

    fun triggerVoice() {
        val voice = voices[voiceIndex]
        playVoice(voice, volume, 0.80)
    }

    private fun playVoice(voice: Voice, amp: Float, duration: Double) {
        Thread {
            val sr = 44100
            val count = max(1, (sr * duration).toInt())
            val data = ShortArray(count)
            for (i in data.indices) {
                val t = i.toDouble() / sr
                val p = i.toDouble() / count
                val env = when {
                    p < 0.015 -> p / 0.015
                    voice.character == Character.PIANO -> exp(-p * 6.0)
                    voice.character == Character.OUD || voice.character == Character.QANUN -> exp(-p * 4.0)
                    voice.character == Character.BRASS || voice.character == Character.SAX -> exp(-p * 2.2)
                    else -> exp(-p * 3.2)
                }
                val base = voice.baseHz
                val wave = when (voice.character) {
                    Character.PIANO -> sin(2.0 * PI * base * t) + voice.harmonic * sin(2.0 * PI * base * 2.01 * t) + 0.18 * sin(2.0 * PI * base * 3.0 * t)
                    Character.ORGAN -> sin(2.0 * PI * base * t) + voice.harmonic * sin(2.0 * PI * base * 2.0 * t) + 0.35 * sin(2.0 * PI * base * 4.0 * t)
                    Character.OUD -> sin(2.0 * PI * base * t) + voice.harmonic * sin(2.0 * PI * base * 2.0 * t) + 0.30 * sin(2.0 * PI * base * 3.0 * t) + 0.13 * sin(2.0 * PI * base * 5.0 * t)
                    Character.QANUN -> sin(2.0 * PI * base * t) + voice.harmonic * sin(2.0 * PI * base * 2.0 * t) + 0.30 * sin(2.0 * PI * base * 4.0 * t) + 0.12 * sin(2.0 * PI * base * 7.0 * t)
                    Character.NAY -> sin(2.0 * PI * base * t) + 0.32 * sin(2.0 * PI * base * 2.0 * t) + 0.08 * sin(2.0 * PI * base * 3.0 * t)
                    Character.VIOLIN -> sin(2.0 * PI * base * t) + voice.harmonic * sin(2.0 * PI * base * 2.0 * t) + 0.34 * sin(2.0 * PI * base * 3.0 * t)
                    Character.BRASS -> tanh(sin(2.0 * PI * base * t) * 1.55) + 0.22 * sin(2.0 * PI * base * 2.0 * t)
                    Character.SAX -> tanh((sin(2.0 * PI * base * t) + 0.28 * sin(2.0 * PI * base * 2.0 * t)) * 1.2) + 0.10 * sin(2.0 * PI * base * 3.0 * t)
                    Character.FLUTE -> sin(2.0 * PI * base * t) + 0.12 * sin(2.0 * PI * base * 2.0 * t)
                    Character.SYNTH -> {
                        val ph = (t * base) % 1.0
                        (2.0 * ph - 1.0) * 0.75 + voice.harmonic * sin(2.0 * PI * base * t)
                    }
                    Character.BASS -> sin(2.0 * PI * base * t) + 0.44 * sin(2.0 * PI * base * 2.0 * t)
                    Character.MALLET -> sin(2.0 * PI * base * t) + 0.22 * sin(2.0 * PI * base * 2.6 * t) + 0.10 * sin(2.0 * PI * base * 5.1 * t)
                }
                val effected = applyEffect(wave, t, sr.toDouble())
                data[i] = (effected * 10500.0 * amp * env)
                    .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(data, sr)
        }.start()
    }

    private fun applyEffect(sample: Double, time: Double, sr: Double): Double {
        return when (effectIndex) {
            0 -> sample
            1 -> sample * (0.88 + 0.12 * sin(2.0 * PI * 2.3 * time))
            2 -> sample + sample * 0.30 * sin(2.0 * PI * 3.7 * time)
            3 -> sample + sample * 0.22 * sin(2.0 * PI * 0.7 * time)
            4 -> sample + sample * 0.18 * sin(2.0 * PI * 5.0 * time)
            5 -> tanh(sample * 1.7)
            6 -> sample * (0.70 + 0.30 * (0.5 + 0.5 * sin(2.0 * PI * 5.0 * time)))
            else -> sample + 0.35 * sin(2.0 * PI * 2.0 * time)
        }
    }

    private fun playTone(freq: Double, duration: Double, amp: Float, harmonic: Double, effect: Int) {
        val selectedEffect = effect.coerceIn(effects.indices)
        Thread {
            val sr = 44100
            val count = max(1, (sr * duration).toInt())
            val data = ShortArray(count)
            for (i in data.indices) {
                val t = i.toDouble() / sr
                val p = i.toDouble() / count
                val env = when {
                    p < 0.015 -> p / 0.015
                    else -> exp(-p * 4.0)
                }
                val wave = sin(2.0 * PI * freq * t) + harmonic * sin(2.0 * PI * freq * 2.0 * t)
                val effected = when (selectedEffect) {
                    0 -> wave
                    1 -> wave * (0.88 + 0.12 * sin(2.0 * PI * 2.3 * t))
                    2 -> wave + wave * 0.30 * sin(2.0 * PI * 3.7 * t)
                    3 -> wave + wave * 0.22 * sin(2.0 * PI * 0.7 * t)
                    4 -> wave + wave * 0.18 * sin(2.0 * PI * 5.0 * t)
                    5 -> tanh(wave * 1.7)
                    6 -> wave * (0.70 + 0.30 * (0.5 + 0.5 * sin(2.0 * PI * 5.0 * t)))
                    else -> wave + 0.35 * sin(2.0 * PI * 2.0 * t)
                }
                data[i] = (effected * 10500.0 * amp * env).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcm(data, sr)
        }.start()
    }

    private fun playAccompaniment(index: Int) {
        val roots = doubleArrayOf(130.81, 146.83, 164.81, 174.61, 196.00, 220.00, 233.08, 261.63)
        val root = roots[index % roots.size]
        playTone(root, 0.33, volume * 0.36f, 0.20, effectIndex)
        playTone(root * 1.25, 0.26, volume * 0.20f, 0.12, effectIndex)
        playTone(root * 1.50, 0.22, volume * 0.16f, 0.10, effectIndex)
    }

    private fun playPercussion(kind: Int) {
        when (kind) {
            0 -> noise(0.08, 0.85, 110.0)
            1 -> toneClick(110.0, 0.12, 0.75f)
            2 -> noise(0.16, 0.55, 2800.0)
            else -> toneClick(65.4, 0.20, 0.72f)
        }
    }

    private fun toneClick(freq: Double, duration: Double, amp: Float) = playTone(freq, duration, amp, 0.08, effectIndex)

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
            track.write(samples, 0, samples.size)
            track.play()
            Thread.sleep((samples.size * 1000L / sampleRate) + 35L)
        } finally {
            track.release()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
class OrgOutputRecorder(private val context: Context) {
    var isRecording: Boolean by mutableStateOf(false)
        private set
    var lastFile: File? by mutableStateOf(null)
        private set
    private var projection: MediaProjection? = null
    private var recordJob: Job? = null

    fun start(captureProjection: MediaProjection, scope: CoroutineScope) {
        stop()
        projection = captureProjection
        val dir = File(context.getExternalFilesDir(null), "recordings").apply { mkdirs() }
        val file = File(dir, "ORG_${System.currentTimeMillis()}.wav")
        lastFile = file
        isRecording = true
        recordJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 48000
            val channelMask = AudioFormat.CHANNEL_IN_STEREO
            val config = android.media.AudioPlaybackCaptureConfiguration.Builder(captureProjection).addMatchingUsage(AudioAttributes.USAGE_MEDIA).build()
            val min = AudioRecord.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
            val recorder = AudioRecord.Builder()
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(channelMask).build())
                .setBufferSizeInBytes(max(min * 2, sampleRate))
                .setAudioPlaybackCaptureConfig(config)
                .build()
            val out = FileOutputStream(file)
            var totalBytes = 0L
            val buffer = ShortArray(sampleRate / 2)
            writeWavHeader(out)
            try {
                recorder.startRecording()
                while (isRecording) {
                    val n = recorder.read(buffer, 0, buffer.size)
                    if (n > 0) {
                        val bytes = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until n) bytes.putShort(buffer[i])
                        out.write(bytes.array())
                        totalBytes += n * 2L
                    }
                }
            } finally {
                try { recorder.stop() } catch (_: Exception) { }
                recorder.release()
                out.close()
                RandomAccessWav.patchHeader(file, totalBytes, sampleRate, 2, 16)
                captureProjection.stop()
                this@OrgOutputRecorder.projection = null
            }
        }
    }

    fun stop() {
        if (!isRecording && recordJob == null) return
        isRecording = false
        recordJob?.cancel()
        recordJob = null
        projection?.stop()
        projection = null
    }

    private fun writeWavHeader(out: FileOutputStream) = out.write(ByteArray(44))
}

private object RandomAccessWav {
    fun patchHeader(file: File, dataLength: Long, sampleRate: Int, channels: Int, bits: Int) {
        java.io.RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.writeBytes("RIFF")
            writeLE32(raf, (36L + dataLength).toInt())
            raf.writeBytes("WAVEfmt ")
            writeLE32(raf, 16)
            writeLE16(raf, 1)
            writeLE16(raf, channels)
            writeLE32(raf, sampleRate)
            writeLE32(raf, sampleRate * channels * bits / 8)
            writeLE16(raf, channels * bits / 8)
            writeLE16(raf, bits)
            raf.writeBytes("data")
            writeLE32(raf, dataLength.toInt())
        }
    }
    private fun writeLE16(raf: java.io.RandomAccessFile, v: Int) { raf.write(v and 255); raf.write((v ushr 8) and 255) }
    private fun writeLE32(raf: java.io.RandomAccessFile, v: Int) { for (s in 0..3) raf.write((v ushr (8 * s)) and 255) }
}
