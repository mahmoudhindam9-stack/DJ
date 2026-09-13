with open('app/src/main/java/com/example/widget/LocationWeatherActivity.kt', 'r') as f:
    lines = f.readlines()

# let's just rewrite the onCreate properly
replacement = """    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (hasLocationPermission()) {
            fetchLocationAndWeather()
        } else {
            requestPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }
"""

with open('app/src/main/java/com/example/widget/LocationWeatherActivity.kt', 'w') as f:
    f.writelines(lines[:41])
    f.write(replacement)
    f.writelines(lines[53:])

