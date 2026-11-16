import re
content = open("app/src/main/res/layout/time_weather_widget.xml").read()

# Replace root LinearLayout opening tag
old_root = """<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/widget_dj_bg"
    android:orientation="vertical"
    android:paddingLeft="10dp" android:paddingRight="10dp"
    android:paddingTop="6dp" android:paddingBottom="6dp">"""

new_root = """<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:id="@+id/widget_root">
    
    <ImageView
        android:id="@+id/widget_bg_image"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:src="@drawable/widget_dj_bg"
        android:scaleType="fitXY" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:paddingLeft="10dp" android:paddingRight="10dp"
        android:paddingTop="6dp" android:paddingBottom="6dp">"""

content = content.replace(old_root, new_root)
content = content.replace("</LinearLayout>\n    <TextView", "    <TextView") # wait, the warning text view is inside the outer LinearLayout in the original
content = content.replace("</LinearLayout>", "</LinearLayout>\n</FrameLayout>", 1)
content = content.rsplit("</LinearLayout>", 1)[0] + "</LinearLayout>\n</FrameLayout>"

open("app/src/main/res/layout/time_weather_widget.xml", "w").write(content)
