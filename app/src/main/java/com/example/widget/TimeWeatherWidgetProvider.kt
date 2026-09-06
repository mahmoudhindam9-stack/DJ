package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.R

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
            views.setString(R.id.weather_clock, "setTimeZone", zone)
            val refresh = PendingIntent.getActivity(
                context, id * 41, Intent(context, LocationWeatherActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.weather_refresh, refresh)
            views.setOnClickPendingIntent(R.id.weather_card, refresh)
            manager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) updateAll(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val views = RemoteViews(context.packageName, R.layout.time_weather_widget)
            views.setTextViewText(R.id.weather_city, prefs.getString(CITY, "Current location") ?: "Current location")
            views.setTextViewText(R.id.weather_temp, prefs.getString(TEMP, "--°C") ?: "--°C")
            views.setTextViewText(R.id.weather_condition, prefs.getString(CONDITION, "Tap refresh") ?: "Tap refresh")
            views.setTextViewText(R.id.weather_status, prefs.getString(STATUS, "Location not set") ?: "Location not set")
            val zone = prefs.getString(TIMEZONE, java.util.TimeZone.getDefault().id) ?: java.util.TimeZone.getDefault().id
            views.setString(R.id.weather_clock, "setTimeZone", zone)
            val pending = PendingIntent.getActivity(context, id * 41, Intent(context, LocationWeatherActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.weather_refresh, pending)
            views.setOnClickPendingIntent(R.id.weather_card, pending)
            manager.updateAppWidget(id, views)
        }
    }
}
