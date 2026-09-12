package com.example

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.weather.WeatherIconRenderer
import com.example.weather.getWeatherState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

data class HourlyForecast(val time: String, val temp: Int, val weatherCode: Int, val isDay: Boolean)
data class DailyForecast(val day: String, val minTemp: Int, val maxTemp: Int, val weatherCode: Int)
data class FullWeatherData(
    val currentTemp: Int,
    val currentDesc: String,
    val weatherCode: Int,
    val isDay: Boolean,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("time_weather_widget", Context.MODE_PRIVATE)
    val city = prefs.getString("city", "Unknown Location") ?: "Unknown Location"
    val lat = prefs.getFloat("lat", 0f).toDouble()
    val lon = prefs.getFloat("lon", 0f).toDouble()

    var weatherData by remember { mutableStateOf<FullWeatherData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lat, lon) {
        if (lat == 0.0 && lon == 0.0) {
            errorMsg = "Please tap the refresh icon on the Weather Widget on your home screen to set your location."
            isLoading = false
            return@LaunchedEffect
        }
        try {
            val data = withContext(Dispatchers.IO) { fetchFullWeather(lat, lon) }
            weatherData = data
            errorMsg = null
        } catch (e: Exception) {
            errorMsg = "Could not fetch weather data: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(city, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (errorMsg != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            } else if (weatherData != null) {
                val data = weatherData!!
                val currentState = getWeatherState(data.weatherCode, data.isDay)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    item {
                        // Current Weather Hero with glassmorphism over canvas animation
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .padding(16.dp)
                                .clip(RoundedCornerShape(32.dp))
                        ) {
                            // The Canvas Animation Background
                            WeatherIconRenderer(
                                state = currentState,
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            // Glassmorphic overlay for readability
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f))
                                    ))
                            )
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Text("${data.currentTemp}°", fontSize = 96.sp, fontWeight = FontWeight.Light, color = Color.White)
                                Text(data.currentDesc, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.9f))
                            }
                        }
                    }

                    item {
                        Text("Today", modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(data.hourly) { hour ->
                                Card(
                                    modifier = Modifier.width(80.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(hour.time, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(16.dp))
                                        
                                        // Miniature canvas for hourly item
                                        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))) {
                                            WeatherIconRenderer(
                                                state = getWeatherState(hour.weatherCode, hour.isDay),
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        
                                        Spacer(Modifier.height(16.dp))
                                        Text("${hour.temp}°", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(32.dp))
                    }

                    item {
                        Text("7-Day Forecast", modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    items(data.daily) { day ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(day.day, modifier = Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                
                                Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))) {
                                    WeatherIconRenderer(
                                        state = getWeatherState(day.weatherCode, true),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                
                                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    Text("${day.minTemp}°", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 16.sp)
                                    Spacer(Modifier.width(20.dp))
                                    Text("${day.maxTemp}°", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun fetchFullWeather(latitude: Double, longitude: Double): FullWeatherData {
    val urlStr = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto"
    val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
    }
    val body = connection.inputStream.bufferedReader().use { it.readText() }
    connection.disconnect()
    
    val json = JSONObject(body)
    
    val current = json.getJSONObject("current")
    val curCode = current.getInt("weather_code")
    val curTemp = current.getDouble("temperature_2m").toInt()
    val isDay = current.optInt("is_day", 1) == 1
    
    val hourlyJson = json.getJSONObject("hourly")
    val hourlyTimes = hourlyJson.getJSONArray("time")
    val hourlyTemps = hourlyJson.getJSONArray("temperature_2m")
    val hourlyCodes = hourlyJson.getJSONArray("weather_code")
    val hourlyIsDay = hourlyJson.optJSONArray("is_day")
    
    val hourlyList = mutableListOf<HourlyForecast>()
    val sdfIn = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
    val sdfOut = SimpleDateFormat("h a", Locale.getDefault())
    
    for (i in 0 until minOf(72, hourlyTimes.length())) {
        val t = hourlyTimes.getString(i)
        val d = sdfIn.parse(t)
        if (d != null && d.time < System.currentTimeMillis() - 3600_000) continue 
        val timeStr = if (d != null) sdfOut.format(d) else t
        val hIsDay = hourlyIsDay?.optInt(i, 1) == 1
        hourlyList.add(HourlyForecast(timeStr, hourlyTemps.getDouble(i).toInt(), hourlyCodes.getInt(i), hIsDay))
        if (hourlyList.size >= 24) break
    }

    val dailyJson = json.getJSONObject("daily")
    val dailyTimes = dailyJson.getJSONArray("time")
    val dailyMax = dailyJson.getJSONArray("temperature_2m_max")
    val dailyMin = dailyJson.getJSONArray("temperature_2m_min")
    val dailyCodes = dailyJson.getJSONArray("weather_code")
    
    val dailyList = mutableListOf<DailyForecast>()
    val sdfDayIn = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val sdfDayOut = SimpleDateFormat("EEEE", Locale.getDefault())
    
    for (i in 0 until dailyTimes.length()) {
        val t = dailyTimes.getString(i)
        val d = sdfDayIn.parse(t)
        val dayStr = if (i == 0) "Today" else if (d != null) sdfDayOut.format(d) else t
        dailyList.add(
            DailyForecast(
                dayStr,
                dailyMin.getDouble(i).toInt(),
                dailyMax.getDouble(i).toInt(),
                dailyCodes.getInt(i)
            )
        )
    }

    return FullWeatherData(
        currentTemp = curTemp,
        currentDesc = weatherDesc(curCode, isDay),
        weatherCode = curCode,
        isDay = isDay,
        hourly = hourlyList,
        daily = dailyList
    )
}

private fun weatherDesc(code: Int, isDay: Boolean = true): String = when (code) {
    0 -> if (isDay) "Clear sky" else "Clear night"
    1, 2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Foggy"
    51, 53, 55, 56, 57 -> "Drizzle"
    61, 63, 65 -> "Rain"
    66, 67 -> "Freezing Rain"
    71, 73, 75 -> "Snow"
    77, 85, 86 -> "Heavy Snow"
    80, 81, 82 -> "Heavy Rain"
    95, 96, 99 -> "Thunderstorm"
    else -> "Unknown"
}
