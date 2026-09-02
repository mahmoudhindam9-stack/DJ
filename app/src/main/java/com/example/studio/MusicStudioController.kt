package com.example.studio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Lightweight, high-performance in-app arranger/sequencer and music workstation.
 * Includes multi-track piano roll, chords, melody/rhythm banks, live Darbuka pads,
 * and a full Studio Master FX Rack (Reverb, Delay, Filter, Warmth).
 */
class MusicStudioController(private val context: Context) {
    data class StudioNote(var pitch: Int, var startBeat: Float, var lengthBeats: Float = 1f, var velocity: Float = 0.9f)
    data class StudioTrack(
        val id: Int,
        var name: String,
        var instrument: String,
        var volume: Float = 0.9f,
        var muted: Boolean = false,
        var solo: Boolean = false,
        val notes: MutableList<StudioNote> = mutableListOf()
    )
    data class MelodyPreset(val name: String, val notes: List<Int>)
    data class RhythmPreset(val name: String, val pattern: IntArray)

    val instruments = listOf(
        // Oriental (شرقي)
        "Oud (عود)", "Qanun (قانون)", "Nay (ناي)", "Arabic Violin (كمان شرقي)",
        "Mizmar (مزمار)", "Bouzouki (بزق)", "Rebab (ربابة)", "Oriental Accordion (أكورديون شرقي)",
        "Kanun Tremolo (قانون ترملو)", "Oriental Clarinet (كلارنيت شرقي)", "Oriental Synth (سينث شرقي)",
        // Occidental (غربي)
        "Grand Piano (بيانو)", "Electric Piano Rhodes (رودز)", "Acoustic Guitar (جيتار أكوستيك)",
        "Electric Guitar (جيتار كهربائي)", "Bass Guitar (باس)", "Violin Ensemble (وتريات)",
        "Cello (شيلو)", "Harp (هارب)", "Accordion (أكورديون)", "Saxophone (ساكسفون)",
        "Flute (فلوت)", "Synth Brass (سينث براس)", "Synth Pad (سينث باد)", "Church Organ (أورغن)",
        "808 Synth Bass (808 باس)"
    )

    val scales = listOf(
        // Maqamat (مقامات شرقية)
        "Bayati", "Rast", "Hijaz", "Nahawand", "Kurd", "Saba", "Sikah", "Ajam",
        "Hijaz Kar", "Jiharkah", "Suznak",
        // Occidental Scales (سلم غربي)
        "Major", "Minor", "Harmonic Minor", "Melodic Minor", "Pentatonic Major",
        "Pentatonic Minor", "Blues", "Dorian", "Phrygian"
    )

    val keys = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    val rhythms = listOf(
        RhythmPreset("Maqsum (مقسوم)", intArrayOf(1, 0, 2, 0, 1, 0, 2, 3)),
        RhythmPreset("Baladi (بلدي)", intArrayOf(1, 0, 0, 2, 1, 0, 2, 0)),
        RhythmPreset("Saeidi (صعيدي)", intArrayOf(1, 0, 2, 1, 1, 0, 2, 1)),
        RhythmPreset("Malfouf (ملفوف)", intArrayOf(1, 3, 2, 3, 1, 3, 2, 3)),
        RhythmPreset("Zafeh (زفة)", intArrayOf(1, 1, 2, 0, 1, 2, 2, 0)),
        RhythmPreset("Khaleeji (خليجي)", intArrayOf(1, 2, 3, 2, 1, 2, 3, 2)),
        RhythmPreset("Chobi (جوبي)", intArrayOf(1, 0, 1, 2, 1, 0, 2, 3)),
        RhythmPreset("Ayoub (أيوب / زار)", intArrayOf(1, 2, 1, 2, 1, 2, 1, 2)),
        RhythmPreset("Dabke (دبكة)", intArrayOf(1, 1, 2, 3, 1, 2, 2, 3)),
        RhythmPreset("Rumba Oriental (رومبا)", intArrayOf(1, 0, 2, 3, 0, 2, 1, 3)),
        RhythmPreset("Pop Beat (بوب)", intArrayOf(1, 0, 2, 0, 1, 0, 2, 0)),
        RhythmPreset("House Beat (هاوس)", intArrayOf(1, 3, 2, 3, 1, 3, 2, 3)),
        RhythmPreset("HipHop 808 (هيب هوب)", intArrayOf(1, 0, 0, 2, 0, 1, 2, 0)),
        RhythmPreset("Waltz 3/4 (فالس)", intArrayOf(1, 0, 0, 2, 0, 0, 2, 0))
    )

    val melodyPresets = listOf(
        MelodyPreset("Alf Leila (ألف ليلة وليلة)", listOf(0, 2, 3, 5, 7, 8, 7, 5, 3, 2, 0)),
        MelodyPreset("Inta Omri (إنت عمري)", listOf(0, 1, 3, 5, 7, 5, 3, 1, 0)),
        MelodyPreset("Lamma Bada (لما بدا)", listOf(0, 2, 3, 5, 4, 3, 2, 0, 2, 3)),
        MelodyPreset("Zeyna (زينة)", listOf(0, 1, 3, 4, 3, 1, 0, -2, 0)),
        MelodyPreset("Nour El Ain (نور العين)", listOf(0, 1, 3, 5, 3, 1, 0, 7, 5, 3)),
        MelodyPreset("3 Daqat (3 دقات)", listOf(0, 2, 4, 5, 7, 9, 7, 5, 4, 2, 0)),
        MelodyPreset("Hijaz Taqsim (تقسيم حجاز)", listOf(0, 1, 4, 5, 7, 8, 7, 5, 4, 1, 0)),
        MelodyPreset("Bayati Taqsim (تقسيم بياتي)", listOf(0, 1, 3, 5, 7, 5, 3, 1, 0, -2, 0)),
        MelodyPreset("Rast Taqsim (تقسيم راست)", listOf(0, 2, 3, 5, 7, 8, 10, 8, 7, 5, 3, 2, 0)),
        MelodyPreset("Saeidi Mizmar (مزمار صعيدي)", listOf(0, 3, 4, 7, 8, 7, 4, 3, 0)),
        MelodyPreset("Für Elise (فير إيليس)", listOf(7, 6, 7, 6, 7, 2, 5, 3, 0)),
        MelodyPreset("Turkish March (مارش تركي)", listOf(2, 1, 0, 2, 4, 3, 2, 4, 5)),
        MelodyPreset("Spanish Romance (جيتار)", listOf(7, 7, 7, 7, 5, 3, 3, 2, 0)),
        MelodyPreset("Blues Shuffle (بلوز)", listOf(0, 3, 5, 6, 7, 10, 12, 10, 7))
    )

    val tracks = mutableStateListOf(
        StudioTrack(0, "Lead (صولو)", "Oud (عود)"),
        StudioTrack(1, "Harmony (هارموني)", "Qanun (قانون)"),
        StudioTrack(2, "Strings (وتريات)", "Arabic Violin (كمان شرقي)"),
        StudioTrack(3, "Bass (باس)", "Bass Guitar (باس)")
    )

    var bpm by mutableStateOf(112)
    var bars by mutableStateOf(4)
    var selectedTrackId by mutableStateOf(0)
    var selectedScale by mutableStateOf("Hijaz")
    var selectedKey by mutableStateOf("D")
    var selectedRhythmIndex by mutableStateOf(0)
    var loopEnabled by mutableStateOf(true)
    var isPlaying by mutableStateOf(false)
    var playheadBeat by mutableStateOf(0f)

    // Studio Master Effects
    var reverbAmount by mutableStateOf(0.25f)
    var delayAmount by mutableStateOf(0.20f)
    var filterCutoff by mutableStateOf(1.0f) // 1.0 = fully open (no LP)
    var warmthDrive by mutableStateOf(0.15f)

    // STUDIO_FUNCTIONALITY_V1
    // Forces Compose refresh after edits to nested mutable track data.
    var uiRevision by mutableStateOf(0)
        private set

    fun bumpUi() { uiRevision++ }

    private var playbackJob: Job? = null
    private val prefs = context.getSharedPreferences("music_studio_project", Context.MODE_PRIVATE)
    private val sampleRate = 44100

    // High performance lookup table for fast sine wave synthesis
    private val SINE_TABLE_SIZE = 4096
    private val sineTable = FloatArray(SINE_TABLE_SIZE) { i ->
        sin(2.0 * PI * i / SINE_TABLE_SIZE).toFloat()
    }

    private inline fun fastSin(phase: Double): Float {
        val normalized = (phase / (2.0 * PI)) % 1.0
        val p = if (normalized < 0) normalized + 1.0 else normalized
        val idx = (p * SINE_TABLE_SIZE).toInt().coerceIn(0, SINE_TABLE_SIZE - 1)
        return sineTable[idx]
    }

    // Studio FX Delay lines
    private val delayBufferL = FloatArray(44100)
    private val delayBufferR = FloatArray(44100)
    private var delayWritePos = 0
    private var filterStateL = 0f
    private var filterStateR = 0f

    init {
        loadProject()
    }

    val selectedTrack: StudioTrack get() = tracks.firstOrNull { it.id == selectedTrackId } ?: tracks.first()
    val selectedRhythm: RhythmPreset get() = rhythms.getOrElse(selectedRhythmIndex) { rhythms.first() }
    val loopBeats: Float get() = bars.coerceIn(1, 32) * 4f

    private var previewTrack: AudioTrack? = null

    fun playNotePreview(pitch: Int) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val freq = midiToHz(pitch)
                val durSec = 0.25
                val samples = (sampleRate * durSec).toInt()
                val pcm = ShortArray(samples * 2)
                val inst = selectedTrack.instrument

                for (i in 0 until samples) {
                    val t = i.toDouble() / sampleRate
                    val progress = i.toDouble() / samples
                    val env = exp(-progress * 4.5).toFloat()
                    val wave = fastWaveform(inst, freq, t)
                    val s = (wave * env * 18000.0f).toInt().coerceIn(-32768, 32767).toShort()
                    pcm[i * 2] = s
                    pcm[i * 2 + 1] = s
                }

                synchronized(this@MusicStudioController) {
                    try { previewTrack?.stop() } catch (_: Throwable) {}
                    try { previewTrack?.release() } catch (_: Throwable) {}

                    val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(pcm.size * 2)
                    previewTrack = AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                        .setBufferSizeInBytes(minBuf)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                    previewTrack?.write(pcm, 0, pcm.size)
                    previewTrack?.play()
                }
            } catch (_: Throwable) {}
        }
    }

    fun addNote(pitch: Int, beat: Float, length: Float = 1f) {
        selectedTrack.notes.removeAll { it.pitch == pitch && abs(it.startBeat - beat) < 0.01f }
        selectedTrack.notes += StudioNote(pitch, beat.coerceIn(0f, loopBeats - 0.25f), length.coerceIn(0.25f, 4f))
        playNotePreview(pitch)
        bumpUi()
    }

    fun removeNote(pitch: Int, beat: Float) {
        selectedTrack.notes.removeAll { it.pitch == pitch && abs(it.startBeat - beat) < 0.26f }
        bumpUi()
    }

    fun addChord(rootPitch: Int, beat: Float) {
        val intervals = intArrayOf(0, 4, 7)
        intervals.forEach { addNote(rootPitch + it, beat, 1f) }
    }

    fun clearTrack() {
        selectedTrack.notes.clear()
        bumpUi()
    }

    fun duplicateTrack() {
        val nextId = (tracks.maxOfOrNull { it.id } ?: 0) + 1
        val source = selectedTrack
        val copy = StudioTrack(nextId, "${source.name} Copy", source.instrument, source.volume)
        source.notes.forEach { copy.notes += it.copy() }
        tracks += copy
        selectedTrackId = nextId
        bumpUi()
    }

    fun addTrack() {
        val nextId = (tracks.maxOfOrNull { it.id } ?: 0) + 1
        tracks += StudioTrack(nextId, "Track ${tracks.size + 1}", instruments[nextId % instruments.size])
        selectedTrackId = nextId
        bumpUi()
    }

    fun deleteTrack() {
        if (tracks.size <= 1) return
        val index = tracks.indexOfFirst { it.id == selectedTrackId }
        if (index >= 0) tracks.removeAt(index)
        selectedTrackId = tracks.getOrNull((index - 1).coerceAtLeast(0))?.id ?: tracks.first().id
        bumpUi()
    }

    fun setTrackInstrument(instrument: String) { selectedTrack.instrument = instrument; bumpUi() }
    fun setTrackVolume(value: Float) { selectedTrack.volume = value.coerceIn(0f, 1f); bumpUi() }
    fun toggleTrackMute() { selectedTrack.muted = !selectedTrack.muted; bumpUi() }
    fun toggleTrackSolo() { selectedTrack.solo = !selectedTrack.solo; bumpUi() }

    fun applyMelodyPreset(index: Int) {
        val preset = melodyPresets[index.coerceIn(melodyPresets.indices)]
        selectedTrack.notes.clear()
        val root = keyBaseMidi()
        preset.notes.forEachIndexed { i, degree ->
            val pitch = root + scaleOffset(degree)
            selectedTrack.notes += StudioNote(pitch, i.toFloat().coerceAtMost(loopBeats - 1f), 0.75f)
        }
        bumpUi()
    }

    fun applyChordProgression() {
        val root = keyBaseMidi()
        val degrees = intArrayOf(0, 3, 4, 0)
        selectedTrack.notes.clear()
        degrees.forEachIndexed { bar, degree ->
            val base = root + scaleOffset(degree)
            addChord(base, bar * 4f)
        }
        bumpUi()
    }

    fun startPlayback(scope: CoroutineScope) {
        if (isPlaying) return
        isPlaying = true
        playbackJob?.cancel()

        playbackJob = scope.launch(Dispatchers.Default) {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(16384)

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
                var currentBeat = 0.0
                val frameChunk = 2048
                val buffer = ShortArray(frameChunk * 2)
                val beatStep = (frameChunk.toDouble() / sampleRate) * (bpm / 60.0)

                // Reset FX state
                delayBufferL.fill(0f)
                delayBufferR.fill(0f)
                filterStateL = 0f
                filterStateR = 0f

                while (isActive && isPlaying) {
                    playheadBeat = currentBeat.toFloat()
                    synthChunkFast(buffer, currentBeat, frameChunk)
                    track.write(buffer, 0, buffer.size)

                    currentBeat += beatStep
                    if (currentBeat >= loopBeats) {
                        if (loopEnabled) {
                            currentBeat %= loopBeats
                        } else {
                            withContext(Dispatchers.Main) {
                                isPlaying = false
                                playheadBeat = 0f
                            }
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (_: Throwable) {}
                withContext(Dispatchers.Main) {
                    isPlaying = false
                    playheadBeat = 0f
                }
            }
        }
    }

    fun stopPlayback() {
        isPlaying = false
        playheadBeat = 0f
        playbackJob?.cancel()
        playbackJob = null
    }

    private fun synthChunkFast(buffer: ShortArray, beatNow: Double, frames: Int) {
        val soloExists = tracks.any { it.solo }
        val beatPerFrame = (bpm / 60.0) / sampleRate

        // Pre-filter active tracks
        val activeTracks = tracks.filter { !it.muted && (!soloExists || it.solo) }

        // Local FX params
        val rev = reverbAmount.coerceIn(0f, 1f)
        val dly = delayAmount.coerceIn(0f, 1f)
        val dlyFrames = (sampleRate * 0.25).toInt().coerceIn(1, 44000)
        val cutoff = filterCutoff.coerceIn(0.05f, 1f)
        val alpha = (0.02f + 0.96f * cutoff)
        val drive = (1f + warmthDrive * 2f)

        for (f in 0 until frames) {
            val beat = (beatNow + f * beatPerFrame) % loopBeats
            var left = 0f
            var right = 0f

            for (tr in activeTracks) {
                val inst = tr.instrument
                val trVol = tr.volume

                for (note in tr.notes) {
                    val start = note.startBeat.toDouble()
                    val len = note.lengthBeats.toDouble()
                    val end = start + len

                    // Accurate note triggering:
                    val isNoteActive = if (end <= loopBeats) {
                        beat in start..end
                    } else {
                        beat >= start || beat < (end % loopBeats)
                    }

                    if (isNoteActive) {
                        val relBeat = if (beat >= start) beat - start else (beat + loopBeats - start)
                        val t = relBeat * 60.0 / bpm
                        val freq = midiToHz(note.pitch)

                        // Smooth ADSR Envelope
                        val env = if (relBeat < 0.04) {
                            (relBeat / 0.04).toFloat()
                        } else {
                            exp(-relBeat / max(0.15, len * 0.6)).toFloat()
                        }

                        val wave = fastWaveform(inst, freq, t)
                        val sample = wave * env * note.velocity * trVol * 0.22f

                        left += sample
                        right += sample
                    }
                }
            }

            // Percussion / Oriental Drum Rhythm
            val rhythmSample = fastPercussionAt(beat)
            left += rhythmSample * 0.20f
            right += rhythmSample * 0.20f

            // 1. Studio Master Filter (LP)
            filterStateL += alpha * (left - filterStateL)
            filterStateR += alpha * (right - filterStateR)
            var procL = filterStateL
            var procR = filterStateR

            // 2. Studio Master Delay & Reverb
            if (dly > 0.01f || rev > 0.01f) {
                val delayReadIdx = (delayWritePos - dlyFrames + 44100) % 44100
                val revReadIdx = (delayWritePos - (sampleRate * 0.045).toInt() + 44100) % 44100

                val echoL = delayBufferL[delayReadIdx] * dly * 0.6f
                val echoR = delayBufferR[delayReadIdx] * dly * 0.6f
                val revL = delayBufferL[revReadIdx] * rev * 0.35f
                val revR = delayBufferR[revReadIdx] * rev * 0.35f

                procL += echoL + revL
                procR += echoR + revR

                delayBufferL[delayWritePos] = (procL * 0.5f).coerceIn(-1f, 1f)
                delayBufferR[delayWritePos] = (procR * 0.5f).coerceIn(-1f, 1f)
                delayWritePos = (delayWritePos + 1) % 44100
            }

            // 3. Warmth Drive / Soft Clipping
            if (warmthDrive > 0.01f) {
                procL = (procL * drive).coerceIn(-1f, 1f)
                procR = (procR * drive).coerceIn(-1f, 1f)
            }

            val lShort = (procL.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            val rShort = (procR.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            buffer[f * 2] = lShort
            buffer[f * 2 + 1] = rShort
        }
    }

    private fun fastWaveform(instrument: String, freq: Double, t: Double): Float {
        val s1 = fastSin(2.0 * PI * freq * t)
        val s2 = fastSin(2.0 * PI * freq * 2.0 * t)
        val s3 = fastSin(2.0 * PI * freq * 3.0 * t)
        val s4 = fastSin(2.0 * PI * freq * 4.0 * t)
        val sub = fastSin(2.0 * PI * (freq * 0.5) * t)
        val vibrato = 1.0f + 0.008f * fastSin(2.0 * PI * 5.5 * t)

        return when {
            instrument.contains("Oud") || instrument.contains("عود") ->
                (s1 + 0.52f * s2 + 0.22f * s3 + 0.10f * s4) * exp(-t * 2.0).toFloat()
            instrument.contains("Qanun") || instrument.contains("قانون") ->
                (s1 + 0.72f * s2 + 0.44f * s3 + 0.28f * s4) * exp(-t * 2.5).toFloat()
            instrument.contains("Nay") || instrument.contains("ناي") ->
                (s1 * vibrato + 0.22f * s2)
            instrument.contains("Mizmar") || instrument.contains("مزمار") ->
                (if (s1 > 0) 0.75f else -0.75f) + 0.35f * s2
            instrument.contains("Violin") || instrument.contains("وتريات") || instrument.contains("Strings") ->
                (s1 * vibrato + 0.42f * s2 + 0.24f * s3 + 0.12f * s4)
            instrument.contains("Cello") || instrument.contains("شيلو") ->
                (s1 + 0.55f * s2 + 0.35f * s3 + 0.20f * sub)
            instrument.contains("Guitar") || instrument.contains("جيتار") ->
                (s1 + 0.45f * s2 + 0.25f * s3) * exp(-t * 2.2).toFloat()
            instrument.contains("Bass") || instrument.contains("باس") ->
                (s1 + 0.60f * s2 + 0.45f * sub)
            instrument.contains("Accordion") || instrument.contains("أكورديون") ->
                (s1 + 0.65f * s2 + 0.35f * s3)
            instrument.contains("Piano") || instrument.contains("بيانو") ->
                (s1 + 0.50f * s2 + 0.25f * s3) * exp(-t * 1.8).toFloat()
            else ->
                (s1 + 0.35f * s2 + 0.15f * s3)
        } * 0.75f
    }

    private fun fastPercussionAt(beat: Double): Float {
        val step = ((beat * 2.0).toInt() % 8)
        val hit = selectedRhythm.pattern[step]
        val t = (beat * 2.0) - (beat * 2.0).toInt()

        return when (hit) {
            1 -> {
                // Doom
                val freq = 85.0 * exp(-t * 14.0)
                fastSin(2.0 * PI * freq * t) * exp(-t * 8.0).toFloat() * 1.2f
            }
            2 -> {
                // Tak
                val freq = 1200.0 * exp(-t * 22.0)
                (fastSin(2.0 * PI * freq * t) * 0.7f + (if (t < 0.05) 0.3f else 0f)) * exp(-t * 24.0).toFloat()
            }
            3 -> {
                // Riq / Sak
                val freq = 2400.0 * exp(-t * 26.0)
                fastSin(2.0 * PI * freq * t) * exp(-t * 28.0).toFloat() * 0.8f
            }
            else -> 0f
        }
    }

    fun playLiveDarbuka(type: String) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val durSec = 0.30
                val samples = (sampleRate * durSec).toInt()
                val pcm = ShortArray(samples * 2)

                for (i in 0 until samples) {
                    val t = i.toDouble() / sampleRate
                    val sample: Float = when (type) {
                        "Doom" -> {
                            val freq = 80.0 * exp(-t * 12.0)
                            fastSin(2.0 * PI * freq * t) * exp(-t * 7.0).toFloat() * 1.5f
                        }
                        "Tak" -> {
                            val freq = 1400.0 * exp(-t * 20.0)
                            fastSin(2.0 * PI * freq * t) * exp(-t * 20.0).toFloat() * 1.1f
                        }
                        "Sak" -> {
                            val noise = (Math.random().toFloat() * 2f - 1f)
                            noise * exp(-t * 30.0).toFloat() * 0.9f
                        }
                        "Ka" -> {
                            val freq = 1800.0 * exp(-t * 25.0)
                            fastSin(2.0 * PI * freq * t) * exp(-t * 25.0).toFloat() * 1.0f
                        }
                        "Riq" -> {
                            val jingle = fastSin(2.0 * PI * 3500.0 * t)
                            jingle * exp(-t * 14.0).toFloat() * 0.9f
                        }
                        "Bandir" -> {
                            val freq = 65.0 * exp(-t * 8.0)
                            fastSin(2.0 * PI * freq * t) * exp(-t * 5.0).toFloat() * 1.6f
                        }
                        else -> fastSin(2.0 * PI * 400.0 * t) * exp(-t * 15.0).toFloat()
                    }
                    val s = (sample * 24000.0f).toInt().coerceIn(-32768, 32767).toShort()
                    pcm[i * 2] = s
                    pcm[i * 2 + 1] = s
                }

                val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(pcm.size * 2)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                delay(320)
                track.release()
            } catch (_: Throwable) {}
        }
    }

    fun saveProject() {
        val root = JSONObject().apply {
            put("bpm", bpm)
            put("bars", bars)
            put("scale", selectedScale)
            put("key", selectedKey)
            put("rhythm", selectedRhythmIndex)
            put("loop", loopEnabled)
            put("tracks", JSONArray().apply {
                tracks.forEach { track ->
                    put(JSONObject().apply {
                        put("id", track.id); put("name", track.name); put("instrument", track.instrument)
                        put("volume", track.volume); put("muted", track.muted); put("solo", track.solo)
                        put("notes", JSONArray().apply {
                            track.notes.forEach { n -> put(JSONObject().apply { put("pitch", n.pitch); put("start", n.startBeat); put("length", n.lengthBeats); put("velocity", n.velocity) }) }
                        })
                    })
                }
            })
        }
        prefs.edit().putString("saved_project_v2", root.toString()).apply()
    }

    private fun loadProject() {
        val json = prefs.getString("saved_project_v2", null) ?: return
        try {
            val root = JSONObject(json)
            bpm = root.optInt("bpm", 112)
            bars = root.optInt("bars", 4)
            selectedScale = root.optString("scale", "Hijaz")
            selectedKey = root.optString("key", "D")
            selectedRhythmIndex = root.optInt("rhythm", 0)
            loopEnabled = root.optBoolean("loop", true)
            val trs = root.optJSONArray("tracks") ?: return
            tracks.clear()
            for (i in 0 until trs.length()) {
                val obj = trs.getJSONObject(i)
                val t = StudioTrack(obj.optInt("id", i), obj.optString("name", "Track ${i + 1}"), obj.optString("instrument", instruments.first()))
                t.volume = obj.optDouble("volume", 0.9).toFloat(); t.muted = obj.optBoolean("muted", false); t.solo = obj.optBoolean("solo", false)
                val notes = obj.optJSONArray("notes") ?: JSONArray()
                for (j in 0 until notes.length()) {
                    val n = notes.getJSONObject(j)
                    t.notes += StudioNote(n.optInt("pitch", 60), n.optDouble("start", 0.0).toFloat(), n.optDouble("length", 1.0).toFloat(), n.optDouble("velocity", 0.9).toFloat())
                }
                tracks += t
            }
            selectedTrackId = tracks.firstOrNull()?.id ?: 0
        } catch (_: Throwable) { }
    }

    private fun keyBaseMidi(): Int = 60 + keys.indexOf(selectedKey).coerceAtLeast(0)

    private fun scaleOffset(degree: Int): Int {
        val d = degree.coerceAtLeast(-7)
        val pattern = when (selectedScale) {
            "Minor", "مينور" -> intArrayOf(0, 2, 3, 5, 7, 8, 10)
            "Hijaz", "حجاز" -> intArrayOf(0, 1, 4, 5, 7, 8, 10)
            "Bayati", "بياتي" -> intArrayOf(0, 1, 3, 5, 7, 8, 10)
            "Rast", "راست" -> intArrayOf(0, 2, 3, 5, 7, 9, 10)
            "Nahawand", "نهاوند" -> intArrayOf(0, 2, 3, 5, 7, 8, 11)
            "Kurd", "كرد" -> intArrayOf(0, 1, 3, 5, 7, 8, 10)
            "Saba", "صبا" -> intArrayOf(0, 1, 3, 5, 6, 8, 10)
            "Sikah", "سيكاه" -> intArrayOf(0, 2, 3, 5, 7, 8, 10)
            "Ajam", "عجم" -> intArrayOf(0, 2, 4, 5, 7, 9, 11)
            "Hijaz Kar" -> intArrayOf(0, 1, 4, 5, 7, 8, 11)
            "Jiharkah" -> intArrayOf(0, 2, 4, 5, 7, 8, 10)
            "Suznak" -> intArrayOf(0, 2, 3, 5, 7, 8, 11)
            "Harmonic Minor" -> intArrayOf(0, 2, 3, 5, 7, 8, 11)
            "Melodic Minor" -> intArrayOf(0, 2, 3, 5, 7, 9, 11)
            "Pentatonic Major" -> intArrayOf(0, 2, 4, 7, 9, 12, 14)
            "Pentatonic Minor" -> intArrayOf(0, 3, 5, 7, 10, 12, 15)
            "Blues" -> intArrayOf(0, 3, 5, 6, 7, 10, 12)
            "Dorian" -> intArrayOf(0, 2, 3, 5, 7, 9, 10)
            "Phrygian" -> intArrayOf(0, 1, 3, 5, 7, 8, 10)
            else -> intArrayOf(0, 2, 4, 5, 7, 9, 11)
        }
        val octave = Math.floorDiv(d, 7)
        val index = Math.floorMod(d, 7)
        return pattern[index] + octave * 12
    }

    private fun midiToHz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    fun close() { stopPlayback() }
}
