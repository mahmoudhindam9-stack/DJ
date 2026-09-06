package com.example.player

import android.content.Context
import android.content.Intent

/** Single notification/control router shared by Library, Online, Playlists, DJ decks and Studio. */
object PlaybackNotificationRouter {
    private data class Handler(
        val source: String,
        var title: String,
        var artist: String,
        var isPlaying: Boolean,
        val playPause: () -> Unit,
        val next: () -> Unit,
        val previous: () -> Unit,
        val stop: () -> Unit
    )

    private var active: Handler? = null

    @Synchronized
    fun activate(context: Context, source: String, title: String, artist: String, isPlaying: Boolean, playPause: () -> Unit, next: () -> Unit = {}, previous: () -> Unit = {}, stop: () -> Unit = {}) {
        active = Handler(source, title, artist, isPlaying, playPause, next, previous, stop)
        ensureService(context)
        MusicService.instance?.let { service -> service.updateNotification(title, artist, isPlaying) }
    }

    @Synchronized
    fun update(context: Context, source: String, title: String, artist: String, isPlaying: Boolean) {
        val current = active ?: return
        if (current.source != source) return
        current.title = title
        current.artist = artist
        current.isPlaying = isPlaying
        ensureService(context)
        MusicService.instance?.updateNotification(title, artist, isPlaying)
    }

    @Synchronized
    fun hasActiveSource(): Boolean = active != null

    @Synchronized
    fun attachService(service: MusicService) {
        active?.let { service.updateNotification(it.title, it.artist, it.isPlaying) }
    }

    @Synchronized
    fun clear(source: String) {
        if (active?.source == source) active = null
    }

    @Synchronized fun dispatchPlayPause(): Boolean = active?.let { it.playPause(); true } ?: false
    @Synchronized fun dispatchNext(): Boolean = active?.let { it.next(); true } ?: false
    @Synchronized fun dispatchPrevious(): Boolean = active?.let { it.previous(); true } ?: false
    @Synchronized fun dispatchStop(): Boolean = active?.let { it.stop(); true } ?: false

    private fun ensureService(context: Context) {
        if (MusicService.instance != null) return
        val intent = Intent(context.applicationContext, MusicService::class.java)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) context.applicationContext.startForegroundService(intent)
            else context.applicationContext.startService(intent)
        } catch (_: Throwable) { }
    }
}
