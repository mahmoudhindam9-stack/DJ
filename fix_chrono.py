import re
with open("app/src/main/java/com/example/widget/TimeWeatherWidgetProvider.kt", "r") as f:
    content = f.read()

chrono_logic_new = """val progress = PlaybackNotificationRouter.activeProgress(context)
            val positionMs = progress.first
            val durationMs = progress.second
            views.setTextViewText(R.id.widget_time_current, com.example.utils.MusicScanner.formatMs(positionMs))
            views.setTextViewText(R.id.widget_time_total, com.example.utils.MusicScanner.formatMs(durationMs))
            val pct = if (durationMs > 0) ((positionMs * 1000) / durationMs).toInt().coerceIn(0, 1000) else 0
            views.setProgressBar(R.id.widget_progress, 1000, pct, false)"""

content = re.sub(r'// Setup Chronometer\s*if \(snapshot\.third\) \{ // If playing\s*views\.setChronometer\(R\.id\.widget_timer, android\.os\.SystemClock\.elapsedRealtime\(\), null, true\)\s*\} else \{\s*views\.setChronometer\(R\.id\.widget_timer, android\.os\.SystemClock\.elapsedRealtime\(\), null, false\)\s*\}\s*val progress = PlaybackNotificationRouter\.activeProgress\(context\)\s*val positionMs = progress\.first\s*val durationMs = progress\.second', chrono_logic_new, content)

with open("app/src/main/java/com/example/widget/TimeWeatherWidgetProvider.kt", "w") as f:
    f.write(content)
