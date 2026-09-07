with open('app/src/main/java/com/example/updater/GitHubUpdater.kt', 'r') as f:
    text = f.read()

target = """                    val latestVersion = tagName.replace("v", "")
                    val currVer = currentVersion.replace("v", "")
                    val prefs = context.getSharedPreferences("updater_prefs", Context.MODE_PRIVATE)
                    val lastDownloaded = prefs.getString("last_downloaded_version", "") ?: ""
                    
                    val isNewer = latestVersion != currVer && latestVersion > currVer && latestVersion != lastDownloaded"""

replacement = """                    val latestVersion = tagName.replace("v", "").trim()
                    val currVer = currentVersion.replace("v", "").trim()
                    val prefs = context.getSharedPreferences("updater_prefs", Context.MODE_PRIVATE)
                    val lastDownloaded = prefs.getString("last_downloaded_version", "") ?: ""
                    
                    // We only download if the remote tag is different from what we last downloaded,
                    // AND it's not the exact same as our hardcoded version.
                    val isNewer = latestVersion.isNotEmpty() && latestVersion != currVer && latestVersion != lastDownloaded"""

text = text.replace(target, replacement)

with open('app/src/main/java/com/example/updater/GitHubUpdater.kt', 'w') as f:
    f.write(text)
