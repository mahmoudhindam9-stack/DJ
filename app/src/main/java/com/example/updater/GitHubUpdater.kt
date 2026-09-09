package com.example.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object GitHubUpdater {
    // ⚠️ قم بتغيير هذه القيم إلى بيانات حسابك على جيت هب
    var githubOwner = "mahmoudhindam9-stack" // اسم الحساب
    var githubRepo = "DJ"   // اسم المستودع

    // Helper to compare version strings (e.g. "1.0" vs "1.2026...")
    private fun isVersionGreater(v1: String, v2: String): Boolean {
        val parts1 = v1.split(".").mapNotNull { it.toLongOrNull() }
        val parts2 = v2.split(".").mapNotNull { it.toLongOrNull() }
        val length = maxOf(parts1.size, parts2.size)
        for (i in 0 until length) {
            val p1 = parts1.getOrElse(i) { 0L }
            val p2 = parts2.getOrElse(i) { 0L }
            if (p1 > p2) return true
            if (p1 < p2) return false
        }
        return false // equal
    }

    suspend fun checkForUpdates(context: Context, currentVersion: String = "1.0", showToast: Boolean = false) {
        if (githubOwner == "YOUR_GITHUB_USERNAME" || githubOwner.isEmpty()) {
            if (showToast) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "الرجاء إضافة اسم الحساب والمستودع في كود GitHubUpdater.kt", Toast.LENGTH_LONG).show()
                }
            }
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/$githubOwner/$githubRepo/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = 5000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val tagName = json.optString("tag_name", "")
                    
                    val latestVersion = tagName.replace("v", "", ignoreCase = true).trim()
                    val currVer = currentVersion.replace("v", "").trim()
                    
                    // Only prompt/download if latest is strictly greater than current
                    val isNewer = latestVersion.isNotEmpty() && isVersionGreater(latestVersion, currVer)

                    // Track which release we've already downloaded/prompted for so a
                    // silent background check (showToast = false, e.g. on every app
                    // launch) doesn't re-trigger the same download over and over when
                    // nothing new has actually been published since last time.
                    val updaterPrefs = context.getSharedPreferences("updater_prefs", Context.MODE_PRIVATE)
                    val alreadyHandledVersion = updaterPrefs.getString("last_downloaded_version", null)
                    val alreadyHandledThisRelease = alreadyHandledVersion == latestVersion

                    if (isNewer && !(alreadyHandledThisRelease && !showToast)) {
                        val assets = json.optJSONArray("assets")
                        if (assets != null && assets.length() > 0) {
                            var apkUrl = ""
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                if (asset.getString("name").endsWith(".apk")) {
                                    apkUrl = asset.getString("browser_download_url")
                                    break
                                }
                            }
                            
                            if (apkUrl.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "تحديث جديد متاح ($latestVersion)، جاري التحميل...", Toast.LENGTH_LONG).show()
                                    downloadAndInstallUpdate(context, apkUrl, "app-update-$latestVersion.apk", latestVersion)
                                }
                            }
                        }
                    } else if (!isNewer) {
                        if (showToast) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "التطبيق محدث لأخر إصدار ($currVer)", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else if (showToast) {
                     withContext(Dispatchers.Main) {
                         Toast.makeText(context, "لم يتم العثور على تحديثات في جيت هب", Toast.LENGTH_SHORT).show()
                     }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (showToast) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "خطأ في الاتصال: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    private fun downloadAndInstallUpdate(context: Context, apkUrl: String, fileName: String, version: String) {
        val prefs = context.getSharedPreferences("updater_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("last_downloaded_version", version).apply()
        try {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            // If already downloaded this exact version, just install it
            if (file.exists() && file.length() > 0) {
                installApk(context, fileName)
                return
            }

            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("تحديث التطبيق")
                .setDescription("جاري تحميل التحديث الجديد")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            val onComplete = object : BroadcastReceiver() {
                override fun onReceive(ctxt: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        installApk(context, fileName)
                        try {
                            context.unregisterReceiver(this)
                        } catch (e: Exception) {}
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "فشل بدء التحميل: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun installApk(context: Context, fileName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(context, "الرجاء السماح بتثبيت التطبيقات المجهولة لإكمال التحديث", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    intent.data = Uri.parse("package:${context.packageName}")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    return
                }
            }

            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "ملف التحديث غير موجود", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "فشل فتح التثبيت، يرجى التثبيت يدوياً من التنزيلات.", Toast.LENGTH_LONG).show()
        }
    }
}
