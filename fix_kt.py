with open("app/src/main/java/com/example/widget/TimeWeatherWidgetProvider.kt", "r") as f:
    content = f.read()

cond_logic = """val condString = prefs.getString(CONDITION, "Tap refresh") ?: "Tap refresh"
            // Split emoji from text if present
            val spaceIndex = condString.indexOf(' ')
            if (spaceIndex > 0 && condString.length > 2 && condString.codePointAt(0) > 0x2000) {
                views.setTextViewText(R.id.weather_icon, condString.substring(0, spaceIndex))
                views.setTextViewText(R.id.weather_condition, condString.substring(spaceIndex + 1))
            } else {
                views.setTextViewText(R.id.weather_icon, "🌍")
                views.setTextViewText(R.id.weather_condition, condString)
            }"""
            
cond_logic_new = """val condString = prefs.getString(CONDITION, "Tap refresh") ?: "Tap refresh"
            val emojiOrCondition: String
            val spaceIndex = condString.indexOf(' ')
            if (spaceIndex > 0 && condString.length > 2 && condString.codePointAt(0) > 0x2000) {
                emojiOrCondition = condString.substring(0, spaceIndex)
                views.setTextViewText(R.id.weather_condition, condString.substring(spaceIndex + 1))
            } else {
                emojiOrCondition = condString
                views.setTextViewText(R.id.weather_condition, condString)
            }
            views.setImageViewResource(R.id.weather_icon, mapConditionToDrawable(emojiOrCondition))"""

content = content.replace(cond_logic, cond_logic_new)

# Setup mapConditionToDrawable method
companion_start = content.find("companion object {")
map_method = """private fun mapConditionToDrawable(condition: String): Int {
            return when {
                condition.contains("☀️") || condition.contains("Sunny") || condition.contains("Clear") -> R.drawable.ic_weather_sunny
                condition.contains("⛅") || condition.contains("Partly") -> R.drawable.ic_weather_partly_cloudy
                condition.contains("☁") || condition.contains("Cloudy") -> R.drawable.ic_weather_cloudy
                condition.contains("🌧") || condition.contains("Rain") -> R.drawable.ic_weather_rain
                condition.contains("⛈") || condition.contains("Thunder") -> R.drawable.ic_weather_thunderstorm
                condition.contains("❄") || condition.contains("Snow") -> R.drawable.ic_weather_snow
                condition.contains("🌫") || condition.contains("Fog") -> R.drawable.ic_weather_fog
                condition.contains("🌙") || condition.contains("Night") -> R.drawable.ic_weather_clear_night
                else -> R.drawable.ic_weather_unknown
            }
        }
        """

content = content[:companion_start + 18] + "\n        " + map_method + content[companion_start + 18:]

chrono_logic = """// Setup Chronometer
            if (snapshot.third) { // If playing
                views.setChronometer(R.id.widget_timer, android.os.SystemClock.elapsedRealtime(), null, true)
            } else {
                views.setChronometer(R.id.widget_timer, android.os.SystemClock.elapsedRealtime(), null, false)
            }
            
            val progress = PlaybackNotificationRouter.activeProgress(context)
            val positionMs = progress.first
            val durationMs = progress.second"""

chrono_logic_new = """val progress = PlaybackNotificationRouter.activeProgress(context)
            val positionMs = progress.first
            val durationMs = progress.second
            views.setTextViewText(R.id.widget_time_current, com.example.utils.MusicScanner.formatMs(positionMs))
            views.setTextViewText(R.id.widget_time_total, com.example.utils.MusicScanner.formatMs(durationMs))
            val pct = if (durationMs > 0) ((positionMs * 1000) / durationMs).toInt().coerceIn(0, 1000) else 0
            views.setProgressBar(R.id.widget_progress, 1000, pct, false)"""

content = content.replace(chrono_logic, chrono_logic_new)

with open("app/src/main/java/com/example/widget/TimeWeatherWidgetProvider.kt", "w") as f:
    f.write(content)
