package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.R
import com.example.player.PlaybackNotificationRouter

class MusicWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = ids.forEach { updateOne(context, manager, it) }

    companion object {
        fun requestAllUpdates(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, MusicWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateOne(context, manager, it) }
        }

        fun updateAppWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int, title: String, artist: String, isPlaying: Boolean) {
            val eq = context.getSharedPreferences("quick_eq", Context.MODE_PRIVATE)
            updateOne(context, manager, appWidgetId, title, artist, isPlaying, eq.getInt("bass", 0), eq.getInt("mid", 0), eq.getInt("treble", 0))
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val snapshot = PlaybackNotificationRouter.activeSnapshot(context)
            val eq = context.getSharedPreferences("quick_eq", Context.MODE_PRIVATE)
            updateOne(context, manager, id, snapshot.first, snapshot.second, snapshot.third, eq.getInt("bass", 0), eq.getInt("mid", 0), eq.getInt("treble", 0))
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int, title: String, artist: String, isPlaying: Boolean, bass: Int, mid: Int, treble: Int) {
            val views = RemoteViews(context.packageName, R.layout.music_widget)
            
            val weatherPrefs = context.getSharedPreferences("time_weather_widget", Context.MODE_PRIVATE)
            views.setTextViewText(R.id.weather_city, weatherPrefs.getString("city", "Current location") ?: "Current location")
            views.setTextViewText(R.id.weather_temp, weatherPrefs.getString("temp", "--°C") ?: "--°C")
            val zone = weatherPrefs.getString("timezone", java.util.TimeZone.getDefault().id) ?: java.util.TimeZone.getDefault().id
            views.setString(R.id.weather_clock, "setTimeZone", zone)
            val refresh = PendingIntent.getActivity(context, id * 41, Intent(context, LocationWeatherActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.weather_refresh, refresh)

            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            views.setTextViewText(R.id.widget_status, if (isPlaying) "▶ Playing" else "⏸ Paused")
            views.setImageViewResource(R.id.widget_btn_play, if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            views.setProgressBar(R.id.widget_eq_bass, 12, (bass + 6).coerceIn(0, 12), false)
            views.setProgressBar(R.id.widget_eq_mid, 12, (mid + 6).coerceIn(0, 12), false)
            views.setProgressBar(R.id.widget_eq_treble, 12, (treble + 6).coerceIn(0, 12), false)
            
            views.setOnClickPendingIntent(R.id.widget_eq_bass, PendingIntent.getService(context, id * 10 + 1, Intent(context, com.example.player.MusicService::class.java).setAction(com.example.player.MusicService.ACTION_EQ_BASS), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.widget_eq_mid, PendingIntent.getService(context, id * 10 + 2, Intent(context, com.example.player.MusicService::class.java).setAction(com.example.player.MusicService.ACTION_EQ_MID), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.widget_eq_treble, PendingIntent.getService(context, id * 10 + 3, Intent(context, com.example.player.MusicService::class.java).setAction(com.example.player.MusicService.ACTION_EQ_TREBLE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            
            WidgetPlaybackIntents.wireButtons(context, views, id, R.id.widget_btn_prev, R.id.widget_btn_play, R.id.widget_btn_next)
            manager.updateAppWidget(id, views)
        }
    }
}
