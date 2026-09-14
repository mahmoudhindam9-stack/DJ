package com.example.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

object WeatherSyncHelper {
    private const val ACTION_AUTO_SYNC = "com.example.widget.ACTION_REFRESH_WEATHER"
    private const val ALARM_REQUEST_CODE = 9021
    private const val INTERVAL_MILLIS = 3600_000L // 1 hour
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun scheduleHourlySync(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TimeWeatherWidgetProvider::class.java).apply {
            action = ACTION_AUTO_SYNC
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MILLIS
        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME,
                triggerAt,
                pendingIntent
            )
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME, triggerAt, pendingIntent)
        }
    }

    fun syncWeather(context: Context) {
        scope.launch {
            val prefs = context.getSharedPreferences("time_weather_widget", Context.MODE_PRIVATE)
            var lat = prefs.getFloat("lat", 0f).toDouble()
            var lon = prefs.getFloat("lon", 0f).toDouble()
            var city = prefs.getString("city", null)

            // If coordinates not stored yet, fallback to IP-based location
            if (lat == 0.0 && lon == 0.0) {
                val ipLoc = fetchIpLocation()
                if (ipLoc != null) {
                    lat = ipLoc.first
                    lon = ipLoc.second
                    city = ipLoc.third
                }
            }

            if (lat == 0.0 && lon == 0.0) {
                scheduleHourlySync(context)
                return@launch
            }

            try {
                val result = fetchWeatherData(lat, lon, city)
                if (result != null) {
                    TimeWeatherWidgetProvider.updateWeather(
                        context = context,
                        city = result.city,
                        temperature = result.temperature,
                        condition = result.condition,
                        timezone = result.timezone,
                        warning = result.warning,
                        lat = result.lat,
                        lon = result.lon,
                        hourlyJson = result.hourlyJson,
                        dailyJson = result.dailyJson
                    )
                }
            } catch (e: Exception) {
                // Offline or transient network failure: maintain cached display gracefully
                android.util.Log.w("WeatherSyncHelper", "Failed to sync weather: ${e.message}")
            } finally {
                scheduleHourlySync(context)
            }
        }
    }

    private fun fetchIpLocation(): Triple<Double, Double, String>? = runCatching {
        val url = "https://get.geojs.io/v1/ip/geo.json"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        val json = JSONObject(body)
        Triple(
            json.getDouble("latitude"),
            json.getDouble("longitude"),
            json.optString("city", "Current location")
        )
    }.getOrNull()

    private data class WeatherData(
        val city: String,
        val temperature: String,
        val condition: String,
        val timezone: String,
        val warning: String,
        val lat: Double,
        val lon: Double,
        val hourlyJson: String,
        val dailyJson: String
    )

    private fun fetchWeatherData(latitude: Double, longitude: Double, fallbackCity: String?): WeatherData? = runCatching {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "DJ-WeatherWidget/1.0")
        }
        val body = try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }

        val json = JSONObject(body)
        val current = json.getJSONObject("current")
        val timezone = json.optString("timezone", TimeZone.getDefault().id)
        val isDay = current.optInt("is_day", 1) == 1
        val code = current.getInt("weather_code")
        val (desc, warn) = formatWeatherDescription(code, isDay)

        // Parse 5-hour forecast
        val hourlyJsonObj = json.optJSONObject("hourly")
        val hourlyArray = JSONArray()
        if (hourlyJsonObj != null) {
            val times = hourlyJsonObj.optJSONArray("time")
            val temps = hourlyJsonObj.optJSONArray("temperature_2m")
            val codes = hourlyJsonObj.optJSONArray("weather_code")
            val isDays = hourlyJsonObj.optJSONArray("is_day")
            val zone = runCatching { ZoneId.of(timezone) }.getOrElse { ZoneId.systemDefault() }
            val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
            val outFormatter = DateTimeFormatter.ofPattern("h a", Locale.getDefault())
            val nowInZone = ZonedDateTime.now(zone)
            if (times != null && temps != null && codes != null) {
                for (i in 0 until times.length()) {
                    val tStr = times.optString(i, "")
                    val forecastZoned = runCatching {
                        LocalDateTime.parse(tStr, isoFormatter).atZone(zone)
                    }.getOrNull()
                    if (forecastZoned != null && forecastZoned.toInstant().isBefore(nowInZone.toInstant().minusSeconds(3600))) {
                        continue
                    }
                    val item = JSONObject().apply {
                        put("time", if (hourlyArray.length() == 0) "Now" else (forecastZoned?.format(outFormatter) ?: tStr))
                        put("temp", String.format(Locale.getDefault(), "%.0f°", temps.optDouble(i, 0.0)))
                        put("code", codes.optInt(i, 0))
                        put("isDay", isDays?.optInt(i, 1) == 1)
                    }
                    hourlyArray.put(item)
                    if (hourlyArray.length() >= 5) break
                }
            }
        }

        // Parse 5-day forecast
        val dailyJsonObj = json.optJSONObject("daily")
        val dailyArray = JSONArray()
        if (dailyJsonObj != null) {
            val times = dailyJsonObj.optJSONArray("time")
            val maxTemps = dailyJsonObj.optJSONArray("temperature_2m_max")
            val minTemps = dailyJsonObj.optJSONArray("temperature_2m_min")
            val codes = dailyJsonObj.optJSONArray("weather_code")
            val sdfDayIn = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sdfDayOut = SimpleDateFormat("EEE", Locale.getDefault())
            if (times != null && maxTemps != null && minTemps != null && codes != null) {
                for (i in 0 until minOf(5, times.length())) {
                    val tStr = times.optString(i, "")
                    val parsedDate = runCatching { sdfDayIn.parse(tStr) }.getOrNull()
                    val dayName = if (i == 0) "Today" else (parsedDate?.let { sdfDayOut.format(it) } ?: tStr)
                    val maxT = String.format(Locale.getDefault(), "%.0f°", maxTemps.optDouble(i, 0.0))
                    val minT = String.format(Locale.getDefault(), "%.0f°", minTemps.optDouble(i, 0.0))
                    val item = JSONObject().apply {
                        put("day", dayName)
                        put("temp", "$maxT / $minT")
                        put("code", codes.optInt(i, 0))
                    }
                    dailyArray.put(item)
                }
            }
        }

        WeatherData(
            city = fallbackCity ?: "Current location",
            temperature = String.format(Locale.getDefault(), "%.0f°C", current.getDouble("temperature_2m")),
            condition = desc,
            timezone = timezone,
            warning = warn,
            lat = latitude,
            lon = longitude,
            hourlyJson = hourlyArray.toString(),
            dailyJson = dailyArray.toString()
        )
    }.getOrNull()

    private fun formatWeatherDescription(code: Int, isDay: Boolean): Pair<String, String> = when (code) {
        0 -> if (isDay) "☀️ Clear sky" to "" else "🌙 Clear night" to ""
        1, 2 -> if (isDay) "🌤️ Partly cloudy" to "" else "☁️ Partly cloudy" to ""
        3 -> "☁️ Overcast" to ""
        45, 48 -> "🌫️ Foggy" to "Low visibility due to fog"
        51, 53, 55, 56, 57 -> "🌦️ Drizzle" to ""
        61, 63 -> "🌧️ Rain" to ""
        65 -> "🌧️ Heavy Rain" to "Heavy rain warning"
        66, 67 -> "🌧️ Freezing Rain" to "Slippery roads warning"
        71, 73, 75 -> "❄️ Snow" to ""
        77, 85, 86 -> "❄️ Heavy Snow" to "Heavy snow warning"
        80, 81 -> "🌧️ Rain Showers" to ""
        82 -> "🌧️ Heavy Rain" to "Heavy rain warning"
        95, 96, 99 -> "⛈️ Thunderstorm" to "Thunderstorm warning"
        else -> "🌍 Weather update" to ""
    }
}
