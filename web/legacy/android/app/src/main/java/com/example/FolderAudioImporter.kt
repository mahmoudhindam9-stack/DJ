package com.example

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.model.AudioItem
import com.example.utils.MusicScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FolderAudioImporter {
    private val supportedExtensions = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "3gp", "mid", "midi")

    suspend fun scan(context: Context, treeUri: Uri, maxFiles: Int = 5000): List<AudioItem> = withContext(Dispatchers.IO) {
        val result = ArrayList<AudioItem>()
        val resolver = context.contentResolver
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return@withContext result
        val rootChildren = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId)
        }.getOrNull() ?: return@withContext result

        fun walk(childrenUri: Uri) {
            if (result.size >= maxFiles) return
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} COLLATE NOCASE ASC"
            )?.use { cursor ->
                while (cursor.moveToNext() && result.size < maxFiles) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: ""
                    val mime = cursor.getString(2) ?: ""
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, id)
                        walk(childUri)
                    } else if (mime.startsWith("audio/") || isAudioName(name)) {
                        runCatching { result.add(MusicScanner.parsePickedUri(context, documentUri)) }
                    }
                }
            }
        }

        walk(rootChildren)
        result.distinctBy { it.id }
    }

    private fun isAudioName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in supportedExtensions
}
