package com.example.player

import android.content.Context
import androidx.compose.runtime.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.model.AudioItem
import kotlinx.coroutines.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DJDeckController(private val context: Context, val deckName: String) {
    // Wires the DSP plugin library into this deck's audio path. Without this,
    // the plugin chain stays empty and every effect toggle is a no-op even
    // though the UI shows it as "on".
    val fxProcessor = DeckFxAudioProcessor().apply { initContext(context) }
    val eqController = EqualizerController(context)

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
        
        if (!currentlyActive && fxId.startsWith("voice_")) {
            activeEffects.keys.toList().filter { it.startsWith("voice_") }.forEach {
                activeEffects[it] = false
            }
        }
        
        activeEffects[fxId] = !currentlyActive
        
        var newPitch = 1.0f
        if (activeEffects["voice_woman"] == true) newPitch = 1.4f
        else if (activeEffects["voice_kid"] == true) newPitch = 1.6f
        else if (activeEffects["voice_chipmunk"] == true) newPitch = 2.0f
        else if (activeEffects["voice_monster"] == true) newPitch = 0.7f
        else if (activeEffects["voice_demon"] == true) newPitch = 0.5f
        else if (activeEffects["voice_giant"] == true) newPitch = 0.6f
        
        setPlaybackPitch(newPitch)
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
        fxProcessor.activeEffects = activeEffects.filterValues { it }.keys.toSet()
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
        // Make sure it actually applies to ExoPlayer correctly
        try {
            exoPlayer.volume = volume
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        eqController.release()
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

    fun updateCrossfader(value: Float) {
        crossfader = value.coerceIn(0f, 1f)
        // Logarithmic volume curve for DJ mixers instead of linear
        val volA = kotlin.math.cos(crossfader * (kotlin.math.PI / 2)).toFloat().coerceIn(0f, 1f)
        val volB = kotlin.math.sin(crossfader * (kotlin.math.PI / 2)).toFloat().coerceIn(0f, 1f)
        deckA.setVolumeLevel(volA)
        deckB.setVolumeLevel(volB)
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
