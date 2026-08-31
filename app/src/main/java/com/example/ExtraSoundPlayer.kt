package com.example

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
