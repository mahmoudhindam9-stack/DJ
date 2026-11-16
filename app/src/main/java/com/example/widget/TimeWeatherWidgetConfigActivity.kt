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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        val (trackTitle, trackArtist, isPlaying) = PlaybackNotificationRouter.activeSnapshot(this)

        setContent {
            MyApplicationTheme {
                TimeWeatherWidgetConfigurator(
                    initialTransparency = 1f - (initialAlpha / 255f),
                    city = weatherCity,
                    temperature = weatherTemp,
                    condition = weatherCondition,
                    weatherStatus = weatherStatus,
                    trackTitle = trackTitle,
                    trackArtist = trackArtist,
                    isPlaying = isPlaying,
                    onCancel = { finish() },
                    onApply = { transparency ->
                        val alpha = ((1f - transparency) * 255f).toInt().coerceIn(0, 255)
                        prefs.edit().putInt("bg_alpha_$appWidgetId", alpha).apply()
                        val widgetManager = AppWidgetManager.getInstance(this)
                        TimeWeatherWidgetProvider.updateOne(this, widgetManager, appWidgetId)
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
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
    trackTitle: String,
    trackArtist: String,
    isPlaying: Boolean,
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
                "Preview the widget and adjust its background transparency",
                color = muted,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(22.dp))

            // The widget preview mirrors the actual home-screen widget.
            // Only the background image alpha changes; its content stays readable.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 310.dp)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(panel)
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⛅", fontSize = 34.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(temperature, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                            Text(city, color = muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(condition, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(clock, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                            Text(date, color = muted, fontSize = 11.sp)
                            Text(weatherStatus, color = muted.copy(alpha = 0.8f), fontSize = 8.sp, maxLines = 1)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(panel)
                            .padding(horizontal = 12.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                        Spacer(Modifier.width(13.dp))
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF247FD1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (isPlaying) "Ⅱ" else "▶", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(13.dp))
                        Text("▶|", color = Color.White, fontSize = 17.sp)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(panel)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("HOURLY FORECAST", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("5-DAY FORECAST", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("Next hours  •  Local forecast", color = muted, fontSize = 10.sp)
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
                    Text("The preview updates as you move the slider", color = muted, fontSize = 11.sp)
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

private fun formatNow(pattern: String): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
