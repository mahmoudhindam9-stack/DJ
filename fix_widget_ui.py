import re

with open("/app/applet/app/src/main/res/layout/time_weather_widget.xml", "r") as f:
    xml = f.read()

# 1. Reduce padding to make it smaller slightly
xml = xml.replace('android:padding="14dp"', 'android:padding="10dp"')

# 2. Add a separate TextView for the weather icon, make temperature and condition bigger
old_temp_layout = """        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:gravity="center_vertical" android:layout_marginTop="2dp">
            <TextView android:id="@+id/weather_temp" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="--°C" android:textColor="@android:color/white" android:textSize="25sp" android:textStyle="bold" />
            <TextView android:id="@+id/weather_condition" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:layout_marginStart="8dp" android:text="Tap refresh" android:textColor="#CCCCCC" android:textSize="10sp" android:maxLines="2" />
        </LinearLayout>"""

new_temp_layout = """        <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:gravity="center_vertical" android:layout_marginTop="2dp">
            <TextView android:id="@+id/weather_icon" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="🌍" android:textSize="40sp" android:layout_marginEnd="8dp"/>
            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical">
                <TextView android:id="@+id/weather_temp" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="--°C" android:textColor="@android:color/white" android:textSize="28sp" android:textStyle="bold" />
                <TextView android:id="@+id/weather_condition" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Tap refresh" android:textColor="#EEEEEE" android:textSize="14sp" android:textStyle="bold" android:maxLines="2" />
            </LinearLayout>
        </LinearLayout>"""

xml = xml.replace(old_temp_layout, new_temp_layout)

# 3. Increase player font size
xml = xml.replace('android:id="@+id/widget_title" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="مشغل الموسيقى" android:textSize="12sp"', 
                  'android:id="@+id/widget_title" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="مشغل الموسيقى" android:textSize="16sp"')

xml = xml.replace('android:id="@+id/widget_artist" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="موسيقى" android:textSize="10sp"',
                  'android:id="@+id/widget_artist" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="موسيقى" android:textSize="14sp"')

# Add an ID to the music container so we can bind click listener to open the player
xml = xml.replace('<LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="4dp" android:gravity="center" android:orientation="horizontal">',
                  '<LinearLayout android:id="@+id/widget_music_container" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="4dp" android:gravity="center" android:orientation="horizontal">')

with open("/app/applet/app/src/main/res/layout/time_weather_widget.xml", "w") as f:
    f.write(xml)
