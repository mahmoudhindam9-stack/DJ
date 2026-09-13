package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.player.PlaybackNotificationRouter

class MusicWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            requestAllUpdates(context)
        }
    }

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
            
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            views.setTextViewText(R.id.widget_status, if (isPlaying) "▶ Playing" else "⏸ Paused")
            views.setImageViewResource(R.id.widget_btn_play, if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            
            val progress = PlaybackNotificationRouter.activeProgress(context)
            val positionMs = progress.first
            val durationMs = progress.second
            views.setTextViewText(R.id.widget_time_current, com.example.utils.MusicScanner.formatMs(positionMs))
            views.setTextViewText(R.id.widget_time_total, com.example.utils.MusicScanner.formatMs(durationMs))
            val pct = if (durationMs > 0) ((positionMs * 1000) / durationMs).toInt().coerceIn(0, 1000) else 0
            views.setProgressBar(R.id.widget_progress, 1000, pct, false)

            views.setProgressBar(R.id.widget_eq_bass, 12, (bass + 6).coerceIn(0, 12), false)
            views.setProgressBar(R.id.widget_eq_mid, 12, (mid + 6).coerceIn(0, 12), false)
            views.setProgressBar(R.id.widget_eq_treble, 12, (treble + 6).coerceIn(0, 12), false)
            
            views.setOnClickPendingIntent(R.id.widget_eq_bass, PendingIntent.getService(context, id * 10 + 1, Intent(context, com.example.player.MusicService::class.java).setAction(com.example.player.MusicService.ACTION_EQ_BASS), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.widget_eq_mid, PendingIntent.getService(context, id * 10 + 2, Intent(context, com.example.player.MusicService::class.java).setAction(com.example.player.MusicService.ACTION_EQ_MID), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.widget_eq_treble, PendingIntent.getService(context, id * 10 + 3, Intent(context, com.example.player.MusicService::class.java).setAction(com.example.player.MusicService.ACTION_EQ_TREBLE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            
            WidgetPlaybackIntents.wireButtons(context, views, id, R.id.widget_btn_prev, R.id.widget_btn_play, R.id.widget_btn_next)
            
            // Open player on click
            val openPlayerIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("open_route", "player")
            }
            val openPlayerPending = PendingIntent.getActivity(context, id * 42, openPlayerIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_title, openPlayerPending)
            views.setOnClickPendingIntent(R.id.widget_artist, openPlayerPending)
            views.setOnClickPendingIntent(R.id.widget_music_container, openPlayerPending)

            manager.updateAppWidget(id, views)
        }
    }
}
