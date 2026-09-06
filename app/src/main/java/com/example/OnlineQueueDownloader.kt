package com.example

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.model.AudioItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.Locale

object OnlineQueueDownloader {
    data class Result(val downloaded: Int, val skipped: Int, val failed: Int)

    suspend fun download(context: Context, treeUri: Uri, songs: List<AudioItem>): Result = withContext(Dispatchers.IO) {
        var downloaded = 0
        var skipped = 0
        var failed = 0
        val resolver = context.contentResolver

        songs.forEach { song ->
            val source = song.uri.toString()
            if (!(source.startsWith("http://") || source.startsWith("https://"))) {
                skipped++
                return@forEach
            }
            try {
                val fileName = fileNameFor(song)
                val existing = findChildByName(resolver, treeUri, fileName)
                if (existing != null) {
                    runCatching { DocumentsContract.deleteDocument(resolver, existing) }
                }
                val mime = URLConnection.guessContentTypeFromName(fileName) ?: "audio/mpeg"
                val documentUri = DocumentsContract.createDocument(
                    resolver,
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)),
                    mime,
                    fileName
                ) ?: error("Unable to create $fileName")

                val connection = (URL(source).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                }
                try {
                    if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                    resolver.openOutputStream(documentUri, "w").use { output ->
                        requireNotNull(output) { "Unable to open $fileName" }
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                            output.flush()
                        }
                    }
                    downloaded++
                } finally {
                    connection.disconnect()
                }
            } catch (_: Throwable) {
                failed++
            }
        }
        Result(downloaded, skipped, failed)
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
                val id = cursor.getString(0)
                return DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
            }
        }
        return null
    }

    private fun fileNameFor(song: AudioItem): String {
        val raw = song.title.trim().ifBlank { "Unknown Track" }
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Unknown Track" }
        val lower = cleaned.lowercase(Locale.ROOT)
        return if (EXTENSIONS.any { lower.endsWith(it) }) cleaned else "$cleaned.mp3"
    }

    private val EXTENSIONS = setOf(".mp3", ".m4a", ".aac", ".wav", ".ogg", ".flac", ".opus", ".webm")
}
