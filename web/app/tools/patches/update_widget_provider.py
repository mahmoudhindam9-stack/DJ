import re
content = open("app/src/main/java/com/example/widget/TimeWeatherWidgetProvider.kt").read()

# Add a method to apply the alpha
update_one_def = """        fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {"""

update_one_new = """        fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val views = RemoteViews(context.packageName, R.layout.time_weather_widget)
            
            val alpha = prefs.getInt("bg_alpha_$id", 255)
            views.setInt(R.id.widget_bg_image, "setImageAlpha", alpha)
"""

content = content.replace("        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {\n            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)\n            val views = RemoteViews(context.packageName, R.layout.time_weather_widget)", update_one_new)

# Also need to make updateOne public so the config activity can call it
open("app/src/main/java/com/example/widget/TimeWeatherWidgetProvider.kt", "w").write(content)
