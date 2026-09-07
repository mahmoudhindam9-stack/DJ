with open('app/src/main/java/com/example/widget/TimeWeatherWidgetProvider.kt', 'r') as f:
    text = f.read()

text = text.replace('private const val STATUS = "status"', 'private const val STATUS = "status"\n        private const val WARNING = "warning"')

func_old = 'fun updateWeather(context: Context, city: String, temperature: String, condition: String, timezone: String) {'
func_new = 'fun updateWeather(context: Context, city: String, temperature: String, condition: String, timezone: String, warning: String = "") {'
text = text.replace(func_old, func_new)

text = text.replace('.putString(TIMEZONE, timezone).putString(STATUS, "Updated now").apply()', '.putString(TIMEZONE, timezone).putString(STATUS, "Updated now").putString(WARNING, warning).apply()')

ui_bind = '''            views.setTextViewText(R.id.weather_status, prefs.getString(STATUS, "Location not set") ?: "Location not set")
            
            val warningTxt = prefs.getString(WARNING, "") ?: ""
            if (warningTxt.isNotEmpty()) {
                views.setViewVisibility(R.id.weather_warning, android.view.View.VISIBLE)
                views.setTextViewText(R.id.weather_warning, warningTxt)
            } else {
                views.setViewVisibility(R.id.weather_warning, android.view.View.GONE)
            }'''
text = text.replace('views.setTextViewText(R.id.weather_status, prefs.getString(STATUS, "Location not set") ?: "Location not set")', ui_bind)

with open('app/src/main/java/com/example/widget/TimeWeatherWidgetProvider.kt', 'w') as f:
    f.write(text)
