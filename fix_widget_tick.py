import re

path = "app/src/main/java/com/example/widget/TimeWeatherWidgetProvider.kt"
with open(path, 'r') as f:
    content = f.read()

if "val progress =" not in content:
    rep = """
            val progress = PlaybackNotificationRouter.activeProgress(context)
            val positionMs = progress.first
            val durationMs = progress.second
            val pct = if (durationMs > 0) ((positionMs * 1000) / durationMs).toInt().coerceIn(0, 1000) else 0
            views.setProgressBar(R.id.widget_progress, 1000, pct, false)
            WidgetPlaybackIntents.wireButtons(context, views, id, R.id.widget_btn_prev, R.id.widget_btn_play, R.id.widget_btn_next)
"""
    content = content.replace("WidgetPlaybackIntents.wireButtons(context, views, id, R.id.widget_btn_prev, R.id.widget_btn_play, R.id.widget_btn_next)", rep)

with open(path, 'w') as f:
    f.write(content)
