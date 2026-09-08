import re

with open("/app/applet/app/src/main/java/com/example/widget/TimeWeatherWidgetProvider.kt", "r") as f:
    content = f.read()

# Add import for MainActivity
if "import com.example.MainActivity" not in content:
    content = content.replace("import com.example.R", "import com.example.MainActivity\nimport com.example.R")

# Update view setting
target_views = """            views.setTextViewText(R.id.weather_temp, prefs.getString(TEMP, "--°C") ?: "--°C")
            views.setTextViewText(R.id.weather_condition, prefs.getString(CONDITION, "Tap refresh") ?: "Tap refresh")"""

replacement_views = """            views.setTextViewText(R.id.weather_temp, prefs.getString(TEMP, "--°C") ?: "--°C")
            val condString = prefs.getString(CONDITION, "Tap refresh") ?: "Tap refresh"
            // Split emoji from text if present
            val spaceIndex = condString.indexOf(' ')
            if (spaceIndex > 0 && condString.length > 2 && condString.codePointAt(0) > 0x2000) {
                views.setTextViewText(R.id.weather_icon, condString.substring(0, spaceIndex))
                views.setTextViewText(R.id.weather_condition, condString.substring(spaceIndex + 1))
            } else {
                views.setTextViewText(R.id.weather_icon, "🌍")
                views.setTextViewText(R.id.weather_condition, condString)
            }"""
content = content.replace(target_views, replacement_views)

# Add intent for opening player
target_intents = """            views.setOnClickPendingIntent(R.id.widget_btn_next, PendingIntent.getBroadcast(context, base + 2, Intent(context, WidgetActionReceiver::class.java).setAction(MusicService.ACTION_NEXT), flags))"""
replacement_intents = """            views.setOnClickPendingIntent(R.id.widget_btn_next, PendingIntent.getBroadcast(context, base + 2, Intent(context, WidgetActionReceiver::class.java).setAction(MusicService.ACTION_NEXT), flags))
            
            // Open player on click
            val openPlayerIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("open_route", "player")
            }
            val openPlayerPending = PendingIntent.getActivity(context, id * 42, openPlayerIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_title, openPlayerPending)
            views.setOnClickPendingIntent(R.id.widget_artist, openPlayerPending)
            views.setOnClickPendingIntent(R.id.widget_music_container, openPlayerPending)"""
content = content.replace(target_intents, replacement_intents)

with open("/app/applet/app/src/main/java/com/example/widget/TimeWeatherWidgetProvider.kt", "w") as f:
    f.write(content)
