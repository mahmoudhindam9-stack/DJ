import re

# 1. Remove dummy crossfader timer from MainPlayerExperience.kt
with open('app/src/main/java/com/example/MainPlayerExperience.kt', 'r') as f:
    text = f.read()

text = re.sub(r'Text\("Crossfade Duration:.*?\n', '', text)
text = re.sub(r'Slider\(value = playerController\.crossfadeDurationMs\.toFloat\(\).*?\n', '', text)

with open('app/src/main/java/com/example/MainPlayerExperience.kt', 'w') as f:
    f.write(text)

# 2. Add Date to time_weather_widget.xml
with open('app/src/main/res/layout/time_weather_widget.xml', 'r') as f:
    xml = f.read()

date_clock = '\n            <TextClock android:id="@+id/weather_date" android:layout_width="match_parent" android:layout_height="wrap_content" android:format12Hour="MMM d, yyyy" android:format24Hour="MMM d, yyyy" android:textColor="#CCCCCC" android:textSize="12sp" android:layout_marginBottom="4dp"/>'
xml = xml.replace('</LinearLayout>\n        <TextView android:id="@+id/weather_city"', '</LinearLayout>' + date_clock + '\n        <TextView android:id="@+id/weather_city"')

with open('app/src/main/res/layout/time_weather_widget.xml', 'w') as f:
    f.write(xml)

# 3. Ensure LocationWeatherActivity allows starting from background if possible, or just exported=true
with open('app/src/main/AndroidManifest.xml', 'r') as f:
    manifest = f.read()
manifest = manifest.replace('.widget.LocationWeatherActivity" android:exported="false"', '.widget.LocationWeatherActivity" android:exported="true"')
manifest = manifest.replace('.widget.WidgetActionReceiver" android:exported="false"', '.widget.WidgetActionReceiver" android:exported="true"')

with open('app/src/main/AndroidManifest.xml', 'w') as f:
    f.write(manifest)

