# 1. Fix XML
with open('app/src/main/res/layout/time_weather_widget.xml', 'r') as f:
    text = f.read()

warning_xml = '''        <TextView android:id="@+id/weather_warning" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="" android:textColor="#FF5555" android:textSize="11sp" android:textStyle="bold" android:visibility="gone" android:layout_marginTop="2dp" />
'''
if "weather_warning" not in text:
    text = text.replace('<TextView android:id="@+id/weather_status"', warning_xml + '        <TextView android:id="@+id/weather_status"')
    with open('app/src/main/res/layout/time_weather_widget.xml', 'w') as f:
        f.write(text)

# 2. Fix DJFxRackScreen.kt
with open('app/src/main/java/com/example/DJFxRackScreen.kt', 'r') as f:
    fx = f.read()

# We might have broken imports or something. The error was syntax error expecting an argument at 165 and 213.
# Let's print those lines.
