import re

content = open("app/src/main/res/layout/time_weather_widget.xml").read()
# Let's just generate the daily rows
daily = """            <!-- Day 1 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:paddingTop="1dp" android:paddingBottom="1dp">
                <TextView
                    android:id="@+id/daily_day_1"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="Today"
                    android:textColor="#FFFFFF"
                    android:textSize="10sp"
                    android:textStyle="bold" />
                <ImageView
                    android:id="@+id/daily_icon_1"
                    android:layout_width="16dp"
                    android:layout_height="16dp"
                    android:src="@drawable/ic_weather_sunny"
                    android:contentDescription="Daily weather icon"
                    android:layout_marginEnd="8dp" />
                <TextView
                    android:id="@+id/daily_temp_1"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="--° / --°"
                    android:textColor="#CCCCCC"
                    android:textSize="10sp"
                    android:textStyle="bold" />
            </LinearLayout>"""

for i in range(2, 6):
    day_name = "Tomorrow" if i == 2 else f"Day {i}"
    item = f"""
            <!-- Day {i} -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:gravity="center_vertical"
                android:paddingTop="1dp" android:paddingBottom="1dp">
                <TextView
                    android:id="@+id/daily_day_{i}"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="{day_name}"
                    android:textColor="#FFFFFF"
                    android:textSize="10sp"
                    android:textStyle="bold" />
                <ImageView
                    android:id="@+id/daily_icon_{i}"
                    android:layout_width="16dp"
                    android:layout_height="16dp"
                    android:src="@drawable/ic_weather_sunny"
                    android:contentDescription="Daily weather icon"
                    android:layout_marginEnd="8dp" />
                <TextView
                    android:id="@+id/daily_temp_{i}"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="--° / --°"
                    android:textColor="#CCCCCC"
                    android:textSize="10sp"
                    android:textStyle="bold" />
            </LinearLayout>"""
    daily += item

end_tags = """
        </LinearLayout>
        </LinearLayout>
        </ScrollView>
    </LinearLayout>

    <TextView
        android:id="@+id/weather_warning"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:visibility="gone" />
</LinearLayout>
"""

# Now we need to replace everything after "<!-- Day 1 -->" with daily + end_tags
idx = content.find("            <!-- Day 1 -->")
new_content = content[:idx] + daily + end_tags
open("app/src/main/res/layout/time_weather_widget.xml", "w").write(new_content)
