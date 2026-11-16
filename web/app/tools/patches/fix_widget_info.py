import re
content = open("app/src/main/res/xml/time_weather_widget_info.xml").read()

if "android:configure" not in content:
    content = content.replace('android:description="@string/widget_time_weather_description" />',
        'android:description="@string/widget_time_weather_description"\n    android:configure="com.example.widget.TimeWeatherWidgetConfigActivity" />')
    open("app/src/main/res/xml/time_weather_widget_info.xml", "w").write(content)
