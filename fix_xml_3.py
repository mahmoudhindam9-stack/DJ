with open("app/src/main/res/layout/time_weather_widget.xml", "r") as f:
    content = f.read()

chronometer = """<Chronometer
                android:id="@+id/widget_timer"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textSize="12sp"
                android:textColor="#888888"
                android:layout_marginTop="2dp" />"""

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

content = content.replace(chronometer, rep_progress)

with open("app/src/main/res/layout/time_weather_widget.xml", "w") as f:
    f.write(content)
