package com.example

import android.app.AlertDialog
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.example.model.AudioItem
import com.example.onlinemusic.OnlineDownloadEngine
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLConnection
import java.util.Locale
import kotlin.coroutines.resume

object OnlineQueueDownloader {
    data class Result(val downloaded: Int, val skipped: Int, val failed: Int)

    suspend fun download(context: Context, treeUri: Uri, songs: List<AudioItem>): Result {
        val onlineSongs = songs.filter { isHttpSource(it.uri.toString()) }
        if (onlineSongs.isEmpty()) return Result(0, songs.size, 0)

        val selected = selectSongs(context, onlineSongs) ?: return Result(0, 0, 0)
        if (selected.isEmpty()) return Result(0, 0, 0)

        return withContext(Dispatchers.IO) {
            var downloaded = 0
            var skipped = songs.count { !isHttpSource(it.uri.toString()) }
            var failed = 0
            val resolver = context.contentResolver
            val progress = ProgressUi(context, selected.size)

            try {
                progress.show()
                selected.forEachIndexed { index, song ->
                    var documentUri: Uri? = null
                    try {
                        val fileName = fileNameFor(song)
                        progress.startSong(index, song.title)
                        findChildByName(resolver, treeUri, fileName)?.let { existing ->
                            runCatching { DocumentsContract.deleteDocument(resolver, existing) }
                        }

                        val mime = URLConnection.guessContentTypeFromName(fileName) ?: "audio/mpeg"
                        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
                            ?: error("Invalid destination folder")
                        documentUri = DocumentsContract.createDocument(
                            resolver,
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId),
                            mime,
                            fileName
                        ) ?: error("Unable to create $fileName")

                        val referer = refererFor(song.uri.toString())
                        OnlineDownloadEngine.downloadToUri(
                            rawUrl = song.uri.toString(),
                            resolver = resolver,
                            destination = documentUri,
                            referer = referer,
                            onProgress = { bytes, total -> progress.updateBytes(bytes, total) }
                        )
                        downloaded++
                        progress.finishSong(index, true)
                    } catch (e: Exception) {
                        documentUri?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
                        android.util.Log.w("OnlineQueueDownloader", "Download failed for ${song.title}", e)
                        failed++
                        progress.finishSong(index, false)
                    }
                }
            } finally {
                progress.dismiss()
            }
            Result(downloaded, skipped, failed)
        }
    }

    private suspend fun selectSongs(context: Context, songs: List<AudioItem>): List<AudioItem>? =
        suspendCancellableCoroutine { continuation ->
            val checked = BooleanArray(songs.size) { true }
            val labels = songs.map { it.title }.toTypedArray()

            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 8, 24, 4)
            }
            val selectAll = CheckBox(context).apply {
                text = "Select all (${songs.size})"
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
                    (list.getChildAt(i) as CheckBox).setOnCheckedChangeListener(null)
                    (list.getChildAt(i) as CheckBox).isChecked = value
                    (list.getChildAt(i) as CheckBox).setOnCheckedChangeListener { _, checkedValue -> checked[i] = checkedValue }
                }
            }

            val dialog = AlertDialog.Builder(context)
                .setTitle("Download from Queue")
                .setMessage("Select the songs you want to download")
                .setView(box)
                .setNegativeButton("Cancel") { _, _ ->
                    if (continuation.isActive) continuation.resume(null)
                }
                .setPositiveButton("Download selected", null)
                .create()

            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val result = songs.filterIndexed { index, _ -> checked[index] }
                    if (result.isEmpty()) {
                        dialog.setTitle("Download from Queue")
                        dialog.setMessage("Select at least one song")
                        return@setOnClickListener
                    }
                    if (continuation.isActive) continuation.resume(result)
                    dialog.dismiss()
                }
            }
            dialog.setOnDismissListener {
                if (continuation.isActive) continuation.resume(null)
            }
            continuation.invokeOnCancellation { dialog.dismiss() }
            dialog.show()
        }

    private class ProgressUi(private val context: Context, private val totalSongs: Int) {
        private var dialog: AlertDialog? = null
        private var titleView: TextView? = null
        private var detailView: TextView? = null
        private var bar: ProgressBar? = null
        private var currentIndex = 0

        fun show() {
            runOnMain {
                val layout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(28, 12, 28, 20)
                }
                titleView = TextView(context).apply { textSize = 17f; gravity = Gravity.START }
                detailView = TextView(context).apply { textSize = 13f }
                bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
                bar?.max = 100
                layout.addView(titleView)
                layout.addView(detailView)
                layout.addView(bar)
                dialog = AlertDialog.Builder(context)
                    .setTitle("Downloading Queue")
                    .setView(layout)
                    .setCancelable(false)
                    .create()
                dialog?.show()
            }
        }

        fun startSong(index: Int, title: String) {
            currentIndex = index
            runOnMain {
                titleView?.text = "${index + 1}/$totalSongs  $title"
                detailView?.text = "Preparing download..."
                bar?.progress = 0
            }
        }

        fun updateBytes(bytes: Long, total: Long) {
            runOnMain {
                val percent = if (total > 0) ((bytes * 100L) / total).toInt().coerceIn(0, 100) else 0
                bar?.progress = percent
                detailView?.text = if (total > 0) {
                    "Song ${currentIndex + 1}/$totalSongs • $percent%"
                } else {
                    "Song ${currentIndex + 1}/$totalSongs • Downloading"
                }
            }
        }

        fun finishSong(index: Int, success: Boolean) {
            runOnMain {
                titleView?.text = "${index + 1}/$totalSongs"
                detailView?.text = if (success) "Completed" else "Failed"
                bar?.progress = if (success) 100 else 0
            }
        }

        fun dismiss() = runOnMain { dialog?.dismiss() }

        private fun runOnMain(block: () -> Unit) {
            android.os.Handler(android.os.Looper.getMainLooper()).post(block)
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

    private val EXTENSIONS = setOf(".mp3", ".m4a", ".aac", ".wav", ".ogg", ".flac", ".opus", ".webm")
}
