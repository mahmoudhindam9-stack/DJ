package com.example.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.withContext
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

class LocationWeatherActivity : ComponentActivity() {
    private val prefsName = "time_weather_widget"
    private var timeoutJob: Job? = null


    @android.annotation.SuppressLint("InvalidFragmentVersionForActivityResult")
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            fetchLocationAndWeather()
        } else {
            fetchWeatherByIpFallback()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (hasLocationPermission()) {
            fetchLocationAndWeather()
        } else {
            requestPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @android.annotation.SuppressLint("MissingPermission")
    private fun fetchLocationAndWeather() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }

        if (last != null && System.currentTimeMillis() - last.time < 15 * 60_000L) {
            loadWeather(last)
            return
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                runCatching { manager.removeUpdates(this) }
                timeoutJob?.cancel()
                loadWeather(location)
            }
        }

        try {
            var requested = false
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).forEach { provider ->
                if (runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)) {
                    manager.requestLocationUpdates(provider, 0L, 0f, listener)
                    requested = true
                }
            }
            if (!requested) {
                fetchWeatherByIpFallback()
                return
            }
            timeoutJob = lifecycleScope.launch {
                delay(12_000L)
                runCatching { manager.removeUpdates(listener) }
                if (!isFinishing) {
                    fetchWeatherByIpFallback()
                }
            }
        } catch (_: SecurityException) {
            fetchWeatherByIpFallback()
        }
    }

    private fun fetchWeatherByIpFallback() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { 
                val url = "https://get.geojs.io/v1/ip/geo.json"
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val json = JSONObject(body)
                val lat = json.getDouble("latitude")
                val lon = json.getDouble("longitude")
                val cityName = json.optString("city", "Current location")
                fetchWeather(lat, lon, cityName)
            }.getOrNull()
            
            withContext(Dispatchers.Main) {
                if (result != null) {
                    TimeWeatherWidgetProvider.updateWeather(
                        this@LocationWeatherActivity,
                        result.city,
                        result.temperature,
                        result.condition,
                        result.timezone,
                        result.warning,
                        result.lat,
                        result.lon,
                        result.hourlyJson,
                        result.dailyJson
                    )
                } else {
                    TimeWeatherWidgetProvider.setStatus(this@LocationWeatherActivity, "Weather unavailable")
                }
                finish()
            }
        }
    }

    private fun loadWeather(location: Location) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { fetchWeather(location.latitude, location.longitude) }.getOrNull()
            withContext(Dispatchers.Main) {
                if (result != null) {
                    TimeWeatherWidgetProvider.updateWeather(
                        this@LocationWeatherActivity,
                        result.city,
                        result.temperature,
                        result.condition,
                        result.timezone,
                        result.warning,
                        result.lat,
                        result.lon,
                        result.hourlyJson,
                        result.dailyJson
                    )
                } else {
                    TimeWeatherWidgetProvider.setStatus(this@LocationWeatherActivity, "Weather unavailable")
                }
                finish()
            }
        }
    }

    private data class WeatherResult(
        val city: String,
        val temperature: String,
        val condition: String,
        val timezone: String,
        val warning: String,
        val lat: Double,
        val lon: Double,
        val hourlyJson: String = "",
        val dailyJson: String = ""
    )

    private fun fetchWeather(latitude: Double, longitude: Double, fallbackCity: String? = null): WeatherResult {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "DJ-WeatherWidget/1.0")
        }
        val body = try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
        val json = JSONObject(body)
        val current = json.getJSONObject("current")
        val timezone = json.optString("timezone", TimeZone.getDefault().id)
        val isDay = current.optInt("is_day", 1) == 1
        val (desc, warn) = weatherDescription(current.getInt("weather_code"), isDay)

        // Parse next 5 hours
        val hourlyJsonObj = json.optJSONObject("hourly")
        val hourlyArray = org.json.JSONArray()
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

        // Parse next 5 days
        val dailyJsonObj = json.optJSONObject("daily")
        val dailyArray = org.json.JSONArray()
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

        val cityName = fallbackCity ?: reverseGeocode(latitude, longitude)

        return WeatherResult(
            city = cityName,
            temperature = String.format(Locale.getDefault(), "%.0f°C", current.getDouble("temperature_2m")),
            condition = desc,
            timezone = timezone,
            warning = warn,
            lat = latitude,
            lon = longitude,
            hourlyJson = hourlyArray.toString(),
            dailyJson = dailyArray.toString()
        )
    }

    private fun reverseGeocode(latitude: Double, longitude: Double): String = runCatching {
        @Suppress("DEPRECATION")
        Geocoder(this, Locale.getDefault()).getFromLocation(latitude, longitude, 1)?.firstOrNull()?.let {
            it.locality ?: it.subAdminArea ?: it.adminArea ?: it.countryName
        }
    }.getOrNull() ?: "Current location"

    private fun weatherDescription(code: Int, isDay: Boolean = true): Pair<String, String> = when (code) {
        0 -> if (isDay) "☀️ Clear sky" to "" else "🌙 Clear night" to ""
        1, 2 -> if (isDay) "🌤️ Partly cloudy" to "" else "☁️ Partly cloudy" to ""
        3 -> "☁️ Overcast" to ""
        45, 48 -> "🌫️ Foggy" to "Low visibility due to fog"
        51, 53, 55, 56, 57 -> "🌦️ Drizzle" to ""
        61, 63, 65 -> "🌧️ Rain" to ""
        66, 67 -> "🌧️ Freezing Rain" to "Slippery roads warning"
        71, 73, 75 -> "❄️ Snow" to ""
        77, 85, 86 -> "❄️ Heavy Snow" to "Heavy snow warning"
        80, 81, 82 -> "🌧️ Heavy Rain" to "Heavy rain warning"
        95, 96, 99 -> "⛈️ Thunderstorm" to "Thunderstorm warning! Stay safe."
        else -> "🌍 Weather update" to ""
    }
}
