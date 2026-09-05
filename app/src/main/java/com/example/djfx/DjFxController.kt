package com.example.djfx

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DjFxController(private val context: Context) {
    private val repository = DjFxRepository(context)
    val audioEngine = DjFxAudioEngine(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    var allFx by mutableStateOf<List<DjFxItem>>(emptyList())
        private set

    var padAssignments by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var currentBank by mutableStateOf("A")
        private set

    val banks = listOf("A", "B", "C", "D")

    init {
        loadData()
    }

    private fun loadData() {
        scope.launch {
            allFx = repository.getAllFx()
            padAssignments = repository.getPadAssignments()
        }
    }

    fun setBank(bank: String) {
        if (bank in banks) {
            currentBank = bank
        }
    }

    fun assignFxToPad(bank: String, index: Int, fxId: String) {
        scope.launch {
            repository.assignPad(bank, index, fxId)
            padAssignments = padAssignments + ("${bank}_$index" to fxId)
        }
    }

    fun removeFxFromPad(bank: String, index: Int) {
        scope.launch {
            repository.clearPad(bank, index)
            padAssignments = padAssignments - "${bank}_$index"
        }
    }

    fun playFx(fxId: String) {
        val fx = allFx.find { it.id == fxId }
        val uri = fx?.localUri ?: fx?.sourceUrl
        if (uri != null) {
            audioEngine.play(uri)
        }
    }

    fun playPreview(uri: String) {
        audioEngine.play(uri)
    }

    fun importFromUris(uris: List<android.net.Uri>) {
        scope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                try {
                    val audio = com.example.utils.MusicScanner.parsePickedUri(context, uri)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val file = java.io.File(context.filesDir, "djfx_${System.currentTimeMillis()}_${audio.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.mp3")
                        val outputStream = java.io.FileOutputStream(file)
                        inputStream.copyTo(outputStream)
                        inputStream.close()
                        outputStream.close()
                        
                        val newFx = DjFxItem(
                            id = "local_${System.currentTimeMillis()}",
                            name = audio.title,
                            category = "Local",
                            source = "Device",
                            license = "User",
                            sourceUrl = uri.toString(),
                            localUri = file.absolutePath
                        )
                        repository.insertFx(newFx)
                    }
                } catch(e: Exception) { e.printStackTrace() }
            }
            val updated = repository.getAllFx()
            kotlinx.coroutines.withContext(Dispatchers.Main) { allFx = updated }
        }
    }

    fun addImportedFx(item: DjFxItem) {
        scope.launch {
            repository.insertFx(item)
            allFx = repository.getAllFx()
        }
    }

    fun toggleFavorite(fxId: String) {
        scope.launch {
            val fx = allFx.find { it.id == fxId } ?: return@launch
            val updated = fx.copy(isFavorite = !fx.isFavorite)
            repository.insertFx(updated)
            allFx = repository.getAllFx()
        }
    }

    fun release() {
        audioEngine.release()
    }
}
