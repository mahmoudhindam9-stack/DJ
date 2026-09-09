import re
with open("app/src/main/res/layout/time_weather_widget.xml", "r") as f:
    content = f.read()

rep_progress = """<LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:layout_marginTop="4dp">
            <TextView
                android:id="@+id/widget_time_current"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="0:00"
                android:textSize="11sp"
                android:textColor="#888888" />
            <ProgressBar
                android:id="@+id/widget_progress"
                style="@android:style/Widget.ProgressBar.Horizontal"
                android:layout_width="0dp"
                android:layout_height="6dp"
                android:layout_weight="1"
                android:layout_marginHorizontal="6dp"
                android:max="1000"
                android:progress="0" />
            <TextView
                android:id="@+id/widget_time_total"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="0:00"
                android:textSize="11sp"
                android:textColor="#888888" />
        </LinearLayout>"""

content = re.sub(r'<Chronometer\s+android:id="@+id/widget_timer"\s+android:layout_width="match_parent"\s+android:layout_height="wrap_content"\s+android:textSize="12sp"\s+android:textColor="#888888"\s+android:layout_marginTop="2dp"\s*/>', rep_progress, content)
with open("app/src/main/res/layout/time_weather_widget.xml", "w") as f:
    f.write(content)
