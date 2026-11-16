from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/example/MainActivity.kt"
NOTIFY = ROOT / "app/src/main/java/com/example/NotificationControlScreen.kt"

main = MAIN.read_text(encoding="utf-8")
marker = "// NOTIFICATION_PERMISSION_V1"
if marker not in main:
    anchor = "    val scope = rememberCoroutineScope()\n"
    if anchor not in main:
        raise SystemExit("MainApp coroutine scope anchor not found")
    insert = '''    // NOTIFICATION_PERMISSION_V1\n    val notificationPermissionLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {\n        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }\n    } else null\n\n    LaunchedEffect(Unit) {\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&\n            androidx.core.content.ContextCompat.checkSelfPermission(\n                context, Manifest.permission.POST_NOTIFICATIONS\n            ) != android.content.pm.PackageManager.PERMISSION_GRANTED\n        ) {\n            notificationPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)\n        }\n    }\n\n'''
    main = main.replace(anchor, anchor + insert, 1)

old = '                NotificationControlScreen(context = context)'
new = '                NotificationControlScreen(context = context)'
# The screen handles its own request launcher; keep navigation signature stable.
main = main.replace(old, new, 1)
MAIN.write_text(main, encoding="utf-8")

notify = NOTIFY.read_text(encoding="utf-8")
if marker not in notify:
    notify = notify.replace('import android.provider.Settings\n', 'import android.provider.Settings\nimport android.Manifest\nimport android.content.pm.PackageManager\nimport android.os.Build\nimport androidx.activity.compose.rememberLauncherForActivityResult\nimport androidx.activity.result.contract.ActivityResultContracts\nimport androidx.core.content.ContextCompat\n', 1)
    notify = notify.replace('import androidx.compose.runtime.setValue\n', 'import androidx.compose.runtime.setValue\nimport androidx.compose.runtime.LaunchedEffect\n', 1)
    anchor = '@Composable\nfun NotificationControlScreen(context: Context) {'
    replacement = '''@Composable\nfun NotificationControlScreen(context: Context) {\n    // NOTIFICATION_PERMISSION_V1\n    val notificationPermissionLauncher = rememberLauncherForActivityResult(\n        ActivityResultContracts.RequestPermission()\n    ) { }\n    val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||\n        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED\n'''
    if anchor not in notify:
        raise SystemExit("Notification screen anchor not found")
    notify = notify.replace(anchor, replacement, 1)
    button_anchor = '                Text("Android notification settings", style = MaterialTheme.typography.titleMedium)\n'
    button = '''                Text("Android notification settings", style = MaterialTheme.typography.titleMedium)\n                Text(\n                    if (notificationsAllowed) "Notifications are allowed" else "Notification permission is not granted",\n                    style = MaterialTheme.typography.bodySmall,\n                    color = MaterialTheme.colorScheme.onSurfaceVariant\n                )\n                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAllowed) {\n                    Button(\n                        onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },\n                        modifier = Modifier.fillMaxWidth()\n                    ) { Text("Allow notifications") }\n                }\n'''
    if button_anchor not in notify:
        raise SystemExit("Notification settings card anchor not found")
    notify = notify.replace(button_anchor, button, 1)
NOTIFY.write_text(notify, encoding="utf-8")
print("Notification permission request added")
