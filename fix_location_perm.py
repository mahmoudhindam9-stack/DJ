import re

with open('app/src/main/java/com/example/widget/LocationWeatherActivity.kt', 'r') as f:
    content = f.read()

# We need to find the onCreate and replace the permission check.
# Existing:
'''
        if (!hasLocationPermission()) {
            val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(permissionPromptedKey, false)) {
                prefs.edit().putBoolean(permissionPromptedKey, true).apply()
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 9001)
            } else {
                // Already prompted and denied, fallback to IP location
                fetchWeatherByIpFallback()
            }
            return
        }
        fetchLocationAndWeather()
'''

new_check = '''
        if (!hasLocationPermission()) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) || shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                // Denied previously but not permanently, or we should show rationale. We'll just request again.
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 9001)
            } else {
                val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                val prompted = prefs.getBoolean(permissionPromptedKey, false)
                if (!prompted) {
                    prefs.edit().putBoolean(permissionPromptedKey, true).apply()
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 9001)
                } else {
                    // Permanently denied (or don't ask again). Fallback.
                    fetchWeatherByIpFallback()
                }
            }
            return
        }
        fetchLocationAndWeather()
'''

# Wait, the instruction says: "Remove the permanent 'already prompted' gate."
# "Do not create a permanent SharedPreferences flag that blocks future permission requests."

new_check2 = '''
        if (hasLocationPermission()) {
            fetchLocationAndWeather()
        } else {
            // Check if we should ask. On Android M+, requestPermissions can just be called.
            // If they selected "Don't ask again", requestPermissions will immediately call onRequestPermissionsResult with DENIED.
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 9001)
        }
'''

content = re.sub(r'if \(!hasLocationPermission\(\)\) \{.*?fetchLocationAndWeather\(\)', new_check2, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/widget/LocationWeatherActivity.kt', 'w') as f:
    f.write(content)

print("Updated LocationWeatherActivity.kt")
