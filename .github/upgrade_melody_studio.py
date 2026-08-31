from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
MELODY = ROOT / "app/src/main/java/com/example/MelodyStudio.kt"
CONTROL = ROOT / "app/src/main/java/com/example/NotificationControlScreen.kt"
MARK = "// EXTERNAL_CONTROLS_V1"

CONTROL_CONTENT = r'''package com.example

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NotificationControlScreen(context: Context) {
    val prefs = remember { context.getSharedPreferences("external_controls", Context.MODE_PRIVATE) }
    var controlsEnabled by remember { mutableStateOf(prefs.getBoolean("enabled", true)) }
    var lockScreenEnabled by remember { mutableStateOf(prefs.getBoolean("lockscreen", true)) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Controls & Notifications", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Control music without opening the app using the notification, lock screen, Bluetooth media buttons and the home-screen widget.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("External playback controls", style = MaterialTheme.typography.titleMedium)
                        Text("Play / Pause / Previous / Next from outside DJ", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = controlsEnabled,
                        onCheckedChange = {
                            controlsEnabled = it
                            prefs.edit().putBoolean("enabled", it).apply()
                        }
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Lock-screen controls", style = MaterialTheme.typography.titleMedium)
                        Text("Keep playback controls visible on the lock screen", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = lockScreenEnabled,
                        onCheckedChange = {
                            lockScreenEnabled = it
                            prefs.edit().putBoolean("lockscreen", it).apply()
                        }
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Android notification settings", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = {
                        val intent = if (android.os.Build.VERSION.SDK_INT >= 26) {
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                putExtra(Settings.EXTRA_CHANNEL_ID, com.example.player.MusicService.CHANNEL_ID)
                            }
                        } else {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open notification settings") }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Home-screen widget", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Add the DJ Music widget to your home screen for instant playback control outside the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
'''


def patch_main():
    text = MAIN.read_text(encoding="utf-8")
    changed = False

    # Fix the previous build blocker: MicScreen receives Context as a parameter,
    # so it must not redeclare LocalContext with the same name.
    duplicate = 'fun MicScreen(micController: MicController, audioLibrary: SnapshotStateList<AudioItem>, context: Context, scope: kotlinx.coroutines.CoroutineScope) {\n    val context = LocalContext.current\n'
    fixed = 'fun MicScreen(micController: MicController, audioLibrary: SnapshotStateList<AudioItem>, context: Context, scope: kotlinx.coroutines.CoroutineScope) {\n'
    if duplicate in text:
        text = text.replace(duplicate, fixed, 1)
        changed = True

    if MARK not in text:
        nav_anchor = '''                NavigationBarItem(\n                    icon = { Icon(Icons.Filled.Mic, contentDescription = "Mic/Karaoke") },'''
        control_item = '''                NavigationBarItem(\n                    icon = { Icon(Icons.Filled.NotificationsActive, contentDescription = "Controls") },\n                    label = { Text("Controls") },\n                    selected = currentDestination?.route == "controls",\n                    onClick = {\n                        navController.navigate("controls") {\n                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }\n                            launchSingleTop = true\n                            restoreState = true\n                        }\n                    }\n                )\n'''
        if nav_anchor in text:
            text = text.replace(nav_anchor, control_item + nav_anchor, 1)
            changed = True

        route_anchor = '''            composable("mic") {\n                MicScreen(micController = micController, audioLibrary = audioLibrary, context = context, scope = scope)\n            }\n'''
        route_insert = route_anchor + '''            composable("controls") {\n                NotificationControlScreen(context = context)\n            }\n'''
        if route_anchor in text:
            text = text.replace(route_anchor, route_insert, 1)
            changed = True

        text = text.replace('fun NotificationControlScreen(context: Context)', 'fun NotificationControlScreen(context: Context)', 1)
        if CONTROL.exists() or True:
            pass

    MAIN.write_text(text, encoding="utf-8")
    return changed


def patch_melody():
    text = MELODY.read_text(encoding="utf-8")
    changed = False
    old_signature = 'fun MelodyStudioCard(audioLibrary: MutableList<AudioItem>, context: Context, djMixerController: DJMixerController)'
    if old_signature in text:
        text = text.replace(old_signature, 'fun MelodyStudioCard(audioLibrary: MutableList<AudioItem>, context: Context)', 1)
        text = text.replace('import com.example.player.DJMixerController\n', '', 1)
        changed = True

    replacements = {
        '"ألّف لحن بالنقر على النغمات ثم شغّله أو احفظه كأغنية جديدة داخل المكتبة."': '"Compose a melody by tapping notes, then play it or save it as a new song in your library."',
        '"اللحن فارغ"': '"Melody is empty"',
        '"اللحن: ${sequence.joinToString(" – ") { it.name }}"': '"Melody: ${sequence.joinToString(" – ") { it.name }}"',
        '"تشغيل"': '"Play"',
        '"عزف متكرر"': '"Loop"',
        '"مسح"': '"Clear"',
        '"حفظ اللحن في مكتبة الأغاني"': '"Save to Music Library"',
        '"تم حفظ ${item.title}"': '"Saved ${item.title}"',
    }
    for old, new in replacements.items():
        if old in text:
            text = text.replace(old, new)
            changed = True

    MELODY.write_text(text, encoding="utf-8")
    return changed


def patch_service_and_widget():
    service = ROOT / "app/src/main/java/com/example/player/MusicService.kt"
    widget = ROOT / "app/src/main/java/com/example/widget/MusicWidgetProvider.kt"
    changed = False

    s = service.read_text(encoding="utf-8")
    s2 = s.replace('currentSong?.title ?: "مشغل الموسيقى"', 'currentSong?.title ?: "Music Player"')\
           .replace('currentSong?.artist ?: "موسيقى"', 'currentSong?.artist ?: "Music"')
    if s2 != s:
        service.write_text(s2, encoding="utf-8")
        changed = True

    w = widget.read_text(encoding="utf-8")
    w2 = w.replace('"مشغل الموسيقى", "اختر أغنية للتشغيل"', '"Music Player", "Choose a song to play"')
    if w2 != w:
        widget.write_text(w2, encoding="utf-8")
        changed = True
    return changed


if __name__ == "__main__":
    changed = patch_main()
    changed = patch_melody() or changed
    changed = patch_service_and_widget() or changed
    CONTROL.write_text(CONTROL_CONTENT, encoding="utf-8")
    print("Melody Studio and external controls upgrade applied" if changed else "External controls already up to date")
