package com.example.djfx

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

class DjFxAudioEngine(private val context: Context) {
    private val maxPlayers = 8
    private val players = Array(maxPlayers) {
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(
                DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true))
            )
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }
    private var currentPlayerIndex = 0

    fun play(uri: String) {
        val player = players[currentPlayerIndex]
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
        currentPlayerIndex = (currentPlayerIndex + 1) % maxPlayers
    }

    fun release() {
        players.forEach { it.release() }
    }
}
