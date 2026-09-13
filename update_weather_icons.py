import os

icons = {
    "ic_weather_clear_night.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24.0"
    android:viewportHeight="24.0">
    <path
        android:fillColor="#FFC107"
        android:pathData="M10,2c-1.82,0 -3.53,0.5 -5,1.35c2.99,1.73 5,4.95 5,8.65s-2.01,6.92 -5,8.65C6.47,21.5 8.18,22 10,22c5.52,0 10,-4.48 10,-10S15.52,2 10,2z"/>
</vector>""",

    "ic_weather_sunny.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24.0"
    android:viewportHeight="24.0">
    <path
        android:fillColor="#FFC107"
        android:pathData="M12,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5 5,-2.24 5,-5 -2.24,-5 -5,-5zM2,13h2c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1H2c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zM20,13h2c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1h-2c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zM11,2v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1V2c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1zM11,20v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1v-2c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1zM5.99,4.58c-0.39,-0.39 -1.03,-0.39 -1.41,0 -0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0 0.39,-0.39 0.39,-1.03 0,-1.41L5.99,4.58zM18.36,16.95c-0.39,-0.39 -1.03,-0.39 -1.41,0 -0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0 0.39,-0.39 0.39,-1.03 0,-1.41l-1.06,-1.06zM19.42,5.99c0.39,-0.39 0.39,-1.03 0,-1.41 -0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41 0.39,0.39 1.03,0.39 1.41,0l1.06,-1.06zM7.05,18.36c0.39,-0.39 0.39,-1.03 0,-1.41 -0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41 0.39,0.39 1.03,0.39 1.41,0l1.06,-1.06z"/>
</vector>""",

    "ic_weather_cloudy.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24.0"
    android:viewportHeight="24.0">
    <path
        android:fillColor="#90A4AE"
        android:pathData="M19.36,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.64,-4.96z"/>
</vector>""",

    "ic_weather_partly_cloudy.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24.0"
    android:viewportHeight="24.0">
    <path
        android:fillColor="#FFC107"
        android:pathData="M12,4c-0.55,0 -1,-0.45 -1,-1V1c0,-0.55 0.45,-1 1,-1s1,0.45 1,1v2C13,3.55 12.55,4 12,4zM6.34,7.76c-0.39,0.39 -1.02,0.39 -1.41,0l-1.42,-1.42c-0.39,-0.39 -0.39,-1.02 0,-1.41s1.02,-0.39 1.41,0l1.42,1.42C6.73,6.73 6.73,7.37 6.34,7.76zM4,12c0,0.55 -0.45,1 -1,1H1c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1h2C3.55,11 4,11.45 4,12zM6.34,16.24L4.93,17.66c-0.39,0.39 -0.39,1.02 0,1.41 0.39,0.39 1.02,0.39 1.41,0l1.42,-1.42c0.39,-0.39 0.39,-1.02 0,-1.41S6.73,15.85 6.34,16.24zM12,8c2.21,0 4,1.79 4,4 0,0.44 -0.08,0.86 -0.21,1.26 1.41,-0.6 3.01,-0.75 4.54,-0.34 0.44,-0.92 0.67,-1.92 0.67,-2.92 0,-3.87 -3.13,-7 -7,-7C10.74,3 7.82,5.4 7.14,8.55 8.52,7.56 10.18,7 12,8z"/>
    <path
        android:fillColor="#90A4AE"
        android:pathData="M19.36,12.04c-0.69,-3.45 -3.72,-6.04 -7.36,-6.04 -2.89,0 -5.4,1.64 -6.65,4.04C2.34,10.36 0,12.91 0,16c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5C24,14.36 21.95,12.22 19.36,12.04z"/>
</vector>""",

    "ic_weather_rain.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24.0"
    android:viewportHeight="24.0">
    <path
        android:fillColor="#90A4AE"
        android:pathData="M19.36,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.64,-4.96z"/>
    <path
        android:fillColor="#4FC3F7"
        android:pathData="M7,17c-0.55,0 -1,0.45 -1,1v3c0,0.55 0.45,1 1,1s1,-0.45 1,-1v-3C8,17.45 7.55,17 7,17zM12,17c-0.55,0 -1,0.45 -1,1v3c0,0.55 0.45,1 1,1s1,-0.45 1,-1v-3C13,17.45 12.55,17 12,17zM17,17c-0.55,0 -1,0.45 -1,1v3c0,0.55 0.45,1 1,1s1,-0.45 1,-1v-3C18,17.45 17.55,17 17,17z"/>
</vector>""",

    "ic_weather_snow.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24.0"
    android:viewportHeight="24.0">
    <path
        android:fillColor="#90A4AE"
        android:pathData="M19.36,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.64,-4.96z"/>
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M6,19c0,0.55 0.45,1 1,1s1,-0.45 1,-1 -0.45,-1 -1,-1S6,18.45 6,19zM11,19c0,0.55 0.45,1 1,1s1,-0.45 1,-1 -0.45,-1 -1,-1S11,18.45 11,19zM16,19c0,0.55 0.45,1 1,1s1,-0.45 1,-1 -0.45,-1 -1,-1S16,18.45 16,19z"/>
</vector>""",

    "ic_weather_thunderstorm.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24.0"
    android:viewportHeight="24.0">
    <path
        android:fillColor="#90A4AE"
        android:pathData="M19.36,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.64,-4.96z"/>
    <path
        android:fillColor="#FFEB3B"
        android:pathData="M14,14h-3l1,-4H9l-2,6h3l-1,5z"/>
</vector>""",

    "ic_weather_fog.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24.0"
    android:viewportHeight="24.0">
    <path
        android:fillColor="#90A4AE"
        android:pathData="M4,17h16c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1H4c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zM4,21h16c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1H4c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zM19.36,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14v0.5h19.5V14c0,-1.64 -0.66,-3.12 -1.73,-4.2l-0.41,-0.41 1.95,-1.95 -1.41,-1.41 -1.41,1.41 0.41,0.41c1.23,1.23 1.99,2.94 1.99,4.82z"/>
</vector>""",

    "ic_weather_unknown.xml": """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24.0"
    android:viewportHeight="24.0">
    <path
        android:fillColor="#90A4AE"
        android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,19h-2v-2h2v2zM15.07,11.25l-0.9,0.92C13.45,12.9 13,13.5 13,15h-2v-0.5c0,-1.1 0.45,-2.1 1.17,-2.83l1.24,-1.26c0.37,-0.36 0.59,-0.86 0.59,-1.41 0,-1.1 -0.9,-2 -2,-2s-2,0.9 -2,2H8c0,-2.21 1.79,-4 4,-4s4,1.79 4,4c0,0.88 -0.36,1.68 -0.93,2.25z"/>
</vector>"""
}

base_dir = "/app/applet/app/src/main/res/drawable/"
for file_name, content in icons.items():
    with open(os.path.join(base_dir, file_name), "w") as f:
        f.write(content)
print("Icons updated successfully!")
