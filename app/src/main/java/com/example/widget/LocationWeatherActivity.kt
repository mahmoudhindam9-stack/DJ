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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.TimeZone

class LocationWeatherActivity : ComponentActivity() {
    private val prefsName = "time_weather_widget"
    private val permissionPromptedKey = "location_permission_prompted"
    private var timeoutJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasLocationPermission()) {
            val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(permissionPromptedKey, false)) {
                prefs.edit().putBoolean(permissionPromptedKey, true).apply()
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 9001)
            } else {
                TimeWeatherWidgetProvider.setStatus(this, "Location permission required")
                finish()
            }
            return
        }
        fetchLocationAndWeather()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 9001) {
            if (hasLocationPermission()) fetchLocationAndWeather()
            else {
                TimeWeatherWidgetProvider.setStatus(this, "Location permission denied")
                finish()
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

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
                TimeWeatherWidgetProvider.setStatus(this, "Location services are off")
                finish()
                return
            }
            timeoutJob = CoroutineScope(Dispatchers.Main).launch {
                delay(12_000L)
                runCatching { manager.removeUpdates(listener) }
                if (!isFinishing) {
                    TimeWeatherWidgetProvider.setStatus(this@LocationWeatherActivity, "Unable to get current location")
                    finish()
                }
            }
        } catch (_: SecurityException) {
            TimeWeatherWidgetProvider.setStatus(this, "Location permission required")
            finish()
        }
    }

    private fun loadWeather(location: Location) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching { fetchWeather(location.latitude, location.longitude) }.getOrNull()
            withContext(Dispatchers.Main) {
                if (result != null) {
                    TimeWeatherWidgetProvider.updateWeather(this@LocationWeatherActivity, result.city, result.temperature, result.condition, result.timezone)
                } else {
                    TimeWeatherWidgetProvider.setStatus(this@LocationWeatherActivity, "Weather unavailable")
                }
                finish()
            }
        }
    }

    private data class WeatherResult(val city: String, val temperature: String, val condition: String, val timezone: String)

    private fun fetchWeather(latitude: Double, longitude: Double): WeatherResult {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,weather_code&timezone=auto"
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
        return WeatherResult(
            city = reverseGeocode(latitude, longitude),
            temperature = String.format(Locale.getDefault(), "%.0f°C", current.getDouble("temperature_2m")),
            condition = weatherDescription(current.getInt("weather_code")),
            timezone = timezone
        )
    }

    private fun reverseGeocode(latitude: Double, longitude: Double): String = runCatching {
        @Suppress("DEPRECATION")
        Geocoder(this, Locale.getDefault()).getFromLocation(latitude, longitude, 1)?.firstOrNull()?.let {
            it.locality ?: it.subAdminArea ?: it.adminArea ?: it.countryName
        }
    }.getOrNull() ?: "Current location"

    private fun weatherDescription(code: Int): String = when (code) {
        0 -> "Clear sky"
        1, 2, 3 -> "Partly cloudy"
        45, 48 -> "Foggy"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Rain"
        71, 73, 75, 77, 85, 86 -> "Snow"
        95, 96, 99 -> "Thunderstorm"
        else -> "Weather update"
    }
}
