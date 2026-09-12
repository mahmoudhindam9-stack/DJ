package com.example.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object GitHubUpdater {
    var githubOwner = "mahmoudhindam9-stack"
    var githubRepo = "DJ"

    data class SelectedAsset(
        val apkUrl: String,
        val fileName: String,
        val expectedSha256: String? = null
    )

    fun isVersionGreater(v1: String, v2: String): Boolean {
        val clean1 = v1.replace(Regex("[^0-9.]"), "")
        val clean2 = v2.replace(Regex("[^0-9.]"), "")
        val parts1 = clean1.split(".").mapNotNull { it.toLongOrNull() }
        val parts2 = clean2.split(".").mapNotNull { it.toLongOrNull() }
        val length = maxOf(parts1.size, parts2.size)
        for (i in 0 until length) {
            val p1 = parts1.getOrElse(i) { 0L }
            val p2 = parts2.getOrElse(i) { 0L }
            if (p1 > p2) return true
            if (p1 < p2) return false
        }
        return false
    }

    fun selectReleaseApk(json: JSONObject): SelectedAsset? {
        val assets = json.optJSONArray("assets") ?: return null
        var bestApkUrl: String? = null
        var bestApkName: String? = null
        var highestPriority = -1

        val checksumMap = mutableMapOf<String, String>()

        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "").trim()
            val url = asset.optString("browser_download_url", "").trim()
            if (name.isEmpty() || url.isEmpty()) continue

            if (name.endsWith(".sha256", ignoreCase = true)) {
                val baseName = name.substringBeforeLast(".sha256")
                checksumMap[baseName] = url
            }

            if (!name.endsWith(".apk", ignoreCase = true)) continue

            val lower = name.lowercase()
            // Strictly exclude unsigned, unaligned, test, debug or idsig artifacts
            if (lower.contains("unsigned") ||
                lower.contains("unaligned") ||
                lower.contains("test") ||
                lower.contains("debug") ||
                name.endsWith(".idsig", ignoreCase = true)
            ) {
                continue
            }

            val priority = when {
                name == "app-release.apk" -> 100
                lower.contains("release") && lower.contains("universal") -> 90
                lower.contains("release") -> 80
                else -> 10
            }

            if (priority > highestPriority) {
                highestPriority = priority
                bestApkUrl = url
                bestApkName = name
            }
        }

        if (bestApkUrl == null || bestApkName == null) return null

        val body = json.optString("body", "")
        val bodySha256 = extractSha256FromBody(body, bestApkName)

        return SelectedAsset(
            apkUrl = bestApkUrl,
            fileName = bestApkName,
            expectedSha256 = bodySha256
        )
    }

    fun extractSha256FromBody(body: String, fileName: String): String? {
        if (body.isEmpty()) return null
        val lines = body.lines()
        for (line in lines) {
            if (line.contains(fileName, ignoreCase = true)) {
                val match = Regex("([a-fA-F0-9]{64})").find(line)
                if (match != null) return match.value.lowercase()
            }
        }
        val generalMatch = Regex("(?:sha256|SHA256)[:\\s]+([a-fA-F0-9]{64})").find(body)
        return generalMatch?.groupValues?.get(1)?.lowercase()
    }

    fun computeFileSha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifyApkIntegrity(file: File, expectedSha256: String? = null): Boolean {
        if (!file.exists() || file.length() <= 0) return false
        if (!expectedSha256.isNullOrBlank()) {
            val actual = computeFileSha256(file)
            if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                return false
            }
        }
        return true
    }

    private fun getCertFingerprint(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun verifyApkSignature(context: Context, file: File): Boolean {
        try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }

            val archiveInfo = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
            // 1. Package name integrity
            if (archiveInfo.packageName != context.packageName) {
                return false
            }

            val currentInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, flags)
            }

            // 2. Version downgrade attack prevention
            val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.versionCode.toLong()
            }
            val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                currentInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                currentInfo.versionCode.toLong()
            }
            if (archiveVersionCode < currentVersionCode) {
                return false
            }

            // 3. Cryptographic SHA-256 certificate matching
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val archiveSigning = archiveInfo.signingInfo ?: return false
                val currentSigning = currentInfo.signingInfo ?: return false

                val archiveSigners = archiveSigning.apkContentsSigners
                val currentSigners = currentSigning.apkContentsSigners
                if (archiveSigners.isNullOrEmpty() || currentSigners.isNullOrEmpty()) {
                    return false
                }

                val archiveFps = archiveSigners.map { getCertFingerprint(it.toByteArray()) }.sorted()
                val currentFps = currentSigners.map { getCertFingerprint(it.toByteArray()) }.sorted()

                if (archiveFps == currentFps) return true

                // Check key rotation history if current key was rotated
                if (currentSigning.hasPastSigningCertificates()) {
                    val historySigners = currentSigning.signingCertificateHistory
                    if (historySigners != null) {
                        val historyFps = historySigners.map { getCertFingerprint(it.toByteArray()) }
                        if (archiveFps.all { it in historyFps }) return true
                    }
                }
                return false
            } else {
                @Suppress("DEPRECATION")
                val archiveSigs = archiveInfo.signatures
                @Suppress("DEPRECATION")
                val currentSigs = currentInfo.signatures
                if (archiveSigs.isNullOrEmpty() || currentSigs.isNullOrEmpty()) {
                    return false
                }

                val archiveFps = archiveSigs.map { getCertFingerprint(it.toByteArray()) }.sorted()
                val currentFps = currentSigs.map { getCertFingerprint(it.toByteArray()) }.sorted()

                return archiveFps == currentFps
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
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
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val tagName = json.optString("tag_name", "")

                    val latestVersion = tagName.replace("v", "", ignoreCase = true).trim()
                    val currVer = currentVersion.replace("v", "").trim()
                    val isNewer = latestVersion.isNotEmpty() && isVersionGreater(latestVersion, currVer)

                    val updaterPrefs = context.getSharedPreferences("updater_prefs", Context.MODE_PRIVATE)
                    val lastPromptTime = updaterPrefs.getLong("last_prompt_time", 0L)
                    val currentTime = System.currentTimeMillis()
                    val oneDayMs = 24 * 60 * 60 * 1000L
                    val shouldPrompt = showToast || (currentTime - lastPromptTime > oneDayMs)

                    if (isNewer && shouldPrompt) {
                        // Record prompt notification time only; NEVER mark version as installed here
                        updaterPrefs.edit { putLong("last_prompt_time", currentTime) }

                        val selected = selectReleaseApk(json)
                        if (selected != null) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "تحديث جديد متاح ($latestVersion)، جاري التحميل...", Toast.LENGTH_LONG).show()
                                downloadAndInstallUpdate(context, selected.apkUrl, "app-update-$latestVersion.apk", selected.expectedSha256)
                            }
                        } else if (showToast) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "لم يتم العثور على حزمة APK صالحة للإصدار $latestVersion", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else if (!isNewer && showToast) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "التطبيق محدث لأخر إصدار ($currVer)", Toast.LENGTH_SHORT).show()
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

    private fun downloadAndInstallUpdate(
        context: Context,
        apkUrl: String,
        fileName: String,
        expectedSha256: String? = null
    ) {
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadDir, fileName)

            // If an existing verified file already exists with identical signature and integrity, use it directly
            if (file.exists() && file.length() > 0) {
                if (verifyApkIntegrity(file, expectedSha256) && verifyApkSignature(context, file)) {
                    installApk(context, file)
                    return
                } else {
                    file.delete()
                }
            }

            val request = DownloadManager.Request(apkUrl.toUri())
                .setTitle("تحديث التطبيق")
                .setDescription("جاري تحميل التحديث الجديد...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager == null) {
                Toast.makeText(context, "خدمة التنزيل غير متوفرة في النظام", Toast.LENGTH_SHORT).show()
                return
            }
            val downloadId = downloadManager.enqueue(request)

            var receiver: BroadcastReceiver? = null
            receiver = object : BroadcastReceiver() {
                override fun onReceive(ctxt: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        try {
                            ctxt.unregisterReceiver(this)
                        } catch (_: Exception) {}

                        val query = DownloadManager.Query().setFilterById(downloadId)
                        val cursor = downloadManager.query(query)
                        if (cursor != null && cursor.moveToFirst()) {
                            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val status = if (statusIndex >= 0) cursor.getInt(statusIndex) else -1
                            cursor.close()

                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    if (verifyApkIntegrity(file, expectedSha256) && verifyApkSignature(context, file)) {
                                        // Hand over to OS package installer.
                                        // Do not mark as installed; OS installation may still be cancelled by user.
                                        installApk(context, file)
                                    } else {
                                        if (file.exists()) file.delete()
                                        Toast.makeText(context, "ملف التحديث تالف أو غير موثوق، تم حذفه لأسباب أمنية.", Toast.LENGTH_LONG).show()
                                    }
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    if (file.exists()) file.delete()
                                    Toast.makeText(context, "فشل تحميل التحديث، تم إلغاء العملية.", Toast.LENGTH_SHORT).show()
                                }
                                else -> {
                                    // Interrupted or paused
                                    if (file.exists()) file.delete()
                                }
                            }
                        } else {
                            cursor?.close()
                        }
                    }
                }
            }

            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "فشل بدء التحميل: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun installApk(context: Context, file: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(context, "الرجاء السماح بتثبيت التطبيقات المجهولة لإكمال التحديث", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = "package:${context.packageName}".toUri()
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return
                }
            }

            if (file.exists() && file.length() > 0) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "ملف التحديث غير موجود أو فارغ", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "فشل فتح التثبيت: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
