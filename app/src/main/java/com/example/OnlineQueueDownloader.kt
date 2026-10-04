package com.example

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.model.AudioItem
import com.example.onlinemusic.OnlineDownloadEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import java.net.URLConnection
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Downloads online songs from the active queue completely in the background
 * without freezing or blocking the user interface.
 */
object OnlineQueueDownloader {
    data class Result(val downloaded: Int, val skipped: Int, val failed: Int)

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadStatus = MutableStateFlow<String?>(null)
    val downloadStatus: StateFlow<String?> = _downloadStatus.asStateFlow()

    private const val NOTIFICATION_CHANNEL_ID = "queue_downloads"
    private const val NOTIFICATION_ID = 5055

    fun startBackgroundDownload(context: Context, treeUri: Uri, songs: List<AudioItem>) {
        val appContext = context.applicationContext
        val onlineSongs = songs.filter { isHttpSource(it.uri.toString()) }
        if (onlineSongs.isEmpty()) {
            showToast(appContext, "لا توجد ملفات قابلة للتنزيل في قائمة الانتظار")
            return
        }

        if (_isDownloading.value) {
            showToast(appContext, "يوجد تنزيل جاري بالفعل في الخلفية")
            return
        }

        // Show song selection dialog on UI thread
        selectSongs(context, onlineSongs) { selected ->
            if (selected.isNullOrEmpty()) return@selectSongs

            showToast(appContext, "بدء تنزيل ${selected.size} مقاطع في الخلفية...")
            _isDownloading.value = true
            _downloadStatus.value = "جاري التنزيل في الخلفية (0/${selected.size})..."

            activeJob?.cancel()
            activeJob = downloadScope.launch {
                val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                createNotificationChannel(notificationManager)

                var downloaded = 0
                val skipped = songs.count { !isHttpSource(it.uri.toString()) }
                var failed = 0
                val resolver = appContext.contentResolver

                val total = selected.size
                try {
                    selected.forEachIndexed { index, song ->
                        if (!isActive) return@forEachIndexed
                        var documentUri: Uri? = null
                        val songTitle = song.title.ifBlank { "Track ${index + 1}" }

                        _downloadStatus.value = "تنزيل (${index + 1}/$total): $songTitle"
                        updateProgressNotification(
                            appContext,
                            notificationManager,
                            title = "تنزيل قائمة الانتظار ($total/$index)",
                            text = songTitle,
                            progress = 0,
                            max = 100,
                            ongoing = true
                        )

                        try {
                            val fileName = fileNameFor(song)
                            findChildByName(resolver, treeUri, fileName)?.let { existing ->
                                runCatching { DocumentsContract.deleteDocument(resolver, existing) }
                            }

                            val mime = URLConnection.guessContentTypeFromName(fileName) ?: "audio/mpeg"
                            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
                                ?: error("مجلد الحفظ غير صالح")
                            documentUri = DocumentsContract.createDocument(
                                resolver,
                                DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId),
                                mime,
                                fileName
                            ) ?: error("تعذر إنشاء الملف $fileName")

                            val referer = refererFor(song.uri.toString())
                            OnlineDownloadEngine.downloadToUri(
                                rawUrl = song.uri.toString(),
                                resolver = resolver,
                                destination = documentUri,
                                referer = referer,
                                onProgress = { bytesWritten, totalBytes ->
                                    val percent = if (totalBytes > 0) ((bytesWritten * 100L) / totalBytes).toInt().coerceIn(0, 100) else 0
                                    updateProgressNotification(
                                        appContext,
                                        notificationManager,
                                        title = "تنزيل (${index + 1}/$total): $songTitle",
                                        text = "$percent%",
                                        progress = percent,
                                        max = 100,
                                        ongoing = true
                                    )
                                }
                            )
                            downloaded++
                        } catch (e: Exception) {
                            documentUri?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
                            android.util.Log.w("OnlineQueueDownloader", "Download failed for ${song.title}", e)
                            failed++
                        }
                    }

                    val completionMessage = "اكتمل التنزيل: تم تنزيل $downloaded، وفشل $failed"
                    _downloadStatus.value = completionMessage
                    showToast(appContext, completionMessage)

                    updateProgressNotification(
                        appContext,
                        notificationManager,
                        title = "اكتمل تنزيل قائمة الانتظار",
                        text = "تم تنزيل $downloaded مقطع بنجاح",
                        progress = 100,
                        max = 100,
                        ongoing = false
                    )
                } catch (t: Throwable) {
                    if (t !is CancellationException) {
                        val errMsg = "فشل تنزيل القائمة: ${t.message}"
                        _downloadStatus.value = errMsg
                        showToast(appContext, errMsg)
                    }
                } finally {
                    _isDownloading.value = false
                }
            }
        }
    }

    suspend fun download(context: Context, treeUri: Uri, songs: List<AudioItem>): Result {
        startBackgroundDownload(context, treeUri, songs)
        return Result(0, 0, 0)
    }

    private fun selectSongs(context: Context, songs: List<AudioItem>, onResult: (List<AudioItem>?) -> Unit) {
        val checked = BooleanArray(songs.size) { true }

        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 8)
        }
        val selectAll = CheckBox(context).apply {
            text = "تحديد الكل (${songs.size})"
            isChecked = true
        }
        box.addView(selectAll)

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        songs.forEachIndexed { index, song ->
            val check = CheckBox(context).apply {
                text = song.title
                isChecked = true
                setOnCheckedChangeListener { _, value -> checked[index] = value }
            }
            list.addView(check)
        }
        box.addView(list)

        selectAll.setOnCheckedChangeListener { _, value ->
            songs.forEachIndexed { index, _ -> checked[index] = value }
            for (i in 0 until list.childCount) {
                val cb = list.getChildAt(i) as CheckBox
                cb.setOnCheckedChangeListener(null)
                cb.isChecked = value
                cb.setOnCheckedChangeListener { _, checkedValue -> checked[i] = checkedValue }
            }
        }

        var handled = false
        val dialog = AlertDialog.Builder(context)
            .setTitle("تنزيل من قائمة الانتظار")
            .setMessage("اختر المقاطع المراد تنزيلها في الخلفية:")
            .setView(box)
            .setNegativeButton("إلغاء") { _, _ ->
                if (!handled) { handled = true; onResult(null) }
            }
            .setPositiveButton("تنزيل في الخلفية", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val result = songs.filterIndexed { index, _ -> checked[index] }
                if (result.isEmpty()) {
                    showToast(context, "الرجاء اختيار مقطع واحد على الأقل")
                    return@setOnClickListener
                }
                if (!handled) {
                    handled = true
                    onResult(result)
                }
                dialog.dismiss()
            }
        }

        dialog.setOnDismissListener {
            if (!handled) {
                handled = true
                onResult(null)
            }
        }

        dialog.show()
    }

    private fun createNotificationChannel(notificationManager: NotificationManager?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "تنزيلات قائمة الانتظار",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعارات تنزيل ملفات الصوت في الخلفية"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateProgressNotification(
        context: Context,
        notificationManager: NotificationManager?,
        title: String,
        text: String,
        progress: Int,
        max: Int,
        ongoing: Boolean
    ) {
        if (notificationManager == null) return
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)

        if (ongoing && max > 0) {
            builder.setProgress(max, progress, false)
        } else if (!ongoing) {
            builder.setProgress(0, 0, false)
            builder.setAutoCancel(true)
        }

        runCatching {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun findChildByName(resolver: ContentResolver, treeUri: Uri, name: String): Uri? {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri) ?: return null
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        resolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} = ?",
            arrayOf(name),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
            }
        }
        return null
    }

    private fun isHttpSource(source: String): Boolean =
        source.startsWith("http://", true) || source.startsWith("https://", true)

    private fun refererFor(rawUrl: String): String? = runCatching {
        val host = URI(rawUrl).host?.lowercase(Locale.ROOT).orEmpty()
        when {
            host.contains("audius") -> "https://audius.co/"
            host.contains("albumaty") -> "https://www.albumaty.com/"
            else -> null
        }
    }.getOrNull()

    private fun fileNameFor(song: AudioItem): String {
        val raw = song.title.trim().ifBlank { "Unknown Track" }
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Unknown Track" }
        val lower = cleaned.lowercase(Locale.ROOT)
        return if (EXTENSIONS.any { lower.endsWith(it) }) cleaned else "$cleaned.mp3"
    }

    private fun showToast(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    private val EXTENSIONS = setOf(".mp3", ".m4a", ".aac", ".wav", ".ogg", ".flac", ".opus", ".webm")
}
