with open("app/src/main/res/layout/time_weather_widget.xml", "r") as f:
    text = f.read()

text = text.replace('<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"', '<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"\n    android:layout_width="match_parent"\n    android:layout_height="match_parent"\n    android:background="@drawable/widget_dj_bg"\n    android:fillViewport="true">\n<LinearLayout', 1)
text = text.replace('    android:background="@drawable/widget_dj_bg">\n', '>\n', 1)

text += "</ScrollView>\n"

with open("app/src/main/res/layout/time_weather_widget.xml", "w") as f:
    f.write(text)
