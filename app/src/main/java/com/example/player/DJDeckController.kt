package com.example.player

import android.content.Context
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.*
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.model.AudioItem
import kotlinx.coroutines.*
import kotlin.math.sin

enum class DJEffect(val displayName: String) {
    FLANGER("Flanger"),
    REVERB("Studio Reverb"),
    ECHO("Dub Echo"),
    CRUSH("Bit Crush")
}

enum class SamplerSound(val title: String, val category: String) {
    TABLA("Tabla", "Percussion"),
    DUFF("Duff", "Percussion"),
    SAGAT("Sagat", "Percussion"),
    BONGO("Bongo", "Percussion"),
    CONGA("Conga", "Percussion"),
    DARBUKA("Darbuka", "Percussion"),
    TIMPANI("Timpani", "Percussion"),
    SHAKER("Shaker", "Percussion"),
    TAMBOURINE("Tambourine", "Percussion"),
    CLAP("Clapping", "Crowd"),
    CROWD("Crowd Cheer", "Crowd"),
    HORN("DJ Horn", "FX"),
    SCRATCH("Scratch", "FX"),
    LASER("Laser FX", "FX"),
    WHISTLE("Whistle", "FX"),
    SIREN("Siren", "FX"),
    AIRHORN("Airhorn", "FX"),
    ZAP("Zap FX", "FX"),
    KICK("Kick Drum", "Drums"),
    SNARE("Snare Drum", "Drums"),
    HIHAT_C("Closed HH", "Drums"),
    HIHAT_O("Open HH", "Drums"),
    CRASH("Crash Cymbal", "Drums"),
    TOM("Tom Drum", "Drums"),
    BASS_DROP("Bass Drop", "Synth"),
    SYNTH_STAB("Synth Stab", "Synth")
}

class DJSoundPlayer(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun playSound(sound: SamplerSound) {
        scope.launch {
            try {
                val sampleRate = 22050
                val durationSec = when (sound) {
                    SamplerSound.TABLA -> 0.45
                    SamplerSound.DUFF -> 0.6
                    SamplerSound.SAGAT -> 0.25
                    SamplerSound.BONGO -> 0.35
                    SamplerSound.CONGA -> 0.4
                    SamplerSound.DARBUKA -> 0.45
                    SamplerSound.TIMPANI -> 0.8
                    SamplerSound.SHAKER -> 0.2
                    SamplerSound.TAMBOURINE -> 0.3
                    SamplerSound.CLAP -> 0.25
                    SamplerSound.CROWD -> 1.5
                    SamplerSound.HORN -> 0.5
                    SamplerSound.SCRATCH -> 0.35
                    SamplerSound.LASER -> 0.3
                    SamplerSound.WHISTLE -> 0.4
                    SamplerSound.SIREN -> 0.8
                    SamplerSound.AIRHORN -> 0.6
                    SamplerSound.ZAP -> 0.25
                    SamplerSound.KICK -> 0.3
                    SamplerSound.SNARE -> 0.25
                    SamplerSound.HIHAT_C -> 0.1
                    SamplerSound.HIHAT_O -> 0.3
                    SamplerSound.CRASH -> 1.2
                    SamplerSound.TOM -> 0.35
                    SamplerSound.BASS_DROP -> 1.0
                    SamplerSound.SYNTH_STAB -> 0.4
                }
                val numSamples = (sampleRate * durationSec).toInt()
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val progress = i.toDouble() / numSamples
                    val sampleVal = when (sound) {
                        SamplerSound.TABLA -> {
                            val freq = 190.0 * (1.0 - progress * 1.8)
                            val env = 1.0 - progress
                            (sin(2.0 * Math.PI * freq * t) * Short.MAX_VALUE * env).toInt()
                        }
                        SamplerSound.DUFF -> {
                            val noise = (Math.random() * 2.0 - 1.0)
                            val tone = sin(2.0 * Math.PI * 110.0 * t)
                            val env = Math.exp(-progress * 4.0)
                            ((tone * 0.4 + noise * 0.6) * Short.MAX_VALUE * env * 0.8).toInt()
                        }
                        SamplerSound.SAGAT -> {
                            val f1 = 3100.0
                            val f2 = 4500.0
                            val env = Math.exp(-progress * 12.0)
                            ((sin(2.0 * Math.PI * f1 * t) + sin(2.0 * Math.PI * f2 * t)) * Short.MAX_VALUE * 0.35 * env).toInt()
                        }
                        SamplerSound.BONGO -> {
                            val freq = 320.0 * (1.0 - progress * 2.0).coerceAtLeast(0.2)
                            val env = Math.exp(-progress * 6.0)
                            (sin(2.0 * Math.PI * freq * t) * Short.MAX_VALUE * env).toInt()
                        }
                        SamplerSound.CONGA -> {
                            val freq = 220.0 * (1.0 - progress * 1.5).coerceAtLeast(0.2)
                            val env = Math.exp(-progress * 5.0)
                            (sin(2.0 * Math.PI * freq * t) * Short.MAX_VALUE * env).toInt()
                        }
                        SamplerSound.DARBUKA -> {
                            val freq = 250.0 * (1.0 - progress * 2.5).coerceAtLeast(0.1)
                            val noise = (Math.random() * 2.0 - 1.0) * 0.3
                            val env = 1.0 - progress
                            ((sin(2.0 * Math.PI * freq * t) + noise) * Short.MAX_VALUE * env).toInt()
                        }
                        SamplerSound.TIMPANI -> {
                            val freq = 90.0 * (1.0 - progress * 0.5)
                            val env = Math.exp(-progress * 3.0)
                            (sin(2.0 * Math.PI * freq * t) * Short.MAX_VALUE * env * 0.9).toInt()
                        }
                        SamplerSound.SHAKER -> {
                            val noise = (Math.random() * 2.0 - 1.0)
                            val env = Math.sin(progress * Math.PI)
                            (noise * Short.MAX_VALUE * env * 0.4).toInt()
                        }
                        SamplerSound.TAMBOURINE -> {
                            val noise = (Math.random() * 2.0 - 1.0)
                            val metal = sin(2.0 * Math.PI * 3500.0 * t)
                            val env = Math.exp(-progress * 8.0)
                            ((metal * 0.3 + noise * 0.7) * Short.MAX_VALUE * env * 0.6).toInt()
                        }
                        SamplerSound.CLAP -> {
                            val noise = (Math.random() * 2.0 - 1.0)
                            val env = if (progress < 0.15) progress * 6.0 else Math.exp(-(progress - 0.15) * 10.0)
                            (noise * Short.MAX_VALUE * env * 0.85).toInt()
                        }
                        SamplerSound.CROWD -> {
                            val noise = (Math.random() * 2.0 - 1.0)
                            val mod = sin(2.0 * Math.PI * 3.0 * t) * 0.4 + 0.6
                            val env = Math.sin(progress * Math.PI)
                            (noise * Short.MAX_VALUE * env * mod * 0.6).toInt()
                        }
                        SamplerSound.HORN -> {
                            val f1 = 360.0
                            val f2 = 480.0
                            val env = if (progress > 0.85) (1.0 - progress) * 6.0 else 1.0
                            ((sin(2.0 * Math.PI * f1 * t) + sin(2.0 * Math.PI * f2 * t)) * Short.MAX_VALUE * 0.45 * env).toInt()
                        }
                        SamplerSound.SCRATCH -> {
                            val freq = 350.0 + sin(2.0 * Math.PI * 30.0 * t) * 800.0
                            val noise = (Math.random() * 2.0 - 1.0) * 0.4
                            val env = 1.0 - progress
                            ((sin(2.0 * Math.PI * freq * t) + noise) * Short.MAX_VALUE * env * 0.7).toInt()
                        }
                        SamplerSound.LASER -> {
                            val freq = 1400.0 * (1.0 - progress).coerceAtLeast(0.05)
                            val env = 1.0 - progress
                            (sin(2.0 * Math.PI * freq * t) * Short.MAX_VALUE * env * 0.7).toInt()
                        }
                        SamplerSound.WHISTLE -> {
                            val freq = 2600.0 + sin(2.0 * Math.PI * 15.0 * t) * 300.0
                            val env = Math.sin(progress * Math.PI)
                            (sin(2.0 * Math.PI * freq * t) * Short.MAX_VALUE * env * 0.6).toInt()
                        }
                        SamplerSound.SIREN -> {
                            val freq = 600.0 + sin(2.0 * Math.PI * 4.0 * t) * 300.0
                            val env = Math.sin(progress * Math.PI)
                            (sin(2.0 * Math.PI * freq * t) * Short.MAX_VALUE * env * 0.6).toInt()
                        }
                        SamplerSound.AIRHORN -> {
                            val f1 = 440.0
                            val f2 = 554.0
                            val f3 = 659.0
                            val env = 1.0 - progress * 0.5
                            ((sin(2.0 * Math.PI * f1 * t) + sin(2.0 * Math.PI * f2 * t) + sin(2.0 * Math.PI * f3 * t)) * Short.MAX_VALUE * 0.3 * env).toInt()
                        }
                        SamplerSound.ZAP -> {
                            val freq = 2000.0 * Math.exp(-progress * 5.0)
                            val noise = (Math.random() * 2.0 - 1.0) * 0.5
                            ((sin(2.0 * Math.PI * freq * t) + noise) * Short.MAX_VALUE * (1.0 - progress)).toInt()
                        }
                        SamplerSound.KICK -> {
                            val freq = 130.0 * (1.0 - progress * 4.0).coerceAtLeast(0.05)
                            val env = 1.0 - progress
                            (sin(2.0 * Math.PI * freq * t) * Short.MAX_VALUE * env).toInt()
                        }
                        SamplerSound.SNARE -> {
                            val tone = sin(2.0 * Math.PI * 240.0 * t)
                            val noise = (Math.random() * 2.0 - 1.0)
                            val env = Math.exp(-progress * 7.0)
                            ((tone * 0.3 + noise * 0.7) * Short.MAX_VALUE * env).toInt()
                        }
                        SamplerSound.HIHAT_C -> {
                            val noise = (Math.random() * 2.0 - 1.0)
                            val env = Math.exp(-progress * 25.0)
                            (noise * Short.MAX_VALUE * env * 0.5).toInt()
                        }
                        SamplerSound.HIHAT_O -> {
                            val noise = (Math.random() * 2.0 - 1.0)
                            val env = Math.exp(-progress * 4.0)
                            (noise * Short.MAX_VALUE * env * 0.5).toInt()
                        }
                        SamplerSound.CRASH -> {
                            val noise = (Math.random() * 2.0 - 1.0)
                            val ring = sin(2.0 * Math.PI * 2800.0 * t) * 0.2
                            val env = Math.exp(-progress * 3.5)
                            ((noise * 0.8 + ring) * Short.MAX_VALUE * env * 0.7).toInt()
                        }
                        SamplerSound.TOM -> {
                            val freq = 160.0 * (1.0 - progress * 2.0).coerceAtLeast(0.1)
                            val env = Math.exp(-progress * 5.0)
                            (sin(2.0 * Math.PI * freq * t) * Short.MAX_VALUE * env).toInt()
                        }
                        SamplerSound.BASS_DROP -> {
                            val freq = 100.0 * (1.0 - progress).coerceAtLeast(0.02)
                            val env = Math.sin(progress * Math.PI)
                            (sin(2.0 * Math.PI * freq * t) * Short.MAX_VALUE * env).toInt()
                        }
                        SamplerSound.SYNTH_STAB -> {
                            val f1 = 440.0
                            val f2 = 880.0
                            val env = Math.exp(-progress * 4.0)
                            ((sin(2.0 * Math.PI * f1 * t) + sin(2.0 * Math.PI * f2 * t)) * Short.MAX_VALUE * 0.4 * env).toInt()
                        }
                    }
                    buffer[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()

                Thread.sleep((durationSec * 1000).toLong() + 50)
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

class DJDeck(context: Context, val deckName: String) {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    var track by mutableStateOf<AudioItem?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var pitchSpeed by mutableStateOf(1.0f)
        private set

    var volume by mutableStateOf(0.8f)
        private set

    var currentPositionMs by mutableStateOf(0L)
        private set

    var durationMs by mutableStateOf(0L)
        private set

    var isFlangerActive by mutableStateOf(false)
    var isReverbActive by mutableStateOf(false)
    var isEchoActive by mutableStateOf(false)
    var isCrushActive by mutableStateOf(false)

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        })
    }

    fun loadTrack(audioItem: AudioItem) {
        track = audioItem
        exoPlayer.setMediaItem(MediaItem.fromUri(audioItem.uri))
        exoPlayer.prepare()
        currentPositionMs = 0L
        durationMs = 0L
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        currentPositionMs = positionMs
    }

    fun updateProgress() {
        if (exoPlayer.isPlaying || durationMs == 0L) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration
            if (dur > 0L) {
                durationMs = dur
            }
        }
    }

    fun togglePlayPause(onPlayStarted: () -> Unit = {}) {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (track != null) {
                onPlayStarted()
                exoPlayer.play()
            }
        }
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun setPitchAndSpeed(newRate: Float) {
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

    fun release() {
        exoPlayer.release()
    }
}

class DJMixerController(context: Context) {
    val deckA = DJDeck(context, "Deck A")
    val deckB = DJDeck(context, "Deck B")
    val soundPlayer = DJSoundPlayer(context)

    var crossfader by mutableStateOf(0.5f)
        private set

    fun updateCrossfader(position: Float) {
        crossfader = position.coerceIn(0f, 1f)
        val volA = (1f - crossfader) * deckA.volume
        val volB = crossfader * deckB.volume
        deckA.exoPlayer.volume = if (deckA.isEchoActive) (volA * 1.15f).coerceAtMost(1f) else volA
        deckB.exoPlayer.volume = if (deckB.isEchoActive) (volB * 1.15f).coerceAtMost(1f) else volB
    }

    fun pauseAll() {
        deckA.pause()
        deckB.pause()
    }

    fun release() {
        deckA.release()
        deckB.release()
    }
}
