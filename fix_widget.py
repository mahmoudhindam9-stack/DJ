with open('app/src/main/res/layout/time_weather_widget.xml', 'r') as f:
    text = f.read()

text = text.replace('android:textSize="14sp"', 'android:textSize="12sp"')
text = text.replace('android:textSize="11sp"', 'android:textSize="10sp"')
text = text.replace('android:layout_width="44dp"', 'android:layout_width="38dp"')
text = text.replace('android:layout_height="44dp"', 'android:layout_height="38dp"')
text = text.replace('android:layout_width="36dp"', 'android:layout_width="30dp"')
text = text.replace('android:layout_height="36dp"', 'android:layout_height="30dp"')

# Add weather warning TextView
warning_xml = '''
        <TextView android:id="@+id/weather_warning" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="" android:textColor="#FF5555" android:textSize="11sp" android:textStyle="bold" android:visibility="gone" android:layout_marginTop="2dp" />'''
text = text.replace('</LinearLayout>\n                \n        <TextView android:id="@+id/weather_status"', '</LinearLayout>' + warning_xml + '\n                \n        <TextView android:id="@+id/weather_status"')

with open('app/src/main/res/layout/time_weather_widget.xml', 'w') as f:
    f.write(text)
