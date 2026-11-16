content = open("app/src/main/res/layout/time_weather_widget.xml").read()
content = content.replace("""            </LinearLayout>
        </LinearLayout>
        </LinearLayout>
    </LinearLayout>

    <TextView""", """            </LinearLayout>
        </LinearLayout>
    </LinearLayout>

    <TextView""")
open("app/src/main/res/layout/time_weather_widget.xml", "w").write(content)
