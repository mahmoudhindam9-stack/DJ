package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme

class TimeWeatherWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set result CANCELED initially
        setResult(RESULT_CANCELED)
        
        val intent = intent
        val extras = intent.extras
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

        val prefs = getSharedPreferences("time_weather_widget", Context.MODE_PRIVATE)
        val currentAlpha = prefs.getInt("bg_alpha_$appWidgetId", 255)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var alphaValue by remember { mutableStateOf(currentAlpha / 255f) }

                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Configure Widget",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(text = "Background Transparency: ${(alphaValue * 100).toInt()}%")
                        Slider(
                            value = alphaValue,
                            onValueChange = { alphaValue = it },
                            valueRange = 0f..1f
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(onClick = {
                                val alphaInt = (alphaValue * 255).toInt()
                                prefs.edit().putInt("bg_alpha_$appWidgetId", alphaInt).apply()
                                
                                val appWidgetManager = AppWidgetManager.getInstance(this@TimeWeatherWidgetConfigActivity)
                                TimeWeatherWidgetProvider.updateOne(this@TimeWeatherWidgetConfigActivity, appWidgetManager, appWidgetId)
                                
                                val resultValue = Intent()
                                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                setResult(RESULT_OK, resultValue)
                                finish()
                            }) {
                                Text("Apply")
                            }
                        }
                    }
                }
            }
        }
    }
}
