package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Music Player", appName)
  }

  @Test
  fun `inflate time weather widget remote views`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val views = android.widget.RemoteViews(context.packageName, R.layout.time_weather_widget)
    val view = views.apply(context, null)
    org.junit.Assert.assertNotNull(view)
  }

  @Test
  fun `time weather widget updates and applies successfully`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.widget.TimeWeatherWidgetProvider.updateWeather(
        context = context,
        city = "London",
        temperature = "18°C",
        condition = "⛅ Partly Cloudy",
        timezone = "Europe/London",
        warning = "",
        hourlyJson = """[{"time":"12:00","temp":"18°","code":1,"isDay":true},{"time":"13:00","temp":"19°","code":2,"isDay":true},{"time":"14:00","temp":"20°","code":3,"isDay":true},{"time":"15:00","temp":"19°","code":61,"isDay":true},{"time":"16:00","temp":"17°","code":0,"isDay":true}]""",
        dailyJson = """[{"day":"Today","temp":"20° / 12°","code":1},{"day":"Mon","temp":"21° / 13°","code":2},{"day":"Tue","temp":"19° / 11°","code":3},{"day":"Wed","temp":"18° / 10°","code":61},{"day":"Thu","temp":"22° / 14°","code":0}]"""
    )
    val views = android.widget.RemoteViews(context.packageName, R.layout.time_weather_widget)
    val view = views.apply(context, null)
    org.junit.Assert.assertNotNull(view)
  }
}
