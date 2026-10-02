package com.example

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.model.AudioItem
import com.example.onlinemusic.OnlineDownloadEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            if (!(source.startsWith("http://", true) || source.startsWith("https://", true))) {
                skipped++
                return@forEach
            }

            var documentUri: Uri? = null
            try {
                val fileName = fileNameFor(song)
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

                OnlineDownloadEngine.downloadToUri(
                    rawUrl = source,
                    resolver = resolver,
                    destination = documentUri,
                    referer = "https://www.albumaty.com/"
                )
                downloaded++
            } catch (e: Exception) {
                documentUri?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
                android.util.Log.w("OnlineQueueDownloader", "Download failed for ${song.title}", e)
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
                return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
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
