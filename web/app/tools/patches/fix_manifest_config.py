import re
content = open("app/src/main/AndroidManifest.xml").read()

if "TimeWeatherWidgetConfigActivity" not in content:
    config_activity = """
        <activity android:name=".widget.TimeWeatherWidgetConfigActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
            </intent-filter>
        </activity>
"""
    content = content.replace("</application>", config_activity + "    </application>")
    open("app/src/main/AndroidManifest.xml", "w").write(content)
