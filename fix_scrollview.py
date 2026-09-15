content = open("app/src/main/res/layout/time_weather_widget.xml").read()
content = content.replace("""        <ScrollView
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:scrollbars="none"
            android:fillViewport="true">
            
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical">""", "")
content = content.replace("""        </LinearLayout>
        </ScrollView>
    </LinearLayout>

    <TextView""", """        </LinearLayout>
    </LinearLayout>

    <TextView""")
open("app/src/main/res/layout/time_weather_widget.xml", "w").write(content)
