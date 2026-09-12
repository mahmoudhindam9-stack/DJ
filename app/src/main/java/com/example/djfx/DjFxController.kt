package com.example.djfx

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DjFxController(private val context: Context) {
    private val repository = DjFxRepository(context)
    val audioEngine = DjFxAudioEngine(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var allFx by mutableStateOf<List<DjFxItem>>(emptyList())
        private set

    var padAssignments by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var currentBank by mutableStateOf("A")
        private set

    val banks = listOf("A", "B", "C", "D")

    val bankLabels = mapOf(
        "A" to "DJ FX",
        "B" to "شرقي",
        "C" to "كوميدي",
        "D" to "تريندات"
    )

    init {
        loadData()
    }

    private fun loadData() {
        scope.launch {
            // One-time cleanup: the factory sound bank used to be re-injected
            // into the library and onto pads on every launch. Remove it for
            // good so the DJ FX page only ever shows sounds the user added.
            val prefs = context.getSharedPreferences("dj_fx_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("factory_sounds_purged", false)) {
                repository.purgeFactorySounds()
                prefs.edit().putBoolean("factory_sounds_purged", true).apply()
            }
            // Always ensure newly added factory sounds are injected
            repository.injectMissingFactorySounds()

            // One-time: pre-fill each bank's pad grid with the factory sounds
            // that belong to it, so the pads aren't empty on first use even
            // though the sounds already exist in the library. Guarded by its
            // own flag (separate from the purge above) so it only ever runs
            // once and never re-fills a pad the user has cleared since.
            if (!prefs.getBoolean("default_pads_seeded", false)) {
                repository.seedDefaultPads(bankLabels)
                prefs.edit().putBoolean("default_pads_seeded", true).apply()
            }

            allFx = repository.getAllFx()
            padAssignments = repository.getPadAssignments()
        }
    }

    fun setBank(bank: String) {
        if (bank in banks) currentBank = bank
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
        allFx.find { it.id == fxId }?.let { fx ->
            audioEngine.play(fx.localUri ?: fx.sourceUrl)
        }
    }

    fun playPreview(uri: String) = audioEngine.play(uri)

    fun importFromUris(uris: List<android.net.Uri>) {
        scope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                runCatching {
                    val audio = com.example.utils.MusicScanner.parsePickedUri(context, uri)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val safeName = audio.title.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                        val file = java.io.File(
                            context.filesDir,
                            "djfx_${System.currentTimeMillis()}_$safeName.mp3"
                        )
                        file.outputStream().use { output -> input.copyTo(output) }
                        repository.insertFx(
                            DjFxItem(
                                id = "local_${System.nanoTime()}",
                                name = audio.title,
                                category = "Local",
                                source = "Device",
                                license = "User",
                                sourceUrl = uri.toString(),
                                localUri = file.absolutePath
                            )
                        )
                    }
                }
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
            repository.insertFx(fx.copy(isFavorite = !fx.isFavorite))
            allFx = repository.getAllFx()
        }
    }

    fun release() {
        scope.cancel()
        audioEngine.release()
    }
}
