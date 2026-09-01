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
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * Lightweight in-app arranger/sequencer inspired by professional MIDI workstations.
 * It is original code: multi-track notes, chord stacking, fixed melody/rhythm banks,
 * persistent projects and a realtime polyphonic preview engine.
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
        MelodyPreset("Alf Leila (ألف ليلةولييلة)", listOf(0, 2, 3, 5, 7, 8, 7, 5, 3, 2, 0)),
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
        StudioTrack(0, "Lead", "Oud"),
        StudioTrack(1, "Harmony", "Qanun"),
        StudioTrack(2, "Counter", "Arabic Violin"),
        StudioTrack(3, "Bass", "Bass")
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

    // STUDIO_FUNCTIONALITY_V1
    // Forces Compose refresh after edits to nested mutable track data.
    var uiRevision by mutableStateOf(0)
        private set

    private fun bumpUi() { uiRevision++ }

    private var playbackJob: Job? = null
    private val prefs = context.getSharedPreferences("music_studio_project", Context.MODE_PRIVATE)
    private val sampleRate = 44100

    init { loadProject() }

    val selectedTrack: StudioTrack get() = tracks.firstOrNull { it.id == selectedTrackId } ?: tracks.first()
    val selectedRhythm: RhythmPreset get() = rhythms.getOrElse(selectedRhythmIndex) { rhythms.first() }
    val loopBeats: Float get() = bars.coerceIn(1, 32) * 4f

    private var previewTrack: AudioTrack? = null

    fun playNotePreview(pitch: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val freq = midiToHz(pitch)
                val durSec = 0.22
                val samples = (sampleRate * durSec).toInt()
                val pcm = ShortArray(samples * 2)
                for (i in 0 until samples) {
                    val t = i.toDouble() / sampleRate
                    val env = Math.exp(-t * 9.0)
                    val s = (waveform(selectedTrack.instrument, freq, t) * env * 14000.0).toInt().coerceIn(-32768, 32767).toShort()
                    pcm[i * 2] = s
                    pcm[i * 2 + 1] = s
                }
                synchronized(this@MusicStudioController) {
                    try { previewTrack?.stop() } catch (_: Throwable) {}
                    try { previewTrack?.release() } catch (_: Throwable) {}
                    previewTrack = AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                        .setBufferSizeInBytes(pcm.size * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                    previewTrack?.write(pcm, 0, pcm.size)
                    previewTrack?.play()
                }
            } catch (_: Throwable) {}
        }
    }

    fun addNote(pitch: Int, beat: Float, length: Float = 1f) {
        selectedTrack.notes.removeAll { it.pitch == pitch && kotlin.math.abs(it.startBeat - beat) < 0.01f }
        selectedTrack.notes += StudioNote(pitch, beat.coerceIn(0f, loopBeats - 0.25f), length.coerceIn(0.25f, 4f))
        playNotePreview(pitch)
        bumpUi()
    }

    fun removeNote(pitch: Int, beat: Float) {
        selectedTrack.notes.removeAll { it.pitch == pitch && kotlin.math.abs(it.startBeat - beat) < 0.26f }
        bumpUi()
    }

    fun addChord(rootPitch: Int, beat: Float) {
        val intervals = intArrayOf(0, 4, 7)
        intervals.forEach { addNote(rootPitch + it, beat, 1f) }
    }

    fun clearTrack() { selectedTrack.notes.clear(); bumpUi() }

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
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(sampleRate / 2 * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            try {
                track.play()
                var beat = 0.0
                val frameChunk = 512
                val buffer = ShortArray(frameChunk * 2)
                val beatStep = (frameChunk.toDouble() / sampleRate) * (bpm / 60.0)
                while (isActive && isPlaying) {
                    playheadBeat = beat.toFloat()
                    synthChunk(buffer, beat)
                    track.write(buffer, 0, buffer.size)
                    beat += beatStep
                    if (beat >= loopBeats) {
                        if (loopEnabled) {
                            beat %= loopBeats
                        } else {
                            isPlaying = false
                            break
                        }
                    }
                }
            } finally {
                try { track.stop() } catch (_: Throwable) {}
                track.release()
            }
        }
    }

    fun stopPlayback() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
        playheadBeat = 0f
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
        prefs.edit().putString("project", root.toString()).apply()
    }

    private fun loadProject() {
        val raw = prefs.getString("project", null) ?: return
        try {
            val root = JSONObject(raw)
            bpm = root.optInt("bpm", bpm).coerceIn(50, 220)
            bars = root.optInt("bars", bars).coerceIn(1, 32)
            selectedScale = root.optString("scale", selectedScale)
            selectedKey = root.optString("key", selectedKey)
            selectedRhythmIndex = root.optInt("rhythm", 0).coerceIn(rhythms.indices)
            loopEnabled = root.optBoolean("loop", true)
            val saved = root.optJSONArray("tracks") ?: return
            tracks.clear()
            for (i in 0 until saved.length()) {
                val obj = saved.getJSONObject(i)
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

    private fun synthChunk(buffer: ShortArray, beatNow: Double) {
        val soloExists = tracks.any { it.solo }
        var frame = 0
        val beatPerFrame = (bpm / 60.0) / sampleRate
        for (i in buffer.indices step 2) {
            val beat = (beatNow + frame * beatPerFrame) % loopBeats
            var left = 0.0
            var right = 0.0
            tracks.forEach { track ->
                if (track.muted || (soloExists && !track.solo)) return@forEach
                track.notes.forEach { note ->
                    val rel = beat - note.startBeat
                    val wrapped = if (rel < 0) rel + loopBeats else rel
                    if (wrapped >= 0 && wrapped < note.lengthBeats) {
                        val t = wrapped * 60.0 / bpm
                        val freq = midiToHz(note.pitch)
                        val env = if (wrapped < 0.04) wrapped / 0.04 else exp(-wrapped / max(0.12, note.lengthBeats * 0.55))
                        val wave = waveform(track.instrument, freq, t)
                        val sample = wave * env * note.velocity * track.volume * 0.16
                        left += sample * (0.98 - track.id * 0.01).coerceIn(0.55, 0.98)
                        right += sample * (0.94 + track.id * 0.01).coerceIn(0.55, 0.98)
                    }
                }
            }
            val rhythmSample = percussionAt(beat)
            left += rhythmSample * 0.09
            right += rhythmSample * 0.09
            val l = (left.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            val r = (right.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            buffer[i] = l; buffer[i + 1] = r
            frame++
        }
    }

    fun playLiveDarbuka(type: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val durSec = 0.35
                val samples = (sampleRate * durSec).toInt()
                val pcm = ShortArray(samples * 2)
                for (i in 0 until samples) {
                    val t = i.toDouble() / sampleRate
                    val sample: Double = when (type) {
                        "Doom" -> {
                            val freq = 80.0 * exp(-t * 12.0)
                            sin(2.0 * PI * freq * t) * exp(-t * 7.0) * 1.5
                        }
                        "Tak" -> {
                            val noise = (Math.random() * 2.0 - 1.0)
                            val freq = 1400.0 * exp(-t * 20.0)
                            (sin(2.0 * PI * freq * t) * 0.6 + noise * 0.4) * exp(-t * 22.0)
                        }
                        "Sak" -> {
                            val noise = (Math.random() * 2.0 - 1.0)
                            (noise * 0.8) * exp(-t * 35.0)
                        }
                        "Ka" -> {
                            val freq = 1800.0 * exp(-t * 25.0)
                            sin(2.0 * PI * freq * t) * exp(-t * 28.0)
                        }
                        "Riq" -> {
                            val noise = (Math.random() * 2.0 - 1.0)
                            val jingle = sin(2.0 * PI * 3500.0 * t)
                            (jingle * 0.5 + noise * 0.5) * exp(-t * 14.0)
                        }
                        "Bandir" -> {
                            val freq = 65.0 * exp(-t * 8.0)
                            val buzz = (Math.random() * 2.0 - 1.0) * 0.2
                            (sin(2.0 * PI * freq * t) + buzz) * exp(-t * 5.0) * 1.6
                        }
                        else -> (Math.random() * 2.0 - 1.0) * exp(-t * 15.0)
                    }
                    val s = (sample * 16000.0).toInt().coerceIn(-32768, 32767).toShort()
                    pcm[i * 2] = s
                    pcm[i * 2 + 1] = s
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                delay(380)
                track.release()
            } catch (_: Throwable) {}
        }
    }

    private fun waveform(instrument: String, freq: Double, t: Double): Double {
        val s1 = sin(2.0 * PI * freq * t)
        val s2 = sin(2.0 * PI * freq * 2.0 * t)
        val s3 = sin(2.0 * PI * freq * 3.0 * t)
        val s4 = sin(2.0 * PI * freq * 4.0 * t)
        val s5 = sin(2.0 * PI * freq * 5.0 * t)
        val s6 = sin(2.0 * PI * freq * 6.0 * t)
        val sub = sin(2.0 * PI * (freq * 0.5) * t)

        val vibrato = 1.0 + 0.008 * sin(2.0 * PI * 5.5 * t)

        return when {
            instrument.contains("Oud") -> (s1 + 0.52 * s2 + 0.22 * s3 + 0.10 * s4) * exp(-t * 1.8)
            instrument.contains("Qanun") || instrument.contains("Kanun") -> (s1 + 0.72 * s2 + 0.44 * s3 + 0.28 * s4) * exp(-t * 2.4)
            instrument.contains("Nay") -> (s1 * vibrato + 0.22 * s2 + (Math.random() * 0.04 - 0.02))
            instrument.contains("Mizmar") -> (if (s1 > 0) 0.8 else -0.8) + 0.4 * s2 + 0.2 * s3
            instrument.contains("Violin") || instrument.contains(" وتريات") || instrument.contains("Strings") -> (s1 * vibrato + 0.42 * s2 + 0.24 * s3 + 0.12 * s4)
            instrument.contains("Cello") || instrument.contains("شيلو") -> (s1 + 0.55 * s2 + 0.35 * s3 + 0.18 * sub)
            instrument.contains("Bouzouki") || instrument.contains("بزق") -> (s1 + 0.65 * s2 + 0.45 * s4) * exp(-t * 2.2)
            instrument.contains("Rebab") || instrument.contains("ربابة") -> (s1 * vibrato + 0.35 * s2 + 0.20 * s3)
            instrument.contains("Accordion") || instrument.contains("أكورديون") -> (s1 + 0.65 * s2 + 0.35 * s3 + 0.25 * s5)
            instrument.contains("Guitar") || instrument.contains("جيتار") -> (s1 + 0.45 * s2 + 0.25 * s3) * exp(-t * 2.0)
            instrument.contains("Bass") || instrument.contains("باس") -> (s1 + 0.60 * s2 + 0.40 * sub)
            instrument.contains("Sax") || instrument.contains("Flute") || instrument.contains("Clarinet") -> (s1 + 0.35 * s2 + 0.18 * s3)
            instrument.contains("Organ") || instrument.contains("أورغن") -> (s1 + s2 * 0.8 + s3 * 0.6 + s4 * 0.4 + s5 * 0.3)
            instrument.contains("Synth") || instrument.contains("Pad") -> (s1 + 0.50 * s2 + 0.30 * s3 + 0.15 * s4)
            instrument.contains("Harp") || instrument.contains("هارب") -> (s1 + 0.55 * s2 + 0.30 * s3) * exp(-t * 3.0)
            else -> s1 + 0.30 * s2 + 0.15 * s3
        } * 0.68
    }

    private fun percussionAt(beat: Double): Double {
        val step = ((beat * 2.0).toInt() % 8)
        val hit = selectedRhythm.pattern[step]
        val t = (beat * 2.0) - (beat * 2.0).toInt()
        return when (hit) {
            1 -> {
                // Doom (deep resonant bass)
                val freq = 85.0 * exp(-t * 14.0)
                sin(2.0 * PI * freq * t) * exp(-t * 8.0) * 1.4
            }
            2 -> {
                // Tak (sharp high edge)
                val noise = (Math.random() * 2.0 - 1.0)
                val freq = 1300.0 * exp(-t * 22.0)
                (sin(2.0 * PI * freq * t) * 0.6 + noise * 0.4) * exp(-t * 24.0) * 0.9
            }
            3 -> {
                // Riq / Sak (accented snap)
                val noise = (Math.random() * 2.0 - 1.0)
                (noise * 0.8) * exp(-t * 32.0) * 0.7
            }
            else -> 0.0
        }
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

    private fun midiToHz(midi: Int): Double = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)

    fun close() { stopPlayback() }
}
