package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.player.PlaybackNotificationRouter
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class WidgetHourlyPreview(
    val time: String,
    val temperature: String,
    val weatherCode: Int,
    val isDay: Boolean
)

private data class WidgetDailyPreview(
    val day: String,
    val temperature: String,
    val weatherCode: Int
)

class TimeWeatherWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val prefs = getSharedPreferences("time_weather_widget", Context.MODE_PRIVATE)
        val initialAlpha = prefs.getInt("bg_alpha_$appWidgetId", 255).coerceIn(0, 255)
        val weatherCity = prefs.getString("city", "Current location") ?: "Current location"
        val weatherTemp = prefs.getString("temp", "--°C") ?: "--°C"
        val weatherCondition = prefs.getString("condition", "Tap refresh to load weather") ?: "Tap refresh to load weather"
        val weatherStatus = prefs.getString("status", "Location not set") ?: "Location not set"
        val hourlyForecast = readHourlyForecast(prefs.getString("forecast_hourly", null))
        val dailyForecast = readDailyForecast(prefs.getString("forecast_daily", null))
        val (trackTitle, trackArtist, isPlaying) = PlaybackNotificationRouter.activeSnapshot(this)
        val (positionMs, durationMs) = PlaybackNotificationRouter.activeProgress(this)

        setContent {
            MyApplicationTheme {
                TimeWeatherWidgetConfigurator(
                    initialTransparency = 1f - (initialAlpha / 255f),
                    city = weatherCity,
                    temperature = weatherTemp,
                    condition = weatherCondition,
                    weatherStatus = weatherStatus,
                    hourlyForecast = hourlyForecast,
                    dailyForecast = dailyForecast,
                    trackTitle = trackTitle,
                    trackArtist = trackArtist,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onCancel = { finish() },
                    onApply = { transparency ->
                        val alpha = ((1f - transparency) * 255f).toInt().coerceIn(0, 255)
                        prefs.edit().putInt("bg_alpha_$appWidgetId", alpha).apply()
                        val widgetManager = AppWidgetManager.getInstance(this)
                        TimeWeatherWidgetProvider.updateOne(this, widgetManager, appWidgetId)
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        )
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun TimeWeatherWidgetConfigurator(
    initialTransparency: Float,
    city: String,
    temperature: String,
    condition: String,
    weatherStatus: String,
    hourlyForecast: List<WidgetHourlyPreview>,
    dailyForecast: List<WidgetDailyPreview>,
    trackTitle: String,
    trackArtist: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onCancel: () -> Unit,
    onApply: (Float) -> Unit,
) {
    var transparency by remember { mutableFloatStateOf(initialTransparency.coerceIn(0f, 1f)) }
    var clock by remember { mutableStateOf(formatNow("h:mm a")) }
    var date by remember { mutableStateOf(formatNow("EEE, MMM d")) }

    LaunchedEffect(Unit) {
        while (true) {
            clock = formatNow("h:mm a")
            date = formatNow("EEE, MMM d")
            delay(1_000L)
        }
    }

    val panel = Color(0xD90A1321)
    val muted = Color(0xFFB8C4D3)
    val accent = Color(0xFF63A9FF)
    val imageOpacity = 1f - transparency
    val trackProgress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val currentWeather = hourlyForecast.firstOrNull()
    val currentWeatherIcon = TimeWeatherWidgetProvider.mapCodeToDrawable(
        currentWeather?.weatherCode ?: 0,
        currentWeather?.isDay ?: true
    )

    Scaffold(
        containerColor = Color(0xFF090F19),
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1624))
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { onApply(transparency) },
                    modifier = Modifier.weight(1.35f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF247FD1))
                ) {
                    Text("Apply", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Time & Weather Widget",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "Live widget preview • Adjust the background transparency",
                color = muted,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(22.dp))

            // Preview the same widget information and layout. Moving the slider
            // changes the wallpaper image opacity immediately, before Apply.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 440.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF111B2A))
                    .padding(8.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.widget_dj_bg),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    alpha = imageOpacity
                )

                Column(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Weather + live clock
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(panel)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(currentWeatherIcon),
                            contentDescription = "Weather",
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(temperature, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                            Text(city, color = muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(condition, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(clock, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text(date, color = muted, fontSize = 11.sp)
                            Text(weatherStatus, color = muted.copy(alpha = 0.8f), fontSize = 8.sp, maxLines = 1)
                        }
                    }

                    // Music information, transport controls, and current progress
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(panel)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    trackTitle.ifBlank { "No Track Loaded" },
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    trackArtist.ifBlank { "DJ Engine" },
                                    color = muted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text("◀", color = Color.White, fontSize = 17.sp)
                            Spacer(Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF247FD1)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (isPlaying) "Ⅱ" else "▶", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text("▶|", color = Color.White, fontSize = 17.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatMs(positionMs), color = muted, fontSize = 9.sp)
                            Spacer(Modifier.width(7.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF455269))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(trackProgress)
                                        .fillMaxHeight()
                                        .background(accent)
                                )
                            }
                            Spacer(Modifier.width(7.dp))
                            Text(formatMs(durationMs), color = muted, fontSize = 9.sp)
                        }
                    }

                    // Hourly forecast, using the same cached data as the real widget
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(panel)
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("HOURLY FORECAST", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("Next 5 Hours", color = muted, fontSize = 9.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val slots = hourlyForecast.take(5)
                            if (slots.isEmpty()) {
                                Text("Tap refresh on the widget to load weather", color = muted, fontSize = 10.sp)
                            } else {
                                slots.forEach { item ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(item.time, color = muted, fontSize = 9.sp, maxLines = 1)
                                        Image(
                                            painter = painterResource(
                                                TimeWeatherWidgetProvider.mapCodeToDrawable(item.weatherCode, item.isDay)
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.size(21.dp)
                                        )
                                        Text(item.temperature, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                        Spacer(Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("5-DAY FORECAST", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("High / Low", color = muted, fontSize = 9.sp)
                        }
                        Spacer(Modifier.height(5.dp))
                        if (dailyForecast.isEmpty()) {
                            Text("Forecast will appear here after the first weather update.", color = muted, fontSize = 10.sp)
                        } else {
                            dailyForecast.take(5).forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.day, color = Color.White, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                    Image(
                                        painter = painterResource(
                                            TimeWeatherWidgetProvider.mapCodeToDrawable(item.weatherCode, true)
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(item.temperature, color = muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Background transparency", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("The widget preview changes as you move the slider", color = muted, fontSize = 11.sp)
                }
                Text("${(transparency * 100).toInt()}%", color = accent, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Slider(
                value = transparency,
                onValueChange = { transparency = it },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = Color(0xFF34445B)
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Opaque", color = muted, fontSize = 10.sp)
                Text("Transparent", color = muted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

private fun readHourlyForecast(raw: String?): List<WidgetHourlyPreview> {
    val array = runCatching { JSONArray(raw ?: "[]") }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until minOf(5, array.length())) {
            val item = array.optJSONObject(index) ?: continue
            add(
                WidgetHourlyPreview(
                    time = item.optString("time", "--"),
                    temperature = item.optString("temp", "--°"),
                    weatherCode = item.optInt("code", 0),
                    isDay = item.optBoolean("isDay", true)
                )
            )
        }
    }
}

private fun readDailyForecast(raw: String?): List<WidgetDailyPreview> {
    val array = runCatching { JSONArray(raw ?: "[]") }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until minOf(5, array.length())) {
            val item = array.optJSONObject(index) ?: continue
            add(
                WidgetDailyPreview(
                    day = item.optString("day", "--"),
                    temperature = item.optString("temp", "--° / --°"),
                    weatherCode = item.optInt("code", 0)
                )
            )
        }
    }
}

private fun formatNow(pattern: String): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date())

private fun formatMs(milliseconds: Long): String {
    if (milliseconds <= 0L) return "0:00"
    val totalSeconds = milliseconds / 1000L
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
