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

class QuickPlayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = ids.forEach { updateOne(context, manager, it) }

    companion object {
        fun requestAllUpdates(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, QuickPlayerWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val snapshot = PlaybackNotificationRouter.activeSnapshot(context)
            val views = RemoteViews(context.packageName, R.layout.quick_player_widget)
            views.setTextViewText(R.id.quick_title, snapshot.first)
            views.setTextViewText(R.id.quick_artist, snapshot.second)
            views.setTextViewText(R.id.quick_status, if (snapshot.third) "▶ Playing" else "⏸ Paused")
            views.setImageViewResource(R.id.quick_play, if (snapshot.third) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            val base = id * 20
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            views.setOnClickPendingIntent(R.id.quick_prev, PendingIntent.getBroadcast(context, base, Intent(context, WidgetActionReceiver::class.java).setAction(com.example.player.MusicService.ACTION_PREV), flags))
            views.setOnClickPendingIntent(R.id.quick_play, PendingIntent.getBroadcast(context, base + 1, Intent(context, WidgetActionReceiver::class.java).setAction(com.example.player.MusicService.ACTION_TOGGLE_PLAY), flags))
            views.setOnClickPendingIntent(R.id.quick_next, PendingIntent.getBroadcast(context, base + 2, Intent(context, WidgetActionReceiver::class.java).setAction(com.example.player.MusicService.ACTION_NEXT), flags))
            val open = PendingIntent.getActivity(context, base + 3, Intent(context, MainActivity::class.java), flags)
            views.setOnClickPendingIntent(R.id.quick_title, open)
            manager.updateAppWidget(id, views)
        }
    }
}
