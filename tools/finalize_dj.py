from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# DJDeckController: route Media3 PCM through the real FX processor and expose
# toggle methods used by the UI. No fake volume/speed side effects remain.
p = ROOT / 'app/src/main/java/com/example/player/DJDeckController.kt'
s = p.read_text(encoding='utf-8')

old = 'import androidx.media3.exoplayer.ExoPlayer\n'
new = '''import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.common.util.UnstableApi
'''
if 'import androidx.media3.exoplayer.DefaultRenderersFactory' not in s:
    s = s.replace(old, new, 1)

old = '''class DJDeck(context: Context, val deckName: String) {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()
'''
new = '''@OptIn(UnstableApi::class)
class DJDeck(context: Context, val deckName: String) {
    private val fxProcessor = DeckFxAudioProcessor()

    private val renderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): AudioSink {
            return DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(fxProcessor))
                .build()
        }
    }

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context, renderersFactory).build()
'''
if old in s:
    s = s.replace(old, new, 1)

marker = '    var isCrushActive by mutableStateOf(false)\n'
methods = '''\n    fun toggleFlanger() {
        isFlangerActive = !isFlangerActive
        fxProcessor.flangerEnabled = isFlangerActive
    }

    fun toggleReverb() {
        isReverbActive = !isReverbActive
        fxProcessor.reverbEnabled = isReverbActive
    }

    fun toggleEcho() {
        isEchoActive = !isEchoActive
        fxProcessor.echoEnabled = isEchoActive
    }

    fun toggleCrush() {
        isCrushActive = !isCrushActive
        fxProcessor.crushEnabled = isCrushActive
    }
'''
if 'fun toggleFlanger()' not in s and marker in s:
    s = s.replace(marker, marker + methods, 1)

old = '''    fun setPitchAndSpeed(newRate: Float) {
        pitchSpeed = newRate.coerceIn(0.5f, 1.5f)
        var effectiveSpeed = pitchSpeed
        if (isFlangerActive) {
            effectiveSpeed *= 1.02f
        }
        exoPlayer.playbackParameters = PlaybackParameters(effectiveSpeed, effectiveSpeed)
    }

    fun setDeckVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        var effVol = volume
        if (isEchoActive) {
            effVol = (effVol * 1.15f).coerceAtMost(1f)
        }
        if (isCrushActive) {
            effVol *= 0.9f
        }
        exoPlayer.volume = effVol
    }
'''
new = '''    fun setPitchAndSpeed(newRate: Float) {
        pitchSpeed = newRate.coerceIn(0.5f, 1.5f)
        exoPlayer.playbackParameters = PlaybackParameters(pitchSpeed, pitchSpeed)
    }

    fun setDeckVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        exoPlayer.volume = volume
    }
'''
if old in s:
    s = s.replace(old, new, 1)

old = '''        deckA.exoPlayer.volume = if (deckA.isEchoActive) (volA * 1.15f).coerceAtMost(1f) else volA
        deckB.exoPlayer.volume = if (deckB.isEchoActive) (volB * 1.15f).coerceAtMost(1f) else volB
'''
new = '''        deckA.exoPlayer.volume = volA
        deckB.exoPlayer.volume = volB
'''
if old in s:
    s = s.replace(old, new, 1)

marker = '''    fun pauseAll() {
        deckA.pause()
        deckB.pause()
    }
'''
method = '''
    fun playMelodyOverDeckA(melody: AudioItem): Boolean {
        if (deckA.track == null) return false
        deckB.loadTrack(melody)
        deckA.exoPlayer.volume = deckA.volume
        deckB.exoPlayer.volume = deckB.volume
        deckA.exoPlayer.play()
        deckB.exoPlayer.play()
        return true
    }
'''
if 'fun playMelodyOverDeckA' not in s and marker in s:
    s = s.replace(marker, marker + method, 1)

p.write_text(s, encoding='utf-8')

# Expand the soundboard with a second synthesized sound bank.
extra = ROOT / 'app/src/main/java/com/example/ExtraSoundPlayer.kt'
extra.write_text(r'''package com.example

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

enum class ExtraSound(val title: String) {
    RIMSHOT("Rim Shot"), SNAP("Finger Snap"), MARACAS("Maracas"), CLAVE("Clave"),
    RIDE("Ride Cymbal"), SUB_KICK("Sub Kick"), PERC_CLICK("Perc Click"), BASS_PULSE("Bass Pulse"),
    IMPACT("Impact"), RISER("Riser"), DOWNLIFTER("Downlifter"), TELEPHONE("Telephone"),
    ROBOT("Robot Zap"), PIANO_PLUCK("Piano Pluck"), ORGAN("Organ Hit"), CHORD("Power Chord"),
    PERC_SHOUT("Perc Shout"), REVERSE("Reverse Sweep"), TICK("Tick"), BELL("Bell"),
    WOODBLOCK("Wood Block"), ELECTRO_HIT("Electro Hit"), LOW_BOOM("Low Boom"), HIGH_BEEP("High Beep")
}

object ExtraSoundPlayer {
    private const val sampleRate = 44_100

    fun play(sound: ExtraSound) {
        Thread {
            try {
                val seconds = when (sound) {
                    ExtraSound.RIMSHOT, ExtraSound.SNAP, ExtraSound.CLAVE, ExtraSound.PERC_CLICK, ExtraSound.TICK -> 0.22
                    ExtraSound.MARACAS -> 0.35
                    ExtraSound.RIDE -> 1.1
                    ExtraSound.SUB_KICK -> 0.65
                    ExtraSound.BASS_PULSE -> 0.45
                    ExtraSound.IMPACT, ExtraSound.LOW_BOOM -> 0.9
                    ExtraSound.RISER, ExtraSound.REVERSE -> 1.2
                    ExtraSound.DOWNLIFTER -> 0.9
                    ExtraSound.TELEPHONE -> 0.5
                    ExtraSound.ROBOT -> 0.35
                    ExtraSound.PIANO_PLUCK, ExtraSound.BELL -> 0.6
                    ExtraSound.ORGAN, ExtraSound.CHORD -> 0.75
                    ExtraSound.PERC_SHOUT -> 0.4
                    ExtraSound.WOODBLOCK, ExtraSound.ELECTRO_HIT, ExtraSound.HIGH_BEEP -> 0.35
                }
                val count = (sampleRate * seconds).toInt()
                val pcm = ShortArray(count)
                for (i in 0 until count) {
                    val t = i.toDouble() / sampleRate
                    val p = i.toDouble() / count
                    val noise = Math.random() * 2.0 - 1.0
                    val v = when (sound) {
                        ExtraSound.RIMSHOT -> noise * exp(-p * 18.0) + sin(2.0 * PI * 1450.0 * t) * exp(-p * 28.0) * 0.7
                        ExtraSound.SNAP -> noise * exp(-p * 24.0) * 0.8
                        ExtraSound.MARACAS -> noise * sin(PI * p) * 0.4
                        ExtraSound.CLAVE -> sin(2.0 * PI * 2200.0 * t) * exp(-p * 22.0) * 0.5
                        ExtraSound.RIDE -> (noise * 0.65 + sin(2.0 * PI * 3300.0 * t) * 0.35) * exp(-p * 3.4)
                        ExtraSound.SUB_KICK -> sin(2.0 * PI * (180.0 - 140.0 * p) * t) * exp(-p * 5.0)
                        ExtraSound.PERC_CLICK -> (noise * 0.7 + sin(2.0 * PI * 1800.0 * t) * 0.3) * exp(-p * 28.0)
                        ExtraSound.BASS_PULSE -> sin(2.0 * PI * 75.0 * t) * sin(PI * p) * 0.75
                        ExtraSound.IMPACT, ExtraSound.LOW_BOOM -> (sin(2.0 * PI * (160.0 - 110.0 * p) * t) * 0.75 + noise * 0.25) * exp(-p * 4.5)
                        ExtraSound.RISER -> sin(2.0 * PI * (220.0 + 1700.0 * p) * t) * sin(PI * p) * 0.45
                        ExtraSound.DOWNLIFTER -> sin(2.0 * PI * (1800.0 - 1500.0 * p) * t) * exp(-p * 2.4) * 0.45
                        ExtraSound.TELEPHONE -> (sin(2.0 * PI * 900.0 * t) + sin(2.0 * PI * 1050.0 * t)) * 0.25 * (1.0 - p * 0.35)
                        ExtraSound.ROBOT -> tanh(sin(2.0 * PI * 120.0 * t) * 7.0) * exp(-p * 8.0) * 0.35
                        ExtraSound.PIANO_PLUCK -> (sin(2.0 * PI * 440.0 * t) + sin(2.0 * PI * 880.0 * t) * 0.25) * exp(-p * 4.5) * 0.45
                        ExtraSound.BELL -> (sin(2.0 * PI * 880.0 * t) + sin(2.0 * PI * 1760.0 * t) * 0.3) * exp(-p * 3.5) * 0.45
                        ExtraSound.ORGAN -> (sin(2.0 * PI * 330.0 * t) + sin(2.0 * PI * 660.0 * t) * 0.45 + sin(2.0 * PI * 990.0 * t) * 0.2) * exp(-p * 2.2) * 0.25
                        ExtraSound.CHORD -> (sin(2.0 * PI * 220.0 * t) + sin(2.0 * PI * 277.18 * t) + sin(2.0 * PI * 329.63 * t)) * exp(-p * 3.2) * 0.25
                        ExtraSound.PERC_SHOUT -> (noise * 0.55 + sin(2.0 * PI * 520.0 * t) * 0.45) * sin(PI * p) * 0.65
                        ExtraSound.REVERSE -> sin(2.0 * PI * (1800.0 - 1500.0 * p) * t) * (p * p) * 0.5
                        ExtraSound.TICK -> (noise * 0.5 + sin(2.0 * PI * 2900.0 * t) * 0.5) * exp(-p * 35.0)
                        ExtraSound.WOODBLOCK -> sin(2.0 * PI * 760.0 * t) * exp(-p * 18.0) * 0.55
                        ExtraSound.ELECTRO_HIT -> (sin(2.0 * PI * 560.0 * t) + sin(2.0 * PI * 1120.0 * t) * 0.4) * exp(-p * 7.0) * 0.55
                        ExtraSound.HIGH_BEEP -> sin(2.0 * PI * 2400.0 * t) * exp(-p * 9.0) * 0.4
                    }
                    pcm[i] = (v.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep((seconds * 1000).toLong() + 40L)
                track.release()
            } catch (_: Throwable) { }
        }.start()
    }
}
''', encoding='utf-8')

# MainActivity: wire UI to real methods and show the extra bank.
m = ROOT / 'app/src/main/java/com/example/MainActivity.kt'
t = m.read_text(encoding='utf-8')
for a, b in {
    'DJPadButton("Flanger", deck.isFlangerActive) { deck.isFlangerActive = !deck.isFlangerActive }': 'DJPadButton("Flanger", deck.isFlangerActive) { deck.toggleFlanger() }',
    'DJPadButton("Reverb", deck.isReverbActive) { deck.isReverbActive = !deck.isReverbActive }': 'DJPadButton("Reverb", deck.isReverbActive) { deck.toggleReverb() }',
    'DJPadButton("Echo", deck.isEchoActive) { deck.isEchoActive = !deck.isEchoActive }': 'DJPadButton("Echo", deck.isEchoActive) { deck.toggleEcho() }',
    'DJPadButton("Crush", deck.isCrushActive) { deck.isCrushActive = !deck.isCrushActive }': 'DJPadButton("Crush", deck.isCrushActive) { deck.toggleCrush() }',
    'MelodyStudioCard(audioLibrary = audioLibrary, context = LocalContext.current)': 'MelodyStudioCard(audioLibrary = audioLibrary, context = LocalContext.current, djMixerController = djMixerController)',
}.items():
    t = t.replace(a, b)

anchor = '''        }
    }
}

@Composable
fun DJPadButton'''
extra_ui = '''        }

        Spacer(modifier = Modifier.height(16.dp))
        ExtraSoundsCard()
    }
}

@Composable
fun ExtraSoundsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("EXTRA SOUND BANK", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            ExtraSound.values().toList().chunked(4).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { sound ->
                        Button(
                            onClick = { ExtraSoundPlayer.play(sound) },
                            modifier = Modifier.weight(1f).height(42.dp),
                            contentPadding = PaddingValues(2.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(sound.title, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
fun DJPadButton'''
if 'fun ExtraSoundsCard()' not in t and anchor in t:
    t = t.replace(anchor, extra_ui, 1)
m.write_text(t, encoding='utf-8')

# Melody Studio: allow the last saved melody to be launched over the current Deck A song.
ms = ROOT / 'app/src/main/java/com/example/MelodyStudio.kt'
mt = ms.read_text(encoding='utf-8')
if 'import com.example.player.DJMixerController' not in mt:
    mt = mt.replace('import com.example.model.AudioItem\n', 'import com.example.model.AudioItem\nimport com.example.player.DJMixerController\n', 1)
mt = mt.replace('fun MelodyStudioCard(audioLibrary: MutableList<AudioItem>, context: Context) {', 'fun MelodyStudioCard(audioLibrary: MutableList<AudioItem>, context: Context, djMixerController: DJMixerController) {')
if 'var lastExportedItem' not in mt:
    mt = mt.replace('    var lastExport by remember { mutableStateOf("") }\n', '    var lastExport by remember { mutableStateOf("") }\n    var lastExportedItem by remember { mutableStateOf<AudioItem?>(null) }\n', 1)
mt = mt.replace('                        lastExport = "تم حفظ ${item.title}"\n', '                        lastExport = "تم حفظ ${item.title}"\n                        lastExportedItem = item\n', 1)
needle = '            } { Text("حفظ اللحن في مكتبة الأغاني") }\n'
addition = needle + '''\n            if (lastExportedItem != null) {\n                Spacer(modifier = Modifier.height(6.dp))\n                Button(\n                    onClick = { djMixerController.playMelodyOverDeckA(lastExportedItem!!) },\n                    modifier = Modifier.fillMaxWidth()\n                ) { Text("تشغيل اللحن فوق الأغنية في Deck A") }\n            }\n'''
if 'تشغيل اللحن فوق الأغنية في Deck A' not in mt and needle in mt:
    mt = mt.replace(needle, addition, 1)
ms.write_text(mt, encoding='utf-8')

print('DJ final wiring applied')
