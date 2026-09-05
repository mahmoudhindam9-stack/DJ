package com.example.djfx

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File
import java.net.URI

class DjFxAudioEngine(private val context: Context) {
    private val maxPlayers = 8
    private val players = Array(maxPlayers) {
        val factory = DefaultMediaSourceFactory(context).setDataSourceFactory(
            DefaultDataSource.Factory(
                context,
                DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
            )
        )
        ExoPlayer.Builder(context).setMediaSourceFactory(factory).build()
    }
    private var currentPlayerIndex = 0

    fun play(uri: String) {
        val normalized = normalizeUri(uri)
        val player = players[currentPlayerIndex]
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(normalized))
        player.prepare()
        player.play()
        currentPlayerIndex = (currentPlayerIndex + 1) % maxPlayers
    }

    private fun normalizeUri(uri: String): String {
        return when {
            uri.startsWith("asset:///") -> uri
            uri.startsWith("file:///") -> uri
            uri.startsWith("http://") || uri.startsWith("https://") -> uri
            File(uri).exists() -> "file://${File(uri).absolutePath}"
            else -> uri
        }
    }

    fun release() {
        players.forEach { it.release() }
    }
}
