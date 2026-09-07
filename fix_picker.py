with open('app/src/main/java/com/example/DJFxRackScreen.kt', 'r') as f:
    text = f.read()

# Add imports
imports = """import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.provider.OpenableColumns
"""
text = text.replace('import androidx.compose.ui.text.font.FontWeight', imports + 'import androidx.compose.ui.text.font.FontWeight')

# Replace dummy logic
target_btn = """                    Button(onClick = {
                        // Dummy Add logic for now (would be file picker)
                        val prefs = context.getSharedPreferences("modular_fx", Context.MODE_PRIVATE)
                        val array = JSONArray(prefs.getString("plugins", "[]") ?: "[]")
                        val newObj = JSONObject()
                        val newId = "custom_${System.currentTimeMillis()}"
                        newObj.put("id", newId)
                        newObj.put("name", "Downloaded FX ${array.length() + 1}")
                        newObj.put("type", "delay") // random
                        array.put(newObj)
                        prefs.edit().putString("plugins", array.toString()).apply()
                        allPlugins = loadCustomEffects(context)
                    }, ) {"""

new_launcher_and_btn = """
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            var fileName = "Custom FX"
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx)
                }
            }
            fileName = fileName.substringBeforeLast(".")
            
            val prefs = context.getSharedPreferences("modular_fx", Context.MODE_PRIVATE)
            val array = JSONArray(prefs.getString("plugins", "[]") ?: "[]")
            val newObj = JSONObject()
            val newId = "custom_${System.currentTimeMillis()}"
            newObj.put("id", newId)
            newObj.put("name", fileName)
            newObj.put("type", "imported")
            array.put(newObj)
            prefs.edit().putString("plugins", array.toString()).apply()
            allPlugins = loadCustomEffects(context)
        }
    }

    if (showLibraryDialog) {"""

text = text.replace('    if (showLibraryDialog) {', new_launcher_and_btn)

new_btn = """                    Button(onClick = {
                        launcher.launch("*/*")
                    }, ) {"""
text = text.replace(target_btn, new_btn)

with open('app/src/main/java/com/example/DJFxRackScreen.kt', 'w') as f:
    f.write(text)
