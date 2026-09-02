package com.example.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

data class MaqamPreset(
    val id: String,
    val name: String,
    val arabicName: String,
    val category: String, // "Scale", "Taqsim", "Song"
    val scaleType: String,
    val description: String,
    val notes: List<Pair<Int, Double>> // MIDI pitch to duration in beats
)

class MaqamPlayer {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var playbackJob: Job? = null

    var isPlaying by mutableStateOf(false)
        private set

    var currentPlayingTitle by mutableStateOf("")
        private set

    var isLooping by mutableStateOf(false)
    var selectedInstrument by mutableStateOf("Oud (عود)")
    var volume by mutableStateOf(0.85f)
    var tempoBpm by mutableStateOf(105f)

    val instruments = listOf(
        "Oud (عود)",
        "Qanun (قانون)",
        "Nay (ناي)",
        "Violin (كمان)",
        "Accordion (أكورديون)",
        "Grand Piano (بيانو)"
    )

    val presets: List<MaqamPreset> = listOf(
        // 1. Scales
        MaqamPreset(
            id = "bayati_scale",
            name = "Bayati Scale",
            arabicName = "سلم بياتي",
            category = "Scales (سلالم)",
            scaleType = "Bayati",
            description = "D4, E4(-half), F4, G4, A4, Bb4, C5, D5",
            notes = listOf(
                62 to 0.75, 63 to 0.75, 65 to 0.75, 67 to 0.75, 69 to 0.75, 70 to 0.75, 72 to 0.75, 74 to 1.5,
                72 to 0.75, 70 to 0.75, 69 to 0.75, 67 to 0.75, 65 to 0.75, 63 to 0.75, 62 to 1.8
            )
        ),
        MaqamPreset(
            id = "rast_scale",
            name = "Rast Scale",
            arabicName = "سلم راست",
            category = "Scales (سلالم)",
            scaleType = "Rast",
            description = "C4, D4, E4(-half), F4, G4, A4, B4(-half), C5",
            notes = listOf(
                60 to 0.75, 62 to 0.75, 63 to 0.75, 65 to 0.75, 67 to 0.75, 69 to 0.75, 70 to 0.75, 72 to 1.5,
                70 to 0.75, 69 to 0.75, 67 to 0.75, 65 to 0.75, 63 to 0.75, 62 to 0.75, 60 to 1.8
            )
        ),
        MaqamPreset(
            id = "hijaz_scale",
            name = "Hijaz Scale",
            arabicName = "سلم حجاز",
            category = "Scales (سلالم)",
            scaleType = "Hijaz",
            description = "D4, Eb4, F#4, G4, A4, Bb4, C5, D5",
            notes = listOf(
                62 to 0.75, 63 to 0.75, 66 to 0.75, 67 to 0.75, 69 to 0.75, 70 to 0.75, 72 to 0.75, 74 to 1.5,
                72 to 0.75, 70 to 0.75, 69 to 0.75, 67 to 0.75, 66 to 0.75, 63 to 0.75, 62 to 1.8
            )
        ),
        MaqamPreset(
            id = "nahawand_scale",
            name = "Nahawand Scale",
            arabicName = "سلم نهاوند",
            category = "Scales (سلالم)",
            scaleType = "Nahawand",
            description = "C4, D4, Eb4, F4, G4, Ab4, B4, C5",
            notes = listOf(
                60 to 0.75, 62 to 0.75, 63 to 0.75, 65 to 0.75, 67 to 0.75, 68 to 0.75, 71 to 0.75, 72 to 1.5,
                71 to 0.75, 68 to 0.75, 67 to 0.75, 65 to 0.75, 63 to 0.75, 62 to 0.75, 60 to 1.8
            )
        ),
        MaqamPreset(
            id = "kurd_scale",
            name = "Kurd Scale",
            arabicName = "سلم كرد",
            category = "Scales (سلالم)",
            scaleType = "Kurd",
            description = "D4, Eb4, F4, G4, A4, Bb4, C5, D5",
            notes = listOf(
                62 to 0.75, 63 to 0.75, 65 to 0.75, 67 to 0.75, 69 to 0.75, 70 to 0.75, 72 to 0.75, 74 to 1.5,
                72 to 0.75, 70 to 0.75, 69 to 0.75, 67 to 0.75, 65 to 0.75, 63 to 0.75, 62 to 1.8
            )
        ),
        MaqamPreset(
            id = "saba_scale",
            name = "Saba Scale",
            arabicName = "سلم صبا",
            category = "Scales (سلالم)",
            scaleType = "Saba",
            description = "D4, Eb4, F4, Gb4, A4, Bb4, C5, D5",
            notes = listOf(
                62 to 0.75, 63 to 0.75, 65 to 0.75, 66 to 0.75, 69 to 0.75, 70 to 0.75, 72 to 0.75, 74 to 1.5,
                72 to 0.75, 70 to 0.75, 69 to 0.75, 66 to 0.75, 65 to 0.75, 63 to 0.75, 62 to 1.8
            )
        ),
        MaqamPreset(
            id = "ajam_scale",
            name = "Ajam Scale",
            arabicName = "سلم عجم (Major)",
            category = "Scales (سلالم)",
            scaleType = "Ajam",
            description = "Bb3, C4, D4, Eb4, F4, G4, A4, Bb4",
            notes = listOf(
                58 to 0.75, 60 to 0.75, 62 to 0.75, 63 to 0.75, 65 to 0.75, 67 to 0.75, 69 to 0.75, 70 to 1.5,
                69 to 0.75, 67 to 0.75, 65 to 0.75, 63 to 0.75, 62 to 0.75, 60 to 0.75, 58 to 1.8
            )
        ),
        MaqamPreset(
            id = "hijazkar_scale",
            name = "Hijaz Kar Scale",
            arabicName = "سلم حجاز كار",
            category = "Scales (سلالم)",
            scaleType = "Hijaz Kar",
            description = "C4, Db4, E4, F4, G4, Ab4, B4, C5",
            notes = listOf(
                60 to 0.75, 61 to 0.75, 64 to 0.75, 65 to 0.75, 67 to 0.75, 68 to 0.75, 71 to 0.75, 72 to 1.5,
                71 to 0.75, 68 to 0.75, 67 to 0.75, 65 to 0.75, 64 to 0.75, 61 to 0.75, 60 to 1.8
            )
        ),

        // 2. Taqasim (تقاسيم حية)
        MaqamPreset(
            id = "taqsim_hijaz",
            name = "Hijaz Taqsim Solo",
            arabicName = "تقسيم حجاز حر",
            category = "Taqasim (تقاسيم)",
            scaleType = "Hijaz",
            description = "تقاسيم حجاز حية بأداء رائع",
            notes = listOf(
                62 to 1.0, 63 to 0.5, 66 to 0.5, 67 to 1.5, 66 to 0.5, 63 to 0.5, 62 to 1.5,
                66 to 0.75, 67 to 0.5, 69 to 1.0, 70 to 0.5, 69 to 0.5, 67 to 1.0, 66 to 1.0,
                63 to 0.75, 66 to 0.5, 63 to 0.5, 62 to 2.2
            )
        ),
        MaqamPreset(
            id = "taqsim_bayati",
            name = "Bayati Taqsim Solo",
            arabicName = "تقسيم بياتي حر",
            category = "Taqasim (تقاسيم)",
            scaleType = "Bayati",
            description = "عزف تقاسيم بياتي نغمية أصيلة",
            notes = listOf(
                62 to 1.0, 65 to 0.75, 67 to 1.0, 69 to 0.5, 70 to 0.5, 69 to 1.2,
                67 to 0.75, 65 to 0.5, 63 to 0.75, 65 to 0.5, 62 to 2.0,
                60 to 0.5, 62 to 0.5, 63 to 0.5, 65 to 1.0, 63 to 0.5, 62 to 2.5
            )
        ),
        MaqamPreset(
            id = "taqsim_saba",
            name = "Saba Taqsim Solo",
            arabicName = "تقسيم صبا شجي",
            category = "Taqasim (تقاسيم)",
            scaleType = "Saba",
            description = "تقاسيم صبا شجية ومؤثرة",
            notes = listOf(
                62 to 1.2, 63 to 0.6, 65 to 0.6, 66 to 1.4, 65 to 0.5, 63 to 0.5, 62 to 1.6,
                66 to 0.8, 69 to 0.8, 70 to 0.6, 69 to 0.8, 66 to 1.0, 65 to 0.6, 63 to 0.6, 62 to 2.4
            )
        ),
        MaqamPreset(
            id = "taqsim_nahawand",
            name = "Nahawand Taqsim",
            arabicName = "تقسيم نهاوند رومانسي",
            category = "Taqasim (تقاسيم)",
            scaleType = "Nahawand",
            description = "عزف نهاوند راقي وهادئ",
            notes = listOf(
                60 to 1.0, 63 to 0.75, 67 to 1.0, 68 to 0.5, 67 to 1.0, 65 to 0.75, 63 to 1.0,
                62 to 0.5, 60 to 1.0, 62 to 0.5, 63 to 0.75, 67 to 1.5, 65 to 0.5, 63 to 0.5, 60 to 2.2
            )
        ),

        // 3. Songs Presets
        MaqamPreset(
            id = "song_alf_leila",
            name = "Alf Leila Wa Leila",
            arabicName = "ألف ليلة وليلة (أم كلثوم)",
            category = "Songs (أغاني)",
            scaleType = "Kurd",
            description = "مقدمة لحن ألف ليلة وليلة الشهير",
            notes = listOf(
                62 to 0.6, 62 to 0.3, 63 to 0.3, 65 to 0.6, 67 to 0.6, 65 to 0.6, 63 to 0.6, 62 to 1.2,
                65 to 0.6, 67 to 0.6, 69 to 0.8, 70 to 0.4, 69 to 0.6, 67 to 0.6, 65 to 1.2,
                67 to 0.6, 69 to 0.6, 70 to 0.8, 72 to 0.4, 70 to 0.6, 69 to 0.6, 67 to 1.5
            )
        ),
        MaqamPreset(
            id = "song_enta_omri",
            name = "Enta Omri",
            arabicName = "إنت عمري (عبد الوهاب)",
            category = "Songs (أغاني)",
            scaleType = "Kurd",
            description = "صولو جيتار ومقدمة إنت عمري",
            notes = listOf(
                62 to 0.7, 65 to 0.7, 69 to 1.0, 69 to 0.5, 70 to 0.5, 69 to 1.2,
                67 to 0.6, 65 to 0.6, 63 to 0.6, 65 to 0.6, 62 to 1.8,
                60 to 0.5, 62 to 0.5, 63 to 0.8, 65 to 0.8, 63 to 0.5, 62 to 2.0
            )
        ),
        MaqamPreset(
            id = "song_lamma_bada",
            name = "Lamma Bada Yatathanna",
            arabicName = "لما بدا يتثنى (موشح)",
            category = "Songs (أغاني)",
            scaleType = "Nahawand",
            description = "موشح أندلسي أصيل بنغم النهاوند",
            notes = listOf(
                60 to 1.0, 63 to 1.0, 65 to 0.6, 67 to 1.2, 65 to 0.6, 63 to 0.6, 62 to 1.0, 60 to 1.5,
                67 to 0.8, 68 to 0.6, 70 to 0.6, 72 to 1.4, 71 to 0.6, 68 to 0.6, 67 to 1.8
            )
        ),
        MaqamPreset(
            id = "song_zeina",
            name = "Zeina Zeina",
            arabicName = "زينة زينة (فريد الأطرش)",
            category = "Songs (أغاني)",
            scaleType = "Bayati",
            description = "لحن زينة زينة المبهج على مقام البياتي",
            notes = listOf(
                62 to 0.5, 65 to 0.5, 67 to 0.7, 67 to 0.4, 69 to 0.4, 67 to 0.6, 65 to 0.6, 62 to 1.0,
                65 to 0.5, 67 to 0.5, 69 to 0.8, 70 to 0.4, 69 to 0.6, 67 to 0.6, 65 to 1.2
            )
        )
    )

    fun playPreset(preset: MaqamPreset) {
        stop()
        isPlaying = true
        currentPlayingTitle = preset.arabicName

        playbackJob = scope.launch {
            val sampleRate = 44100
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(8192)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            try {
                track.play()

                do {
                    val currentBpm = tempoBpm.coerceIn(50f, 200f)
                    val beatDurationSec = 60.0 / currentBpm
                    val instrument = selectedInstrument
                    val currentVol = volume.coerceIn(0f, 1f)

                    for ((pitch, beats) in preset.notes) {
                        if (!isActive || !isPlaying) break

                        val noteDuration = beats * beatDurationSec
                        val samplesCount = (sampleRate * noteDuration).toInt()
                        val freq = 440.0 * 2.0.pow((pitch - 69) / 12.0)
                        val pcmChunk = ShortArray(2048 * 2)

                        var sampleIndex = 0
                        while (sampleIndex < samplesCount && isActive && isPlaying) {
                            val framesToGenerate = (samplesCount - sampleIndex).coerceAtMost(2048)
                            for (f in 0 until framesToGenerate) {
                                val t = (sampleIndex + f).toDouble() / sampleRate
                                val progress = (sampleIndex + f).toDouble() / samplesCount
                                val env = when {
                                    progress < 0.04 -> progress / 0.04
                                    progress > 0.85 -> (1.0 - progress) / 0.15
                                    else -> exp(-progress * 1.5)
                                }

                                val wave = synthesizeWaveform(instrument, freq, t)
                                val sampleVal = (wave * env * currentVol * 30000.0).toInt().coerceIn(-32768, 32767).toShort()
                                pcmChunk[f * 2] = sampleVal
                                pcmChunk[f * 2 + 1] = sampleVal
                            }
                            track.write(pcmChunk, 0, framesToGenerate * 2)
                            sampleIndex += framesToGenerate
                        }
                    }

                    if (isLooping && isPlaying) {
                        delay(120)
                    }
                } while (isLooping && isActive && isPlaying)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (_: Throwable) {}
                withContext(Dispatchers.Main) {
                    isPlaying = false
                    currentPlayingTitle = ""
                }
            }
        }
    }

    fun stop() {
        isPlaying = false
        currentPlayingTitle = ""
        playbackJob?.cancel()
        playbackJob = null
    }

    private fun synthesizeWaveform(inst: String, freq: Double, t: Double): Double {
        val s1 = sin(2.0 * PI * freq * t)
        val s2 = sin(2.0 * PI * freq * 2.0 * t)
        val s3 = sin(2.0 * PI * freq * 3.0 * t)
        val s4 = sin(2.0 * PI * freq * 4.0 * t)
        val vibrato = 1.0 + 0.008 * sin(2.0 * PI * 5.5 * t)

        return when {
            inst.contains("Oud") || inst.contains("عود") ->
                (s1 + 0.52 * s2 + 0.22 * s3 + 0.10 * s4) * exp(-t * 2.2) * 0.95
            inst.contains("Qanun") || inst.contains("قانون") ->
                (s1 + 0.72 * s2 + 0.44 * s3 + 0.28 * s4) * exp(-t * 2.6) * 0.9
            inst.contains("Nay") || inst.contains("ناي") ->
                (s1 * vibrato + 0.25 * s2 + 0.08 * s3) * 0.9
            inst.contains("Violin") || inst.contains("كمان") ->
                (s1 * vibrato + 0.45 * s2 + 0.25 * s3 + 0.12 * s4) * 0.85
            inst.contains("Accordion") || inst.contains("أكورديون") ->
                (s1 + 0.65 * s2 + 0.35 * s3) * 0.85
            else ->
                (s1 + 0.40 * s2 + 0.20 * s3) * exp(-t * 1.8) * 0.9
        }
    }
}
