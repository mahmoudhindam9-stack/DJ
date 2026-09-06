package com.example.player

import android.content.Context
import android.content.Intent
import com.example.widget.DJMixerWidgetProvider
import com.example.widget.MusicWidgetProvider
import com.example.widget.QuickPlayerWidgetProvider

/** One source of truth for the active playback shown by notifications and home-screen widgets. */
object PlaybackNotificationRouter {
    private const val PREFS = "active_playback_widget"
    private const val KEY_SOURCE = "source"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_PLAYING = "playing"

    private data class Handler(val source: String, var title: String, var artist: String, var isPlaying: Boolean, val playPause: () -> Unit, val next: () -> Unit, val previous: () -> Unit, val stop: () -> Unit)
    private var active: Handler? = null
    private var appContext: Context? = null

    @Synchronized
    fun activate(context: Context, source: String, title: String, artist: String, isPlaying: Boolean, playPause: () -> Unit, next: () -> Unit = {}, previous: () -> Unit = {}, stop: () -> Unit = {}) {
        appContext = context.applicationContext
        active = Handler(source, title, artist, isPlaying, playPause, next, previous, stop)
        persist(context, source, title, artist, isPlaying)
        ensureService(context)
        MusicService.instance?.updateNotification(title, artist, isPlaying)
    }

    @Synchronized
    fun update(context: Context, source: String, title: String, artist: String, isPlaying: Boolean) {
        val current = active ?: return
        if (current.source != source) return
        current.title = title
        current.artist = artist
        current.isPlaying = isPlaying
        persist(context, source, title, artist, isPlaying)
        ensureService(context)
        MusicService.instance?.updateNotification(title, artist, isPlaying)
    }

    @Synchronized fun hasActiveSource(): Boolean = active != null

    @Synchronized fun attachService(service: MusicService) { active?.let { service.updateNotification(it.title, it.artist, it.isPlaying) } }

    @Synchronized
    fun clear(source: String) {
        if (active?.source == source) {
            active = null
            appContext?.let { context -> persist(context, "", "مشغل الموسيقى", "موسيقى", false) }
        }
    }

    @Synchronized fun dispatchPlayPause(): Boolean = active?.let { it.playPause(); true } ?: false
    @Synchronized fun dispatchNext(): Boolean = active?.let { it.next(); true } ?: false
    @Synchronized fun dispatchPrevious(): Boolean = active?.let { it.previous(); true } ?: false
    @Synchronized fun dispatchStop(): Boolean = active?.let { it.stop(); true } ?: false

    private fun persist(context: Context, source: String, title: String, artist: String, isPlaying: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SOURCE, source).putString(KEY_TITLE, title).putString(KEY_ARTIST, artist).putBoolean(KEY_PLAYING, isPlaying).apply()
        if (source == "player") {
            context.getSharedPreferences("dj_player_session", Context.MODE_PRIVATE).edit()
                .putString(AudioPlayerController.KEY_TITLE, title).putString(AudioPlayerController.KEY_ARTIST, artist).putBoolean("playing", isPlaying).apply()
        }
        MusicWidgetProvider.requestAllUpdates(context)
        QuickPlayerWidgetProvider.requestAllUpdates(context)
        DJMixerWidgetProvider.requestAllUpdates(context)
    }

    fun activeSnapshot(context: Context): Triple<String, String, Boolean> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Triple(prefs.getString(KEY_TITLE, "مشغل الموسيقى") ?: "مشغل الموسيقى", prefs.getString(KEY_ARTIST, "موسيقى") ?: "موسيقى", prefs.getBoolean(KEY_PLAYING, false))
    }

    private fun ensureService(context: Context) {
        if (MusicService.instance != null) return
        val intent = Intent(context.applicationContext, MusicService::class.java)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) context.applicationContext.startForegroundService(intent)
            else context.applicationContext.startService(intent)
        } catch (_: Throwable) { }
    }
}
