package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.R
import com.example.player.AudioPlayerController
import com.example.player.MusicService

class MusicWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("dj_player_session", Context.MODE_PRIVATE)
        val eq = context.getSharedPreferences("quick_eq", Context.MODE_PRIVATE)
        val title = prefs.getString(AudioPlayerController.KEY_TITLE, "مشغل الموسيقى") ?: "مشغل الموسيقى"
        val artist = prefs.getString(AudioPlayerController.KEY_ARTIST, "موسيقى") ?: "موسيقى"
        val playing = prefs.getBoolean("playing", false)
        appWidgetIds.forEach { id ->
            updateAppWidget(context, appWidgetManager, id, title, artist, playing, eq.getInt("bass", 0), eq.getInt("mid", 0), eq.getInt("treble", 0))
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, title: String, artist: String, isPlaying: Boolean) {
            val eq = context.getSharedPreferences("quick_eq", Context.MODE_PRIVATE)
            updateAppWidget(context, appWidgetManager, appWidgetId, title, artist, isPlaying, eq.getInt("bass", 0), eq.getInt("mid", 0), eq.getInt("treble", 0))
        }

        private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, title: String, artist: String, isPlaying: Boolean, bass: Int, mid: Int, treble: Int) {
            val views = RemoteViews(context.packageName, R.layout.music_widget)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            views.setTextViewText(R.id.widget_status, if (isPlaying) "▶ Playing" else "⏸ Paused")
            views.setImageViewResource(R.id.widget_btn_play, if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            views.setProgressBar(R.id.widget_eq_bass, 12, (bass + 6).coerceIn(0, 12), false)
            views.setProgressBar(R.id.widget_eq_mid, 12, (mid + 6).coerceIn(0, 12), false)
            views.setProgressBar(R.id.widget_eq_treble, 12, (treble + 6).coerceIn(0, 12), false)
            val base = appWidgetId * 10
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            views.setOnClickPendingIntent(R.id.widget_btn_prev, PendingIntent.getService(context, base, Intent(context, MusicService::class.java).setAction(MusicService.ACTION_PREV), flags))
            views.setOnClickPendingIntent(R.id.widget_btn_play, PendingIntent.getService(context, base + 1, Intent(context, MusicService::class.java).setAction(MusicService.ACTION_TOGGLE_PLAY), flags))
            views.setOnClickPendingIntent(R.id.widget_btn_next, PendingIntent.getService(context, base + 2, Intent(context, MusicService::class.java).setAction(MusicService.ACTION_NEXT), flags))
            views.setOnClickPendingIntent(R.id.widget_eq_bass, PendingIntent.getService(context, base + 3, Intent(context, MusicService::class.java).setAction(MusicService.ACTION_EQ_BASS), flags))
            views.setOnClickPendingIntent(R.id.widget_eq_mid, PendingIntent.getService(context, base + 4, Intent(context, MusicService::class.java).setAction(MusicService.ACTION_EQ_MID), flags))
            views.setOnClickPendingIntent(R.id.widget_eq_treble, PendingIntent.getService(context, base + 5, Intent(context, MusicService::class.java).setAction(MusicService.ACTION_EQ_TREBLE), flags))
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
