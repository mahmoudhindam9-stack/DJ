import re
content = open("app/src/main/java/com/example/radio/RadioScreen.kt").read()

validator_code = """
object StationValidator {
    val validStations = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
}

private suspend fun resolvePlaylistUrl(url: String): String = withContext(Dispatchers.IO) {
"""

content = content.replace("private suspend fun resolvePlaylistUrl(url: String): String = withContext(Dispatchers.IO) {", validator_code)

validation_logic = """
    var stations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var validating by remember { mutableStateOf(false) }
    
    // Trigger recomposition on state changes
"""

content = content.replace("    var stations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }\n    var loading by remember { mutableStateOf(false) }\n    var error by remember { mutableStateOf<String?>(null) }\n    var deckPicker by remember { mutableStateOf<RadioStation?>(null) }\n    var favorites by remember { mutableStateOf<Set<String>>(emptySet()) }\n    var loadingStationId by remember { mutableStateOf<String?>(null) }\n    var failedStationId by remember { mutableStateOf<String?>(null) }\n    val attempts = remember { mutableStateMapOf<String, Int>() }\n\n    // Trigger recomposition on state changes", """    var stations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var validating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deckPicker by remember { mutableStateOf<RadioStation?>(null) }
    var favorites by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loadingStationId by remember { mutableStateOf<String?>(null) }
    var failedStationId by remember { mutableStateOf<String?>(null) }
    val attempts = remember { mutableStateMapOf<String, Int>() }

    // Trigger recomposition on state changes""")

effect_logic = """
    LaunchedEffect(country, search, refreshToken) {
        loading = true
        error = null
        try {
            val rawStations = RadioBrowserRepository.stations(country.ifBlank { null }, search)
            loading = false
            validating = true
            val validList = mutableListOf<RadioStation>()
            
            withContext(Dispatchers.IO) {
                // Test concurrently with limited parallelism
                val parallelism = 10
                for (chunk in rawStations.chunked(parallelism)) {
                    chunk.map { station ->
                        kotlinx.coroutines.async {
                            if (StationValidator.validStations.containsKey(station.id)) {
                                if (StationValidator.validStations[station.id] == true) {
                                    synchronized(validList) { validList.add(station) }
                                }
                                return@async
                            }
                            
                            var isValid = false
                            for (urlStr in station.streamUrls) {
                                val resolvedUrl = resolvePlaylistUrl(urlStr)
                                try {
                                    val conn = URL(resolvedUrl).openConnection() as HttpURLConnection
                                    conn.connectTimeout = 5000
                                    conn.readTimeout = 5000
                                    conn.requestMethod = "GET"
                                    conn.setRequestProperty("User-Agent", "DJ-Music-Player/1.0")
                                    val code = conn.responseCode
                                    if (code in 200..299) {
                                        val contentType = conn.contentType?.lowercase() ?: ""
                                        if (contentType.contains("audio") || contentType.contains("mpeg") || contentType.contains("ogg") || contentType.contains("aac") || contentType.contains("flac") || contentType.contains("application") || contentType.contains("video/ogg")) {
                                            isValid = true
                                        }
                                    }
                                    conn.disconnect()
                                    if (isValid) break
                                } catch (e: Exception) {}
                            }
                            StationValidator.validStations[station.id] = isValid
                            if (isValid) {
                                synchronized(validList) { validList.add(station) }
                            }
                        }
                    }.forEach { it.await() }
                    
                    // Update UI incrementally
                    stations = validList.toList()
                }
            }
            stations = validList
        } catch (e: Exception) {
            error = e.localizedMessage ?: "فشل الاتصال بالخادم"
        } finally {
            loading = false
            validating = false
        }
    }
"""

content = content.replace("""    LaunchedEffect(country, search, refreshToken) {
        loading = true
        error = null
        try {
            stations = RadioBrowserRepository.stations(country.ifBlank { null }, search)
        } catch (e: Exception) {
            error = e.localizedMessage ?: "فشل الاتصال بالخادم"
        } finally {
            loading = false
        }
    }""", effect_logic)

progress_logic = """
        if (loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Spacer(Modifier.height(10.dp)) }
        if (validating) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Checking World Radio...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
        }
"""

content = content.replace("        if (loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Spacer(Modifier.height(10.dp)) }", progress_logic)

open("app/src/main/java/com/example/radio/RadioScreen.kt", "w").write(content)
