import re

with open('app/src/main/java/com/example/widget/LocationWeatherActivity.kt', 'r') as f:
    content = f.read()

# Add lifecycleScope import if missing
if 'import androidx.lifecycle.lifecycleScope' not in content:
    content = content.replace('import kotlinx.coroutines.launch', 'import kotlinx.coroutines.launch\nimport androidx.lifecycle.lifecycleScope')

content = content.replace('CoroutineScope(Dispatchers.Main).launch', 'lifecycleScope.launch')
content = content.replace('CoroutineScope(Dispatchers.IO).launch', 'lifecycleScope.launch(Dispatchers.IO)')

with open('app/src/main/java/com/example/widget/LocationWeatherActivity.kt', 'w') as f:
    f.write(content)

print("Updated LocationWeatherActivity.kt")
