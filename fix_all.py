import os
import re

# 1. Fix GitHubUpdater infinite loop
updater_path = 'app/src/main/java/com/example/updater/GitHubUpdater.kt'
with open(updater_path, 'r') as f:
    updater_code = f.read()

updater_replacement = """
                    val latestVersion = tagName.replace("v", "")
                    val currVer = currentVersion.replace("v", "")
                    val prefs = context.getSharedPreferences("updater_prefs", Context.MODE_PRIVATE)
                    val lastDownloaded = prefs.getString("last_downloaded_version", "") ?: ""
                    
                    val isNewer = latestVersion != currVer && latestVersion > currVer && latestVersion != lastDownloaded
"""
updater_code = updater_code.replace("""                    val latestVersion = tagName.replace("v", "")
                    val currVer = currentVersion.replace("v", "")
                    // Compare by length or alphabetically since dates will just be bigger
                    val isNewer = latestVersion != currVer && latestVersion > currVer""", updater_replacement)

download_func_replacement = """    private fun downloadAndInstallUpdate(context: Context, apkUrl: String, fileName: String, tagName: String) {
        val prefs = context.getSharedPreferences("updater_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("last_downloaded_version", tagName.replace("v", "")).apply()
"""
updater_code = updater_code.replace("private fun downloadAndInstallUpdate(context: Context, apkUrl: String, fileName: String) {", download_func_replacement)
updater_code = updater_code.replace('downloadAndInstallUpdate(context, apkUrl, "app-update-$tagName.apk")', 'downloadAndInstallUpdate(context, apkUrl, "app-update-$tagName.apk", tagName)')

with open(updater_path, 'w') as f:
    f.write(updater_code)

# 2. Fix Modular FX Rack layout
fx_rack_path = 'app/src/main/java/com/example/DJFxRackScreen.kt'
with open(fx_rack_path, 'r') as f:
    fx_rack = f.read()

# Change LazyVerticalGrid for rack to LazyRow
rack_grid_old = """                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    userScrollEnabled = true
                ) {"""
rack_grid_new = """                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = true
                ) {"""
fx_rack = fx_rack.replace(rack_grid_old, rack_grid_new)

# Adjust EffectTile to have a fixed width so they look like horizontal chips
fx_rack = fx_rack.replace('modifier = modifier.height(38.dp),', 'modifier = modifier.height(45.dp).width(110.dp),')

# Change Library Dialog Grid to Column
lib_grid_old = """                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(300.dp)
                    ) {"""
lib_grid_new = """                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(300.dp)
                    ) {"""
fx_rack = fx_rack.replace(lib_grid_old, lib_grid_new)
# Fix items to not have modifier.fillMaxWidth() in the LazyRow unless we want it? We removed it in tile anyway by hardcoding width.

with open(fx_rack_path, 'w') as f:
    f.write(fx_rack)

# 3. Add Auto-Crossfade back to MainPlayerExperience and AudioPlayerController
main_player_path = 'app/src/main/java/com/example/MainPlayerExperience.kt'
with open(main_player_path, 'r') as f:
    main_player = f.read()

crossfade_ui = """
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Crossfade Duration: ${playerController.crossfadeDurationMs / 1000}s", style = MaterialTheme.typography.labelSmall)
            Slider(value = playerController.crossfadeDurationMs.toFloat(), onValueChange = { playerController.crossfadeDurationMs = it.toLong() }, valueRange = 0f..10000f, steps = 9)
        }
"""
if "Crossfade Duration" not in main_player:
    main_player = main_player.replace("    }\n}\n", crossfade_ui + "    }\n}\n")
    with open(main_player_path, 'w') as f:
        f.write(main_player)

# 4. Modify AudioPlayerController to implement simple fading
audio_ctrl_path = 'app/src/main/java/com/example/player/AudioPlayerController.kt'
with open(audio_ctrl_path, 'r') as f:
    audio_ctrl = f.read()

progress_old = """    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying || durationMs == 0L) {
                    currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val dur = exoPlayer.duration
                    if (dur > 0L) durationMs = dur
                }
                delay(250)
            }
        }
    }"""
progress_new = """    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying || durationMs == 0L) {
                    currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val dur = exoPlayer.duration
                    if (dur > 0L) durationMs = dur
                    
                    // Simple Crossfade (Fade In/Out) Effect
                    if (crossfadeDurationMs > 0L && exoPlayer.isPlaying) {
                        val remaining = durationMs - currentPositionMs
                        if (remaining > 0 && remaining < crossfadeDurationMs) {
                            exoPlayer.volume = (remaining.toFloat() / crossfadeDurationMs.toFloat()).coerceIn(0f, 1f)
                        } else if (currentPositionMs < crossfadeDurationMs) {
                            exoPlayer.volume = (currentPositionMs.toFloat() / crossfadeDurationMs.toFloat()).coerceIn(0f, 1f)
                        } else {
                            exoPlayer.volume = 1f
                        }
                    } else if (exoPlayer.isPlaying) {
                        exoPlayer.volume = 1f
                    }
                }
                delay(100) // faster update for smoother fade
            }
        }
    }"""
audio_ctrl = audio_ctrl.replace(progress_old, progress_new)
if "var crossfadeDurationMs" not in audio_ctrl:
    audio_ctrl = audio_ctrl.replace("class AudioPlayerController(private val context: Context) {", "class AudioPlayerController(private val context: Context) {\n    var crossfadeDurationMs by androidx.compose.runtime.mutableLongStateOf(2000L)\n")
with open(audio_ctrl_path, 'w') as f:
    f.write(audio_ctrl)

# 5. Fix Widget Crash (Replace <View> with <FrameLayout> to be safe in RemoteViews)
widget_path = 'app/src/main/res/layout/time_weather_widget.xml'
with open(widget_path, 'r') as f:
    widget_xml = f.read()
widget_xml = widget_xml.replace('<View ', '<FrameLayout ')
widget_xml = widget_xml.replace('</View>', '</FrameLayout>')
with open(widget_path, 'w') as f:
    f.write(widget_xml)

