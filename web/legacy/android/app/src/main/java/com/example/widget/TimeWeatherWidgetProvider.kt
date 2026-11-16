package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.player.MusicService
import com.example.player.PlaybackNotificationRouter
import org.json.JSONArray

class TimeWeatherWidgetProvider : AppWidgetProvider() {
    companion object {
        fun mapCodeToDrawable(code: Int, isDay: Boolean = true): Int {
            return when (code) {
                0 -> if (isDay) R.drawable.ic_weather_sunny else R.drawable.ic_weather_clear_night
                1, 2 -> if (isDay) R.drawable.ic_weather_partly_cloudy else R.drawable.ic_weather_clear_night
                3 -> R.drawable.ic_weather_cloudy
                45, 48 -> R.drawable.ic_weather_fog
                51, 53, 55, 56, 57 -> R.drawable.ic_weather_rain
                61, 63 -> R.drawable.ic_weather_rain
                65, 66, 67 -> R.drawable.ic_weather_heavy_rain
                71, 73, 75, 77, 85, 86 -> R.drawable.ic_weather_snow
                80, 81 -> R.drawable.ic_weather_rain
                82 -> R.drawable.ic_weather_heavy_rain
                95, 96, 99 -> R.drawable.ic_weather_thunderstorm
                else -> R.drawable.ic_weather_unknown
            }
        }

        private fun mapConditionToDrawable(condition: String): Int {
            return when {
                condition.contains("Thunderstorm", ignoreCase = true) || condition.contains("Thunder", ignoreCase = true) || condition.contains("⛈") -> R.drawable.ic_weather_thunderstorm
                (condition.contains("Heavy", ignoreCase = true) && condition.contains("Rain", ignoreCase = true)) || condition.contains("Heavy Rain", ignoreCase = true) -> R.drawable.ic_weather_heavy_rain
                condition.contains("Rain", ignoreCase = true) || condition.contains("Drizzle", ignoreCase = true) || condition.contains("Shower", ignoreCase = true) || condition.contains("🌧") || condition.contains("🌦") -> R.drawable.ic_weather_rain
                condition.contains("Snow", ignoreCase = true) || condition.contains("Sleet", ignoreCase = true) || condition.contains("Blizzard", ignoreCase = true) || condition.contains("❄") -> R.drawable.ic_weather_snow
                condition.contains("Fog", ignoreCase = true) || condition.contains("Mist", ignoreCase = true) || condition.contains("Haze", ignoreCase = true) || condition.contains("🌫") -> R.drawable.ic_weather_fog
                condition.contains("Partly Cloudy", ignoreCase = true) || condition.contains("Partly", ignoreCase = true) || condition.contains("⛅") || condition.contains("🌤") -> R.drawable.ic_weather_partly_cloudy
                condition.contains("Cloudy", ignoreCase = true) || condition.contains("Overcast", ignoreCase = true) || condition.contains("☁") -> R.drawable.ic_weather_cloudy
                condition.contains("Clear Night", ignoreCase = true) || condition.contains("Night", ignoreCase = true) || condition.contains("🌙") -> R.drawable.ic_weather_clear_night
                condition.contains("Sunny", ignoreCase = true) || condition.contains("Clear", ignoreCase = true) || condition.contains("☀️") -> R.drawable.ic_weather_sunny
                else -> R.drawable.ic_weather_unknown
            }
        }
        
        const val ACTION_REFRESH = "com.example.widget.ACTION_REFRESH_WEATHER"
        private const val PREFS = "time_weather_widget"
        private const val CITY = "city"
        private const val TEMP = "temp"
        private const val CONDITION = "condition"
        private const val TIMEZONE = "timezone"
        private const val STATUS = "status"
        private const val WARNING = "warning"
        private const val FORECAST_HOURLY = "forecast_hourly"
        private const val FORECAST_DAILY = "forecast_daily"

        private const val LAT = "lat"
        private const val LON = "lon"

        fun setStatus(context: Context, status: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(STATUS, status).apply()
            updateAll(context)
        }

        fun updateWeather(
            context: Context,
            city: String,
            temperature: String,
            condition: String,
            timezone: String,
            warning: String = "",
            lat: Double = 0.0,
            lon: Double = 0.0,
            hourlyJson: String? = null,
            dailyJson: String? = null
        ) {
            val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(CITY, city).putString(TEMP, temperature).putString(CONDITION, condition)
                .putString(TIMEZONE, timezone).putString(STATUS, "Updated now").putString(WARNING, warning)
                .putFloat(LAT, lat.toFloat()).putFloat(LON, lon.toFloat())
            if (!hourlyJson.isNullOrEmpty()) {
                editor.putString(FORECAST_HOURLY, hourlyJson)
            }
            if (!dailyJson.isNullOrEmpty()) {
                editor.putString(FORECAST_DAILY, dailyJson)
            }
            editor.apply()
            updateAll(context)
        }

        fun updateForecast(context: Context, hourlyJson: String, dailyJson: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(FORECAST_HOURLY, hourlyJson)
                .putString(FORECAST_DAILY, dailyJson)
                .apply()
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

        fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val views = RemoteViews(context.packageName, R.layout.time_weather_widget)
            
            val alpha = prefs.getInt("bg_alpha_$id", 255)
            views.setInt(R.id.widget_bg_image, "setImageAlpha", alpha)

            views.setTextViewText(R.id.weather_city, prefs.getString(CITY, "Current location") ?: "Current location")
            views.setTextViewText(R.id.weather_temp, prefs.getString(TEMP, "--°C") ?: "--°C")
            val condString = prefs.getString(CONDITION, "Tap refresh") ?: "Tap refresh"
            val emojiOrCondition: String
            val spaceIndex = condString.indexOf(' ')
            if (spaceIndex > 0 && condString.length > 2 && condString.codePointAt(0) > 0x2000) {
                emojiOrCondition = condString.substring(0, spaceIndex)
                views.setTextViewText(R.id.weather_condition, condString.substring(spaceIndex + 1))
            } else {
                emojiOrCondition = condString
                views.setTextViewText(R.id.weather_condition, condString)
            }
            views.setImageViewResource(R.id.weather_icon, mapConditionToDrawable(condString))
                        views.setTextViewText(R.id.weather_status, prefs.getString(STATUS, "Location not set") ?: "Location not set")
            
            val warningTxt = prefs.getString(WARNING, "") ?: ""
            if (warningTxt.isNotEmpty()) {
                views.setViewVisibility(R.id.weather_warning, android.view.View.VISIBLE)
                views.setTextViewText(R.id.weather_warning, warningTxt)
            } else {
                views.setViewVisibility(R.id.weather_warning, android.view.View.GONE)
            }
            val defaultZone = java.util.TimeZone.getDefault().id
            val zone = prefs.getString(TIMEZONE, defaultZone)?.takeIf { it.isNotBlank() } ?: defaultZone
            runCatching {
                views.setString(R.id.weather_clock, "setTimeZone", zone)
                views.setString(R.id.weather_date, "setTimeZone", zone)
            }

            val refresh = PendingIntent.getActivity(
                context, id * 41, Intent(context, LocationWeatherActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.weather_refresh, refresh)
            
            // Open weather on click
            val openWeatherIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("open_route", "weather")
            }
            val openWeatherPending = PendingIntent.getActivity(context, id * 43, openWeatherIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.weather_card, openWeatherPending)
            views.setOnClickPendingIntent(R.id.weather_icon, openWeatherPending)
            views.setOnClickPendingIntent(R.id.weather_temp, openWeatherPending)
            views.setOnClickPendingIntent(R.id.weather_condition, openWeatherPending)
            views.setOnClickPendingIntent(R.id.weather_city, openWeatherPending)

            // Music binding
            val snapshot = PlaybackNotificationRouter.activeSnapshot(context)
            views.setTextViewText(R.id.widget_title, snapshot.first)
            views.setTextViewText(R.id.widget_artist, snapshot.second)
            views.setImageViewResource(R.id.widget_btn_prev, R.drawable.ic_widget_prev)
            views.setImageViewResource(R.id.widget_btn_play, if (snapshot.third) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
            views.setImageViewResource(R.id.widget_btn_next, R.drawable.ic_widget_next)
            
            val progress = PlaybackNotificationRouter.activeProgress(context)
            val positionMs = progress.first
            val durationMs = progress.second
            views.setTextViewText(R.id.widget_time_current, com.example.utils.MusicScanner.formatMs(positionMs))
            views.setTextViewText(R.id.widget_time_total, com.example.utils.MusicScanner.formatMs(durationMs))
            val pct = if (durationMs > 0) ((positionMs * 1000) / durationMs).toInt().coerceIn(0, 1000) else 0
            views.setProgressBar(R.id.widget_progress, 1000, pct, false)
            
            WidgetPlaybackIntents.wireButtons(context, views, id, R.id.widget_btn_prev, R.id.widget_btn_play, R.id.widget_btn_next)

            
            // Open player on click
            val openPlayerIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("open_route", "player")
            }
            val openPlayerPending = PendingIntent.getActivity(context, id * 42, openPlayerIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_title, openPlayerPending)
            views.setOnClickPendingIntent(R.id.widget_artist, openPlayerPending)
            views.setOnClickPendingIntent(R.id.widget_music_container, openPlayerPending)

            // Forecast container click listener (opens weather screen)
            views.setOnClickPendingIntent(R.id.widget_forecast_container, openWeatherPending)

            // Bind Hourly Forecast (5 slots)
            val hourlyRaw = prefs.getString(FORECAST_HOURLY, null)
            val hourlyTimeIds = intArrayOf(R.id.hourly_time_1, R.id.hourly_time_2, R.id.hourly_time_3, R.id.hourly_time_4, R.id.hourly_time_5)
            val hourlyIconIds = intArrayOf(R.id.hourly_icon_1, R.id.hourly_icon_2, R.id.hourly_icon_3, R.id.hourly_icon_4, R.id.hourly_icon_5)
            val hourlyTempIds = intArrayOf(R.id.hourly_temp_1, R.id.hourly_temp_2, R.id.hourly_temp_3, R.id.hourly_temp_4, R.id.hourly_temp_5)

            if (!hourlyRaw.isNullOrEmpty()) {
                runCatching {
                    val arr = JSONArray(hourlyRaw)
                    for (i in 0 until 5) {
                        if (i < arr.length()) {
                            val obj = arr.getJSONObject(i)
                            views.setTextViewText(hourlyTimeIds[i], obj.optString("time", "--"))
                            views.setTextViewText(hourlyTempIds[i], obj.optString("temp", "--°"))
                            val code = obj.optInt("code", 0)
                            val isDay = obj.optBoolean("isDay", true)
                            views.setImageViewResource(hourlyIconIds[i], mapCodeToDrawable(code, isDay))
                        }
                    }
                    views.setTextViewText(R.id.forecast_hourly_subtitle, "Next 5 Hours")
                }.onFailure {
                    views.setTextViewText(R.id.forecast_hourly_subtitle, "Hourly Forecast")
                }
            } else {
                views.setTextViewText(R.id.forecast_hourly_subtitle, "Tap ⟳ to load")
            }

            // Bind Daily Forecast (5 slots)
            val dailyRaw = prefs.getString(FORECAST_DAILY, null)
            val dailyDayIds = intArrayOf(R.id.daily_day_1, R.id.daily_day_2, R.id.daily_day_3, R.id.daily_day_4, R.id.daily_day_5)
            val dailyIconIds = intArrayOf(R.id.daily_icon_1, R.id.daily_icon_2, R.id.daily_icon_3, R.id.daily_icon_4, R.id.daily_icon_5)
            val dailyTempIds = intArrayOf(R.id.daily_temp_1, R.id.daily_temp_2, R.id.daily_temp_3, R.id.daily_temp_4, R.id.daily_temp_5)

            if (!dailyRaw.isNullOrEmpty()) {
                runCatching {
                    val arr = JSONArray(dailyRaw)
                    for (i in 0 until 5) {
                        if (i < arr.length()) {
                            val obj = arr.getJSONObject(i)
                            views.setTextViewText(dailyDayIds[i], obj.optString("day", "--"))
                            views.setTextViewText(dailyTempIds[i], obj.optString("temp", "--° / --°"))
                            val code = obj.optInt("code", 0)
                            views.setImageViewResource(dailyIconIds[i], mapCodeToDrawable(code, true))
                        }
                    }
                    views.setTextViewText(R.id.forecast_daily_subtitle, "High / Low")
                }.onFailure {
                    views.setTextViewText(R.id.forecast_daily_subtitle, "5-Day Outlook")
                }
            } else {
                views.setTextViewText(R.id.forecast_daily_subtitle, "5-Day Outlook")
            }

            manager.updateAppWidget(id, views)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherSyncHelper.scheduleHourlySync(context)
        WeatherSyncHelper.syncWeather(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_REFRESH,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_BOOT_COMPLETED -> {
                WeatherSyncHelper.syncWeather(context)
                updateAll(context)
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            updateOne(context, manager, id)
        }
        WeatherSyncHelper.syncWeather(context)
    }
}
