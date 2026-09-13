import re

with open('app/src/main/res/layout/time_weather_widget.xml', 'r') as f:
    xml = f.read()

# Move weather_city inside the middle LinearLayout
new_city = '''
            <TextView
                android:id="@+id/weather_city"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="City"
                android:textColor="#BBBBBB"
                android:textSize="12sp"
                android:maxLines="1"
                android:ellipsize="end" />'''

xml = xml.replace('''
            <TextView
                android:id="@+id/weather_condition"''', new_city + '''
            <TextView
                android:id="@+id/weather_condition"''')

# Remove the old weather_city
xml = re.sub(r'<TextView\s+android:id="@+id/weather_city"\s+android:layout_width="match_parent"\s+android:layout_height="wrap_content"\s+android:visibility="gone"\s*/>', '', xml)

# Wrap weather_status and weather_refresh in a horizontal LinearLayout at the bottom
status_layout = '''
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center"
        android:layout_marginTop="8dp">
        <TextView
            android:id="@+id/weather_status"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Location not set"
            android:textColor="#666666"
            android:textSize="10sp" />
        <ImageButton
            android:id="@+id/weather_refresh"
            android:layout_width="24dp"
            android:layout_height="24dp"
            android:layout_marginStart="8dp"
            android:background="?android:attr/selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_popup_sync"
            android:tint="#888888"
            android:contentDescription="Refresh" />
    </LinearLayout>'''

xml = re.sub(r'<TextView\s+android:id="@+id/weather_status".*?/>', status_layout, xml, flags=re.DOTALL)
xml = re.sub(r'<ImageButton\s+android:id="@+id/weather_refresh"\s+android:layout_width="wrap_content"\s+android:layout_height="wrap_content"\s+android:visibility="gone"\s*/>', '', xml)

with open('app/src/main/res/layout/time_weather_widget.xml', 'w') as f:
    f.write(xml)

print("Updated time_weather_widget.xml")
