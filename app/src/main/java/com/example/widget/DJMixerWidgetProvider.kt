package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.R
import com.example.MainActivity

class DJMixerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = ids.forEach { updateOne(context, manager, it) }

    companion object {
        fun requestAllUpdates(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, DJMixerWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val a = context.getSharedPreferences("dj_deck_Deck_A", Context.MODE_PRIVATE)
            val b = context.getSharedPreferences("dj_deck_Deck_B", Context.MODE_PRIVATE)
            val views = RemoteViews(context.packageName, R.layout.dj_mixer_widget)
            views.setTextViewText(R.id.deck_a_title, a.getString("track_title", "Deck A — Empty") ?: "Deck A — Empty")
            views.setTextViewText(R.id.deck_a_artist, a.getString("track_artist", "Load a track") ?: "Load a track")
            views.setTextViewText(R.id.deck_b_title, b.getString("track_title", "Deck B — Empty") ?: "Deck B — Empty")
            views.setTextViewText(R.id.deck_b_artist, b.getString("track_artist", "Load a track") ?: "Load a track")
            val pending = PendingIntent.getActivity(context, id * 31, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.dj_widget_open, pending)
            manager.updateAppWidget(id, views)
        }
    }
}
