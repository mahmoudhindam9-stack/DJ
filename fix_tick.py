import re

# 1. PlaybackNotificationRouter.kt
path = "app/src/main/java/com/example/player/PlaybackNotificationRouter.kt"
with open(path, 'r') as f:
    content = f.read()

if "fun updateProgress" not in content:
    new_methods = """
    @Synchronized
    fun updateProgress(context: Context, source: String, positionMs: Long, durationMs: Long) {
        if (active?.source != source) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putLong("position", positionMs).putLong("duration", durationMs).apply()
        com.example.widget.TimeWeatherWidgetProvider.requestAllUpdates(context)
        MusicWidgetProvider.requestAllUpdates(context)
        QuickPlayerWidgetProvider.requestAllUpdates(context)
    }

    fun activeProgress(context: Context): Pair<Long, Long> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Pair(prefs.getLong("position", 0L), prefs.getLong("duration", 0L))
    }
"""
    content = content.replace("fun activeSnapshot", new_methods + "\n    fun activeSnapshot")
    with open(path, 'w') as f:
        f.write(content)

