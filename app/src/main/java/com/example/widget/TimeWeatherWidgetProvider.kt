package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.R
import com.example.player.MusicService
import com.example.player.PlaybackNotificationRouter

class TimeWeatherWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_REFRESH = "com.example.widget.ACTION_REFRESH_WEATHER"
        private const val PREFS = "time_weather_widget"
        private const val CITY = "city"
        private const val TEMP = "temp"
        private const val CONDITION = "condition"
        private const val TIMEZONE = "timezone"
        private const val STATUS = "status"

        fun setStatus(context: Context, status: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(STATUS, status).apply()
            updateAll(context)
        }

        fun updateWeather(context: Context, city: String, temperature: String, condition: String, timezone: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(CITY, city).putString(TEMP, temperature).putString(CONDITION, condition)
                .putString(TIMEZONE, timezone).putString(STATUS, "Updated now").apply()
            updateAll(context)
        }

        fun requestAllUpdates(context: Context) {
            updateAll(context)
        }

        private fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = android.content.ComponentName(context, TimeWeatherWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val views = RemoteViews(context.packageName, R.layout.time_weather_widget)
            views.setTextViewText(R.id.weather_city, prefs.getString(CITY, "Current location") ?: "Current location")
            views.setTextViewText(R.id.weather_temp, prefs.getString(TEMP, "--°C") ?: "--°C")
            views.setTextViewText(R.id.weather_condition, prefs.getString(CONDITION, "Tap refresh") ?: "Tap refresh")
            views.setTextViewText(R.id.weather_status, prefs.getString(STATUS, "Location not set") ?: "Location not set")
            val zone = prefs.getString(TIMEZONE, java.util.TimeZone.getDefault().id) ?: java.util.TimeZone.getDefault().id

            val refresh = PendingIntent.getActivity(
                context, id * 41, Intent(context, LocationWeatherActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.weather_refresh, refresh)
            views.setOnClickPendingIntent(R.id.weather_card, refresh)

            // Music binding
            val snapshot = PlaybackNotificationRouter.activeSnapshot(context)
            views.setTextViewText(R.id.widget_title, snapshot.first)
            views.setTextViewText(R.id.widget_artist, snapshot.second)
            views.setImageViewResource(R.id.widget_btn_play, if (snapshot.third) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)

            val base = id * 10
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            views.setOnClickPendingIntent(R.id.widget_btn_prev, PendingIntent.getBroadcast(context, base, Intent(context, WidgetActionReceiver::class.java).setAction(MusicService.ACTION_PREV), flags))
            views.setOnClickPendingIntent(R.id.widget_btn_play, PendingIntent.getBroadcast(context, base + 1, Intent(context, WidgetActionReceiver::class.java).setAction(MusicService.ACTION_TOGGLE_PLAY), flags))
            views.setOnClickPendingIntent(R.id.widget_btn_next, PendingIntent.getBroadcast(context, base + 2, Intent(context, WidgetActionReceiver::class.java).setAction(MusicService.ACTION_NEXT), flags))

            manager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) updateAll(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            updateOne(context, manager, id)
        }
    }
}
