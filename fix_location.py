with open('app/src/main/java/com/example/widget/LocationWeatherActivity.kt', 'r') as f:
    text = f.read()

data_old = 'private data class WeatherResult(val city: String, val temperature: String, val condition: String, val timezone: String)'
data_new = 'private data class WeatherResult(val city: String, val temperature: String, val condition: String, val timezone: String, val warning: String)'
text = text.replace(data_old, data_new)

cond_func_old = '''    private fun weatherDescription(code: Int): String = when (code) {
        0 -> "Clear sky"
        1, 2, 3 -> "Partly cloudy"
        45, 48 -> "Foggy"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Rain"
        71, 73, 75, 77, 85, 86 -> "Snow"
        95, 96, 99 -> "Thunderstorm"
        else -> "Weather update"
    }'''

cond_func_new = '''    private fun weatherDescription(code: Int): Pair<String, String> = when (code) {
        0 -> "☀️ Clear sky" to ""
        1, 2 -> "🌤️ Partly cloudy" to ""
        3 -> "☁️ Overcast" to ""
        45, 48 -> "🌫️ Foggy" to "Low visibility due to fog"
        51, 53, 55, 56, 57 -> "🌦️ Drizzle" to ""
        61, 63, 65 -> "🌧️ Rain" to ""
        66, 67 -> "🌧️ Freezing Rain" to "Slippery roads warning"
        71, 73, 75 -> "❄️ Snow" to ""
        77, 85, 86 -> "❄️ Heavy Snow" to "Heavy snow warning"
        80, 81, 82 -> "🌧️ Heavy Rain" to "Heavy rain warning"
        95, 96, 99 -> "⛈️ Thunderstorm" to "Thunderstorm warning! Stay safe."
        else -> "🌍 Weather update" to ""
    }'''
text = text.replace(cond_func_old, cond_func_new)

parse_old = '''        return WeatherResult(
            city = reverseGeocode(latitude, longitude),
            temperature = String.format(Locale.getDefault(), "%.0f°C", current.getDouble("temperature_2m")),
            condition = weatherDescription(current.getInt("weather_code")),
            timezone = timezone
        )'''
parse_new = '''        val (desc, warn) = weatherDescription(current.getInt("weather_code"))
        return WeatherResult(
            city = reverseGeocode(latitude, longitude),
            temperature = String.format(Locale.getDefault(), "%.0f°C", current.getDouble("temperature_2m")),
            condition = desc,
            timezone = timezone,
            warning = warn
        )'''
text = text.replace(parse_old, parse_new)

update_old = 'TimeWeatherWidgetProvider.updateWeather(this@LocationWeatherActivity, result.city, result.temperature, result.condition, result.timezone)'
update_new = 'TimeWeatherWidgetProvider.updateWeather(this@LocationWeatherActivity, result.city, result.temperature, result.condition, result.timezone, result.warning)'
text = text.replace(update_old, update_new)

with open('app/src/main/java/com/example/widget/LocationWeatherActivity.kt', 'w') as f:
    f.write(text)
