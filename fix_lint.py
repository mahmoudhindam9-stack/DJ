with open('app/src/main/java/com/example/widget/LocationWeatherActivity.kt', 'r') as f:
    content = f.read()

content = content.replace(
    "private val requestPermissionLauncher = registerForActivityResult(",
    "@android.annotation.SuppressLint(\"InvalidFragmentVersionForActivityResult\")\n    private val requestPermissionLauncher = registerForActivityResult("
)

with open('app/src/main/java/com/example/widget/LocationWeatherActivity.kt', 'w') as f:
    f.write(content)
