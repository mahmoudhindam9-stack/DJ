with open('app/src/main/res/layout/time_weather_widget.xml', 'r') as f:
    lines = f.readlines()

new_lines = []
skip = False
for line in lines:
    if 'android:id="@+id/weather_status"' in line:
        skip = True
        new_lines.append('''    <LinearLayout
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
    </LinearLayout>
''')
    elif skip and '/>' in line:
        skip = False
    elif not skip:
        # Also remove the duplicate city and refresh blocks manually
        if 'android:id="@+id/weather_city"' in line and len(new_lines) > 200:
            skip = True
        elif 'android:id="@+id/weather_refresh"' in line and len(new_lines) > 200:
            skip = True
        else:
            new_lines.append(line)

with open('app/src/main/res/layout/time_weather_widget.xml', 'w') as f:
    f.writelines(new_lines)

