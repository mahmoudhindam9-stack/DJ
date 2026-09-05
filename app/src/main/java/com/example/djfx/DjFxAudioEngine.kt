package com.example.djfx

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class DjFxAudioEngine(private val context: Context) {
    private val maxPlayers = 8
    private val players = Array(maxPlayers) {
        val factory = DefaultMediaSourceFactory(context).setDataSourceFactory(
            DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true))
        )
        ExoPlayer.Builder(context).setMediaSourceFactory(factory).build()
    }
    private var currentPlayerIndex = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheDir = File(context.filesDir, "djfx_factory_cache").apply { mkdirs() }

    fun play(uri: String) {
        scope.launch {
            runCatching {
                val resolved = if (uri.startsWith("http://") || uri.startsWith("https://")) cacheRemote(uri) else uri
                withContext(Dispatchers.Main) {
                    val player = players[currentPlayerIndex]
                    player.setMediaItem(MediaItem.fromUri(resolved))
                    player.prepare()
                    player.play()
                    currentPlayerIndex = (currentPlayerIndex + 1) % maxPlayers
                }
            }
        }
    }

    private fun cacheRemote(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val ext = url.substringAfterLast('.', "ogg").substringBefore('?').lowercase()
        val file = File(cacheDir, "$digest.$ext")
        if (!file.exists() || file.length() < 128L) {
            java.net.URL(url).openStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        }
        return Uri.fromFile(file).toString()
    }

    fun release() {
        scope.cancel()
        players.forEach { it.release() }
    }
}
