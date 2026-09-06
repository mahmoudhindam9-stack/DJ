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

    private val handlers = LinkedHashMap<String, Handler>()
    private var activeSource: String? = null

    @Synchronized
    fun activate(
        context: Context,
        source: String,
        title: String,
        artist: String,
        isPlaying: Boolean,
        playPause: () -> Unit,
        next: () -> Unit = {},
        previous: () -> Unit = {},
        stop: () -> Unit = {}
    ) {
        handlers.remove(source)
        handlers[source] = Handler(source, title, artist, isPlaying, playPause, next, previous, stop)
        if (isPlaying || activeSource == null) activeSource = source
        publish(context)
    }

    @Synchronized
    fun update(context: Context, source: String, title: String, artist: String, isPlaying: Boolean) {
        val current = handlers[source]
        if (current == null) {
            return
        }
        current.title = title
        current.artist = artist
        current.isPlaying = isPlaying
        if (isPlaying) {
            activeSource = source
        } else if (activeSource == source) {
            activeSource = handlers.values.lastOrNull { it.isPlaying }?.source ?: source
        }
        publish(context)
    }

    @Synchronized
    fun hasActiveSource(): Boolean = activeSource != null && handlers.containsKey(activeSource)

    @Synchronized
    fun attachService(service: MusicService) {
        activeHandler()?.let { handler ->
            service.updateNotification(handler.title, handler.artist, handler.isPlaying)
        }
    }

    @Synchronized
    fun clear(source: String) {
        handlers.remove(source)
        if (activeSource == source) {
            activeSource = handlers.values.lastOrNull { it.isPlaying }?.source
                ?: handlers.keys.lastOrNull()
        }
    }

    @Synchronized
    fun dispatchPlayPause(): Boolean = activeHandler()?.let { it.playPause(); true } ?: false

    @Synchronized
    fun dispatchNext(): Boolean = activeHandler()?.let { it.next(); true } ?: false

    @Synchronized
    fun dispatchPrevious(): Boolean = activeHandler()?.let { it.previous(); true } ?: false

    @Synchronized
    fun dispatchStop(): Boolean = activeHandler()?.let { it.stop(); true } ?: false

    @Synchronized
    private fun activeHandler(): Handler? {
        return activeSource?.let { handlers[it] }
    }

    @Synchronized
    private fun publish(context: Context) {
        val handler = activeHandler() ?: return
        ensureService(context)
        MusicService.instance?.updateNotification(handler.title, handler.artist, handler.isPlaying)
    }

    private fun ensureService(context: Context) {
        if (MusicService.instance != null) return
        val intent = Intent(context.applicationContext, MusicService::class.java)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.applicationContext.startForegroundService(intent)
            } else {
                context.applicationContext.startService(intent)
            }
        } catch (_: Throwable) {
        }
    }
}
