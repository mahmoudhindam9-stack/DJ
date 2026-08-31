package com.example.player

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.model.AudioItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class RepeatOption {
    OFF, ALL, ONE
}

class AudioPlayerController(private val context: Context) {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    var playlist = mutableStateListOf<AudioItem>()
        private set

    var currentSongIndex by mutableStateOf(-1)
        private set

    var currentSong by mutableStateOf<AudioItem?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var currentPositionMs by mutableStateOf(0L)
        private set

    var durationMs by mutableStateOf(0L)
        private set

    var isShuffle by mutableStateOf(false)
        private set

    var repeatOption by mutableStateOf(RepeatOption.OFF)
        private set

    var volume by mutableStateOf(1f)
        private set

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                syncNotification()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                } else if (playbackState == Player.STATE_ENDED) {
                    handleTrackEnded()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = exoPlayer.currentMediaItemIndex
                if (index in playlist.indices) {
                    currentSongIndex = index
                    currentSong = playlist[index]
                    syncNotification()
                }
            }
        })
    }

    private fun syncNotification() {
        try {
            val serviceIntent = Intent(context, MusicService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            MusicService.instance?.playerController = this
            MusicService.instance?.updateNotification(
                currentSong?.title ?: "مشغل الموسيقى",
                currentSong?.artist ?: "موسيقى",
                isPlaying
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setQueue(songs: List<AudioItem>, startIndex: Int = 0) {
        playlist.clear()
        playlist.addAll(songs)
        if (playlist.isNotEmpty() && startIndex in playlist.indices) {
            currentSongIndex = startIndex
            currentSong = playlist[startIndex]
            val mediaItems = playlist.map { MediaItem.fromUri(it.uri) }
            exoPlayer.setMediaItems(mediaItems, startIndex, 0L)
            exoPlayer.prepare()
        }
    }

    fun playSong(song: AudioItem, fullQueue: List<AudioItem> = playlist) {
        if (fullQueue != playlist) {
            playlist.clear()
            playlist.addAll(fullQueue)
        }
        val targetIndex = playlist.indexOfFirst { it.id == song.id }
        if (targetIndex != -1) {
            val mediaItems = playlist.map { MediaItem.fromUri(it.uri) }
            exoPlayer.setMediaItems(mediaItems, targetIndex, 0L)
            exoPlayer.prepare()
            exoPlayer.play()
            currentSongIndex = targetIndex
            currentSong = song
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0)
            }
            exoPlayer.play()
        }
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        if (isShuffle) {
            val randomIndex = (playlist.indices).random()
            currentSongIndex = randomIndex
        } else {
            currentSongIndex = (currentSongIndex + 1) % playlist.size
        }
        currentSong = playlist.getOrNull(currentSongIndex)
        currentSong?.let {
            exoPlayer.seekTo(currentSongIndex, 0L)
            exoPlayer.play()
        }
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return
        if (currentPositionMs > 3000) {
            exoPlayer.seekTo(0)
            return
        }
        if (isShuffle) {
            val randomIndex = (playlist.indices).random()
            currentSongIndex = randomIndex
        } else {
            currentSongIndex = if (currentSongIndex - 1 < 0) playlist.size - 1 else currentSongIndex - 1
        }
        currentSong = playlist.getOrNull(currentSongIndex)
        currentSong?.let {
            exoPlayer.seekTo(currentSongIndex, 0L)
            exoPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        currentPositionMs = positionMs
    }

    fun toggleShuffle() {
        isShuffle = !isShuffle
        exoPlayer.shuffleModeEnabled = isShuffle
    }

    fun toggleRepeat() {
        repeatOption = when (repeatOption) {
            RepeatOption.OFF -> RepeatOption.ALL
            RepeatOption.ALL -> RepeatOption.ONE
            RepeatOption.ONE -> RepeatOption.OFF
        }
        exoPlayer.repeatMode = when (repeatOption) {
            RepeatOption.OFF -> Player.REPEAT_MODE_OFF
            RepeatOption.ALL -> Player.REPEAT_MODE_ALL
            RepeatOption.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    fun setVolumeLevel(newVolume: Float) {
        volume = newVolume.coerceIn(0f, 1f)
        exoPlayer.volume = volume
    }

    private fun handleTrackEnded() {
        when (repeatOption) {
            RepeatOption.ONE -> {
                exoPlayer.seekTo(0)
                exoPlayer.play()
            }
            RepeatOption.ALL -> {
                playNext()
            }
            RepeatOption.OFF -> {
                if (currentSongIndex < playlist.size - 1) {
                    playNext()
                } else {
                    isPlaying = false
                }
            }
        }
    }

    fun updateProgress() {
        if (exoPlayer.isPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (durationMs <= 0L) {
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
            }
        }
    }

    fun release() {
        exoPlayer.release()
    }
}
