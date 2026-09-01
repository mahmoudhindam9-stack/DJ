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
        "Grand Piano", "Warm Piano", "Oud", "Qanun", "Nay", "Arabic Violin",
        "Mizmar", "Strings", "Cello", "Flute", "Sax", "Trumpet", "Guitar",
        "Bass", "Synth Lead", "Warm Pad", "Bell"
    )

    val scales = listOf("Major", "Minor", "Hijaz", "Bayati", "Rast", "Nahawand", "Kurd", "Saba", "Ajam")
    val keys = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val rhythms = listOf(
        RhythmPreset("Maqsum", intArrayOf(1,0,2,0,1,0,2,0)),
        RhythmPreset("Baladi", intArrayOf(1,0,0,2,1,0,2,0)),
        RhythmPreset("Saeidi", intArrayOf(1,0,2,1,1,0,2,1)),
        RhythmPreset("Malfouf", intArrayOf(1,0,2,1,1,0,2,1)),
        RhythmPreset("Khaleeji", intArrayOf(1,2,0,2,1,2,0,2)),
        RhythmPreset("Sama'i", intArrayOf(1,0,0,1,2,0,1,0)),
        RhythmPreset("Rumba", intArrayOf(1,0,2,0,0,2,1,0)),
        RhythmPreset("Pop", intArrayOf(1,0,2,0,1,0,2,0)),
        RhythmPreset("Rock", intArrayOf(1,2,1,2,1,2,1,2)),
        RhythmPreset("House", intArrayOf(1,2,1,2,1,2,1,2)),
        RhythmPreset("Disco", intArrayOf(1,0,2,0,1,0,2,0)),
        RhythmPreset("Waltz", intArrayOf(1,0,0,2,0,0,2,0)),
        RhythmPreset("6/8", intArrayOf(1,0,2,1,0,2,1,0)),
        RhythmPreset("Latin", intArrayOf(1,0,2,1,0,2,1,2)),
        RhythmPreset("Slow Ballad", intArrayOf(1,0,0,0,2,0,0,0)),
        RhythmPreset("Wedding", intArrayOf(1,0,2,1,1,0,2,1))
    )

    val melodyPresets = listOf(
        MelodyPreset("Oriental Rise", listOf(0,2,4,5,7,5,4,2,0)),
        MelodyPreset("Hijaz Walk", listOf(0,1,4,5,7,5,4,1,0)),
        MelodyPreset("Bayati Phrase", listOf(0,1,3,4,3,1,0,-2,0)),
        MelodyPreset("Rast Phrase", listOf(0,2,3,5,4,3,2,0)),
        MelodyPreset("Arabic Dance", listOf(0,2,0,4,3,2,0,5,4,2)),
        MelodyPreset("Romantic", listOf(0,3,5,7,5,4,3,2,0)),
        MelodyPreset("Pop Hook", listOf(0,0,3,5,3,0,7,5,3)),
        MelodyPreset("Ballad", listOf(0,2,4,7,5,4,2,0)),
        MelodyPreset("Wedding Intro", listOf(0,4,5,7,9,7,5,4,2,0)),
        MelodyPreset("Mizmar", listOf(0,3,4,7,8,7,4,3,0)),
        MelodyPreset("Nay Solo", listOf(0,2,5,4,2,7,5,4,2,0)),
        MelodyPreset("Qanun", listOf(0,2,4,7,9,7,4,2,0)),
        MelodyPreset("Oud Theme", listOf(0,2,3,5,3,2,0,7,5,3)),
        MelodyPreset("Saeidi Hook", listOf(0,0,3,5,7,5,3,0)),
        MelodyPreset("Oriental Pop", listOf(0,2,4,2,7,5,4,2,0)),
        MelodyPreset("Finale", listOf(7,9,11,12,11,9,7,5,4,2,0))
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

    fun addNote(pitch: Int, beat: Float, length: Float = 1f) {
        selectedTrack.notes.removeAll { it.pitch == pitch && kotlin.math.abs(it.startBeat - beat) < 0.01f }
        selectedTrack.notes += StudioNote(pitch, beat.coerceIn(0f, loopBeats - 0.25f), length.coerceIn(0.25f, 4f))
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
                var last = System.nanoTime()
                while (isActive && isPlaying) {
                    val now = System.nanoTime()
                    val dt = (now - last) / 1_000_000_000.0
                    last = now
                    beat += dt * bpm / 60.0
                    if (beat >= loopBeats) {
                        if (loopEnabled) {
                            beat %= loopBeats
                        } else {
                            isPlaying = false
                            break
                        }
                    }
                    playheadBeat = beat.toFloat()
                    synthChunk(buffer, beat)
                    track.write(buffer, 0, buffer.size)
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

    private fun waveform(instrument: String, freq: Double, t: Double): Double {
        val s1 = sin(2.0 * PI * freq * t)
        val s2 = sin(2.0 * PI * freq * 2.0 * t)
        val s3 = sin(2.0 * PI * freq * 3.0 * t)
        return when {
            instrument.contains("Oud") -> s1 + 0.46 * s2 + 0.18 * s3
            instrument.contains("Qanun") -> s1 + 0.62 * s2 + 0.34 * s3
            instrument.contains("Nay") -> s1 + 0.18 * s2
            instrument.contains("Violin") || instrument.contains("Strings") || instrument.contains("Cello") -> s1 + 0.32 * s2 + 0.16 * s3
            instrument.contains("Bass") -> s1 + 0.44 * s2
            instrument.contains("Synth") || instrument.contains("Pad") -> s1 + 0.52 * s2 + 0.22 * s3
            instrument.contains("Bell") -> s1 + 0.72 * s2 + 0.30 * s4(freq, t)
            instrument.contains("Flute") -> s1 + 0.10 * s2
            instrument.contains("Sax") || instrument.contains("Trumpet") -> s1 + 0.52 * s2 + 0.24 * s3
            else -> s1 + 0.24 * s2 + 0.12 * s3
        } * 0.68
    }

    private fun s4(freq: Double, t: Double): Double = sin(2.0 * PI * freq * 4.0 * t)
    private fun percussionAt(beat: Double): Double {
        val step = ((beat * 2.0).toInt() % 8)
        val hit = selectedRhythm.pattern[step]
        return when (hit) {
            1 -> exp(-((beat * 2.0) - (beat * 2.0).toInt()) * 25.0)
            2 -> 0.5 * exp(-((beat * 2.0) - (beat * 2.0).toInt()) * 35.0)
            else -> 0.0
        }
    }

    private fun keyBaseMidi(): Int = 60 + keys.indexOf(selectedKey).coerceAtLeast(0)

    private fun scaleOffset(degree: Int): Int {
        val d = degree.coerceAtLeast(-7)
        val pattern = when (selectedScale) {
            "Minor" -> intArrayOf(0, 2, 3, 5, 7, 8, 10)
            "Hijaz" -> intArrayOf(0, 1, 4, 5, 7, 8, 10)
            "Bayati" -> intArrayOf(0, 1, 3, 5, 7, 8, 10)
            "Rast" -> intArrayOf(0, 2, 3, 5, 7, 9, 10)
            "Nahawand" -> intArrayOf(0, 2, 3, 5, 7, 8, 11)
            "Kurd" -> intArrayOf(0, 1, 3, 5, 7, 8, 10)
            "Saba" -> intArrayOf(0, 1, 3, 5, 6, 8, 10)
            "Ajam" -> intArrayOf(0, 2, 4, 5, 7, 9, 11)
            else -> intArrayOf(0, 2, 4, 5, 7, 9, 11)
        }
        val octave = Math.floorDiv(d, 7)
        val index = Math.floorMod(d, 7)
        return pattern[index] + octave * 12
    }

    private fun midiToHz(midi: Int): Double = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)

    fun close() { stopPlayback() }
}
