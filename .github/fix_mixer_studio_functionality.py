from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MIXER = ROOT / 'app/src/main/java/com/example/player/DJDeckController.kt'
STUDIO = ROOT / 'app/src/main/java/com/example/studio/MusicStudioController.kt'


def replace_once(text: str, pattern: str, replacement: str, label: str, regex: bool = False) -> str:
    if regex:
        new_text, n = re.subn(pattern, replacement, text, count=1, flags=re.S)
    else:
        n = text.count(pattern)
        new_text = text.replace(pattern, replacement, 1)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 match, got {n}')
    return new_text


def fix_mixer() -> None:
    text = MIXER.read_text(encoding='utf-8')
    # Kotlin generates a JVM setter for a mutable property. Do not name a
    # function setMixerGain while also declaring a property mixerGain.
    text = re.sub(r'\bmixerGain\b', 'mixerGainValue', text)

    marker = '// MIXER_FUNCTIONALITY_V1'
    if marker not in text:
        text = replace_once(
            text,
            '    var volume by mutableStateOf(0.8f)\n        private set\n\n    var currentPositionMs by mutableStateOf(0L)',
            '    var volume by mutableStateOf(0.8f)\n        private set\n\n    // MIXER_FUNCTIONALITY_V1\n    // User volume is the deck fader; mixerGainValue is applied by the crossfader.\n    private var mixerGainValue by mutableStateOf(1f)\n\n    var currentPositionMs by mutableStateOf(0L)',
            'mixer gain field'
        )

        text = replace_once(
            text,
            '    fun setDeckVolume(vol: Float) {\n        volume = vol.coerceIn(0f, 1f)\n        exoPlayer.volume = volume\n    }',
            '    fun setDeckVolume(vol: Float) {\n        volume = vol.coerceIn(0f, 1f)\n        applyMixerGain()\n    }\n\n    fun setMixerGain(gain: Float) {\n        mixerGainValue = gain.coerceIn(0f, 1f)\n        applyMixerGain()\n    }\n\n    private fun applyMixerGain() {\n        exoPlayer.volume = (volume * mixerGainValue).coerceIn(0f, 1f)\n    }',
            'deck volume application'
        )

        text = replace_once(
            text,
            '    fun updateCrossfader(position: Float) {\n        crossfader = position.coerceIn(0f, 1f)\n        val volA = (1f - crossfader) * deckA.volume\n        val volB = crossfader * deckB.volume\n        deckA.exoPlayer.volume = volA\n        deckB.exoPlayer.volume = volB\n    }',
            '    fun updateCrossfader(position: Float) {\n        crossfader = position.coerceIn(0f, 1f)\n        deckA.setMixerGain(1f - crossfader)\n        deckB.setMixerGain(crossfader)\n    }',
            'crossfader routing'
        )

        text = replace_once(
            text,
            '        deckA.exoPlayer.volume = deckA.volume\n        deckB.exoPlayer.volume = deckB.volume',
            '        deckA.setMixerGain(1f - crossfader)\n        deckB.setMixerGain(crossfader)',
            'melody crossfader reapply'
        )
        MIXER.write_text(text, encoding='utf-8')


def fix_studio() -> None:
    text = STUDIO.read_text(encoding='utf-8')
    marker = '// STUDIO_FUNCTIONALITY_V1'
    if marker not in text:
        text = replace_once(
            text,
            '    var playheadBeat by mutableStateOf(0f)\n\n    private var playbackJob: Job? = null',
            '    var playheadBeat by mutableStateOf(0f)\n\n    // STUDIO_FUNCTIONALITY_V1\n    // Forces Compose refresh after edits to nested mutable track data.\n    var uiRevision by mutableStateOf(0)\n        private set\n\n    private fun bumpUi() { uiRevision++ }\n\n    private var playbackJob: Job? = null',
            'studio ui revision'
        )

        replacements = [
            ('    fun addNote(pitch: Int, beat: Float, length: Float = 1f) {\n        selectedTrack.notes.removeAll { it.pitch == pitch && kotlin.math.abs(it.startBeat - beat) < 0.01f }\n        selectedTrack.notes += StudioNote(pitch, beat.coerceIn(0f, loopBeats - 0.25f), length.coerceIn(0.25f, 4f))\n    }',
             '    fun addNote(pitch: Int, beat: Float, length: Float = 1f) {\n        selectedTrack.notes.removeAll { it.pitch == pitch && kotlin.math.abs(it.startBeat - beat) < 0.01f }\n        selectedTrack.notes += StudioNote(pitch, beat.coerceIn(0f, loopBeats - 0.25f), length.coerceIn(0.25f, 4f))\n        bumpUi()\n    }'),
            ('    fun removeNote(pitch: Int, beat: Float) {\n        selectedTrack.notes.removeAll { it.pitch == pitch && kotlin.math.abs(it.startBeat - beat) < 0.26f }\n    }',
             '    fun removeNote(pitch: Int, beat: Float) {\n        selectedTrack.notes.removeAll { it.pitch == pitch && kotlin.math.abs(it.startBeat - beat) < 0.26f }\n        bumpUi()\n    }'),
            ('    fun clearTrack() { selectedTrack.notes.clear() }',
             '    fun clearTrack() { selectedTrack.notes.clear(); bumpUi() }'),
            ('        tracks += copy\n        selectedTrackId = nextId',
             '        tracks += copy\n        selectedTrackId = nextId\n        bumpUi()'),
            ('        tracks += StudioTrack(nextId, "Track ${tracks.size + 1}", instruments[nextId % instruments.size])\n        selectedTrackId = nextId',
             '        tracks += StudioTrack(nextId, "Track ${tracks.size + 1}", instruments[nextId % instruments.size])\n        selectedTrackId = nextId\n        bumpUi()'),
            ('        selectedTrackId = tracks.getOrNull((index - 1).coerceAtLeast(0))?.id ?: tracks.first().id\n    }',
             '        selectedTrackId = tracks.getOrNull((index - 1).coerceAtLeast(0))?.id ?: tracks.first().id\n        bumpUi()\n    }'),
            ('    fun setTrackInstrument(instrument: String) { selectedTrack.instrument = instrument }',
             '    fun setTrackInstrument(instrument: String) { selectedTrack.instrument = instrument; bumpUi() }'),
            ('    fun setTrackVolume(value: Float) { selectedTrack.volume = value.coerceIn(0f, 1f) }',
             '    fun setTrackVolume(value: Float) { selectedTrack.volume = value.coerceIn(0f, 1f); bumpUi() }'),
            ('    fun toggleTrackMute() { selectedTrack.muted = !selectedTrack.muted }',
             '    fun toggleTrackMute() { selectedTrack.muted = !selectedTrack.muted; bumpUi() }'),
            ('    fun toggleTrackSolo() { selectedTrack.solo = !selectedTrack.solo }',
             '    fun toggleTrackSolo() { selectedTrack.solo = !selectedTrack.solo; bumpUi() }'),
        ]
        for old, new in replacements:
            text = replace_once(text, old, new, 'studio mutation')

        text = replace_once(
            text,
            '        preset.notes.forEachIndexed { i, degree ->\n            val pitch = root + scaleOffset(degree)\n            selectedTrack.notes += StudioNote(pitch, i.toFloat().coerceAtMost(loopBeats - 1f), 0.75f)\n        }\n    }',
            '        preset.notes.forEachIndexed { i, degree ->\n            val pitch = root + scaleOffset(degree)\n            selectedTrack.notes += StudioNote(pitch, i.toFloat().coerceAtMost(loopBeats - 1f), 0.75f)\n        }\n        bumpUi()\n    }',
            'melody preset refresh'
        )
        text = replace_once(
            text,
            '        degrees.forEachIndexed { bar, degree ->\n            val base = root + scaleOffset(degree)\n            addChord(base, bar * 4f)\n        }\n    }',
            '        degrees.forEachIndexed { bar, degree ->\n            val base = root + scaleOffset(degree)\n            addChord(base, bar * 4f)\n        }\n        bumpUi()\n    }',
            'progression refresh'
        )

        text = replace_once(
            text,
            '                    if (beat >= loopBeats) {\n                        beat %= loopBeats\n                    }',
            '                    if (beat >= loopBeats) {\n                        if (loopEnabled) {\n                            beat %= loopBeats\n                        } else {\n                            isPlaying = false\n                            break\n                        }\n                    }',
            'one-shot playback'
        )

        STUDIO.write_text(text, encoding='utf-8')


def main() -> None:
    fix_mixer()
    fix_studio()
    print('Mixer/Studio functionality patch applied.')


if __name__ == '__main__':
    main()
