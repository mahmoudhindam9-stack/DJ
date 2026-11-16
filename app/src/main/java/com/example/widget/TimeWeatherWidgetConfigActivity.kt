package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.locale.AppLanguageManager
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimeWeatherWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Default to RESULT_CANCELED if user backs out or presses Cancel
        setResult(RESULT_CANCELED)

        val extras = intent?.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        AppLanguageManager.init(this)
        val prefs = getSharedPreferences("time_weather_widget", Context.MODE_PRIVATE)
        val initialAlphaInt = prefs.getInt("bg_alpha_$appWidgetId", 230)

        setContent {
            val isArabic by AppLanguageManager.isArabicFlow.collectAsState()
            val layoutDirection = AppLanguageManager.currentLayoutDirection(isArabic)
            CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides layoutDirection) {
                MyApplicationTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        var alphaValue by remember { mutableStateOf((initialAlphaInt / 255f).coerceIn(0f, 1f)) }
                        val opacityPercent = (alphaValue * 100).toInt()
                        val transparencyPercent = 100 - opacityPercent
                        val previewTime = remember {
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                        }
                        val previewDate = remember {
                            SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date())
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    text = if (isArabic) "إعدادات ويدجت الوقت والطقس" else "Time & Weather Widget Setup",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isArabic) {
                                        "معاينة حية لشفافية الخلفية والحجم الكامل للويدجت قبل التطبيق."
                                    } else {
                                        "Live preview of widget background transparency at full size before applying."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Simulated wallpaper backdrop + live widget transparency preview
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(250.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFF1E3C72),
                                                    Color(0xFF2A5298),
                                                    Color(0xFF7F53AC)
                                                )
                                            )
                                        )
                                        .padding(16.dp)
                                        .testTag("widget_live_preview_container"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Widget card with live background alpha applied
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color(0xFF101420).copy(alpha = alphaValue))
                                            .border(
                                                width = 1.dp,
                                                color = Color.White.copy(alpha = (0.12f + alphaValue * 0.2f).coerceAtMost(0.4f)),
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .padding(16.dp)
                                            .testTag("widget_live_preview_card"),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = previewTime,
                                                    color = Color.White,
                                                    fontSize = 34.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Text(
                                                    text = previewDate,
                                                    color = Color(0xFFB0C4DE),
                                                    fontSize = 13.sp
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Cloud,
                                                    contentDescription = "Weather",
                                                    tint = Color(0xFF64B5F6),
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = "24°C",
                                                        color = Color.White,
                                                        fontSize = 22.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = if (isArabic) "صافي • 5×4" else "Clear • 5×4",
                                                        color = Color(0xFFB0C4DE),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }

                                        // Forecast strip preview
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            listOf("12:00" to "24°", "15:00" to "26°", "18:00" to "22°", "21:00" to "19°", "00:00" to "17°").forEach { (hr, temp) ->
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(text = hr, color = Color(0xFFB0C4DE), fontSize = 10.sp)
                                                    Text(text = temp, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }

                                        // Integrated Music Bar preview
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(Color.White.copy(alpha = 0.08f))
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF2979FF)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.MusicNote,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                Column {
                                                    Text(
                                                        text = "DJ Studio Pro",
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = if (isArabic) "شفافية الخلفية: $transparencyPercent%" else "Transparency: $transparencyPercent%",
                                                        color = Color(0xFFB0C4DE),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Filled.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                                Icon(Icons.Filled.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isArabic) "عتامة الخلفية (Opacity)" else "Background Opacity",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "$opacityPercent% (${if (isArabic) "شفافية" else "Transparent"} $transparencyPercent%)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("widget_alpha_label")
                                    )
                                }

                                Slider(
                                    value = alphaValue,
                                    onValueChange = {
                                        alphaValue = it
                                        // Update live widget preview on home screen while dragging as well
                                        val liveAlphaInt = (it * 255).toInt().coerceIn(0, 255)
                                        prefs.edit().putInt("bg_alpha_$appWidgetId", liveAlphaInt).apply()
                                        val appWidgetManager = AppWidgetManager.getInstance(this@TimeWeatherWidgetConfigActivity)
                                        TimeWeatherWidgetProvider.updateOne(this@TimeWeatherWidgetConfigActivity, appWidgetManager, appWidgetId)
                                    },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.testTag("widget_alpha_slider")
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        // Restore initial alpha on Cancel
                                        prefs.edit().putInt("bg_alpha_$appWidgetId", initialAlphaInt).apply()
                                        val appWidgetManager = AppWidgetManager.getInstance(this@TimeWeatherWidgetConfigActivity)
                                        TimeWeatherWidgetProvider.updateOne(this@TimeWeatherWidgetConfigActivity, appWidgetManager, appWidgetId)
                                        val cancelResult = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                        setResult(RESULT_CANCELED, cancelResult)
                                        finish()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("widget_cancel_button")
                                ) {
                                    Text(if (isArabic) "إلغاء (Cancel)" else "Cancel")
                                }

                                Button(
                                    onClick = {
                                        val alphaInt = (alphaValue * 255).toInt().coerceIn(0, 255)
                                        prefs.edit().putInt("bg_alpha_$appWidgetId", alphaInt).apply()

                                        val appWidgetManager = AppWidgetManager.getInstance(this@TimeWeatherWidgetConfigActivity)
                                        TimeWeatherWidgetProvider.updateOne(this@TimeWeatherWidgetConfigActivity, appWidgetManager, appWidgetId)

                                        val resultValue = Intent().apply {
                                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                        }
                                        setResult(RESULT_OK, resultValue)
                                        finish()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("widget_apply_button")
                                ) {
                                    Text(if (isArabic) "تطبيق (Apply)" else "Apply")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
