package com.example.player

import android.content.Context
import android.media.AudioDeviceInfo
import androidx.compose.runtime.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.common.util.UnstableApi
import com.example.model.AudioItem
import kotlinx.coroutines.*
import kotlin.math.sin

enum class SamplerSound(val title: String, val category: String) {
    TABLA("Tabla", "Percussion"), DUFF("Duff", "Percussion"), SAGAT("Sagat", "Percussion"),
    BONGO("Bongo", "Percussion"), CONGA("Conga", "Percussion"), DARBUKA("Darbuka", "Percussion"),
    TIMPANI("Timpani", "Percussion"), SHAKER("Shaker", "Percussion"), TAMBOURINE("Tambourine", "Percussion"),
    CLAP("Clapping", "Crowd"), CROWD("Crowd Cheer", "Crowd"), HORN("DJ Horn", "FX"),
    SCRATCH("Scratch", "FX"), LASER("Laser FX", "FX"), WHISTLE("Whistle", "FX"),
    SIREN("Siren", "FX"), AIRHORN("Airhorn", "FX"), ZAP("Zap FX", "FX"),
    KICK("Kick Drum", "Drums"), SNARE("Snare Drum", "Drums"), HIHAT_C("Closed HH", "Drums"),
    HIHAT_O("Open HH", "Drums"), CRASH("Crash Cymbal", "Drums"), TOM("Tom Drum", "Drums"),
    BASS_DROP("Bass Drop", "Synth"), SYNTH_STAB("Synth Stab", "Synth")
}

class DJSoundPlayer(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    fun playSound(sound: SamplerSound) {
        // Implement simple playback if needed or stub out.
    }
}

class DJDeckController(private val context: Context, val deckName: String) {
    val fxProcessor = DeckFxAudioProcessor()
    val eqController = EqualizerController(context) { syncEq() }
    private fun syncEq() {
        val levels = eqController.bands.map { it.currentLevelDb.toFloat() }.toFloatArray()
        fxProcessor.setEqLevels(levels, eqController.isEnabled)
    }

    private val renderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
            return DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(fxProcessor))
                .build()
        }
    }

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context, renderersFactory).build()

    var currentSong by mutableStateOf<AudioItem?>(null)
    var isPlaying by mutableStateOf(false)
    var currentPositionMs by mutableStateOf(0L)
    var durationMs by mutableStateOf(0L)
    
    var pitch by mutableStateOf(1.0f)
    var volume by mutableStateOf(1.0f)

    var activeEffects = mutableStateMapOf<String, Boolean>()
    var fxAmount by mutableStateOf(0.5f)
    var beatDivision by mutableStateOf(0.25f)

    init {
        activeDecks.add(this)
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) pauseOthers()
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                } else if (state == Player.STATE_ENDED) {
                    isPlaying = false
                }
            }
        })
    }

    // --- GLOBAL PAUSE MECHANISM ---
    private fun pauseOthers() {
        AudioPlayerController.activeInstance?.pause()
    }

    fun isEffectActive(fxId: String): Boolean = activeEffects[fxId] == true

    fun toggleEffect(fxId: String) {
        val currentlyActive = activeEffects[fxId] ?: false
        activeEffects[fxId] = !currentlyActive
        updateProcessorEffects()
    }

    fun setEffectAmount(amount: Float) {
        fxAmount = amount.coerceIn(0f, 1f)
        fxProcessor.amount = fxAmount
    }

    fun setEffectBeatDivision(div: Float) {
        beatDivision = div
        fxProcessor.beatDivision = div
        // Not used in standard plugins yet, but can be passed down later
    }

    private fun updateProcessorEffects() {
        fxProcessor.activeEffects.clear()
        activeEffects.filterValues { it }.keys.forEach { fxId ->
            fxProcessor.activeEffects.add(fxId)
        }
    }

    fun loadTrack(song: AudioItem) {
        currentSong = song
        exoPlayer.setMediaItem(MediaItem.fromUri(song.uri))
        exoPlayer.prepare()
        currentPositionMs = 0L
    }

    fun play() {
        pauseOthers()
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun togglePlay() {
        if (isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        val safe = positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L))
        exoPlayer.seekTo(safe)
        currentPositionMs = safe
    }

    fun setPlaybackPitch(newPitch: Float) {
        pitch = newPitch.coerceIn(0.5f, 2.0f)
        exoPlayer.setPlaybackParameters(androidx.media3.common.PlaybackParameters(pitch, 1.0f))
    }

    fun setVolumeLevel(newVolume: Float) {
        volume = newVolume.coerceIn(0f, 1f)
        exoPlayer.volume = volume
    }

    fun updateProgress() {
        if (exoPlayer.isPlaying || durationMs == 0L) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration
            if (dur > 0L) durationMs = dur
        }
    }

    

    fun release() {
        activeDecks.remove(this)
        exoPlayer.release()
    }

    companion object {
        val activeDecks = mutableListOf<DJDeckController>()
    }
}

class DJMixerController(context: Context) {
    val deckA = DJDeckController(context, "A")
    val deckB = DJDeckController(context, "B")
    var crossfader by mutableStateOf(0.5f)
    val sampler = DJSoundPlayer(context)

    fun updateCrossfader(value: Float) {
        crossfader = value.coerceIn(0f, 1f)
        deckA.setVolumeLevel(if (crossfader <= 0.5f) 1f else 1f - (crossfader - 0.5f) * 2f)
        deckB.setVolumeLevel(if (crossfader >= 0.5f) 1f else crossfader * 2f)
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
