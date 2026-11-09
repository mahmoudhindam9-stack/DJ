package com.example.onlinemusic

import android.app.ActivityManager
import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Runs Qwen3-0.6B completely on-device through Google's LiteRT-LM runtime.
 * The model is downloaded only after the user explicitly taps the download button.
 */
class LocalMusicAssistant(context: Context) : AutoCloseable {
    companion object {
        private const val MODEL_URL =
            "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm"
        private const val MIN_MODEL_BYTES = 300L * 1024L * 1024L
        private const val SYSTEM_INSTRUCTION = """
You are the private, on-device music discovery assistant inside the DJ Android app.
Understand Arabic (including Egyptian colloquial Arabic) and English music requests.
You do not have access to the internet or audio waveforms. You only receive song titles and search results.
Never invent song titles, artists, IDs, URLs, or availability. Search queries are suggestions only.
For search-query tasks, return ONLY valid JSON: {"queries":["short realistic music search phrase", "..."]}.
Create at most four concise search phrases. Preserve requested language, country, genre, era, artist, and mood.
For ranking tasks, return ONLY valid JSON: {"ids":[0,2,1]}.
Rank only the supplied numeric candidate IDs. Do not create IDs. Return the most relevant IDs first.
Do not explain your reasoning. Do not use Markdown fences or extra text.
"""
    }

    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()
    private val inferenceMutex = Mutex()
    private val modelDirectory = File(appContext.filesDir, "on_device_ai")
    private val modelFile = File(modelDirectory, "qwen3-0.6b.litertlm")
    private val partialFile = File(modelDirectory, "qwen3-0.6b.litertlm.part")
    @Volatile private var engine: Engine? = null

    val isReady: Boolean
        get() = engine != null

    suspend fun initializeIfDownloaded(): Boolean {
        if (isReady) return true
        if (!modelFile.isFile || modelFile.length() < MIN_MODEL_BYTES) return false
        ensureDeviceMemory()
        initializeEngine(modelFile)
        return isReady
    }

    suspend fun downloadAndInitialize(onProgress: suspend (Float) -> Unit) {
        if (isReady) return
        ensureDeviceMemory()
        val downloaded = downloadModel(onProgress)
        initializeEngine(downloaded)
    }

    private fun ensureDeviceMemory() {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        val minimumRam = 4L * 1024L * 1024L * 1024L
        check(info.totalMem >= minimumRam) {
            "يحتاج تشغيل نموذج Qwen3 إلى هاتف بذاكرة RAM فعلية لا تقل عن 4 جيجابايت. سيظل مشغل الموسيقى يعمل بصورة طبيعية."
        }
    }

    private suspend fun downloadModel(onProgress: suspend (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            if (modelFile.isFile && modelFile.length() >= MIN_MODEL_BYTES) {
                withContext(Dispatchers.Main.immediate) { onProgress(1f) }
                return@withContext modelFile
            }
            modelDirectory.mkdirs()
            partialFile.delete()

            val request = Request.Builder()
                .url(MODEL_URL)
                .header("User-Agent", "DJ-OnDevice-Music-Assistant")
                .header("Accept", "application/octet-stream")
                .build()

            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "فشل تنزيل النموذج: HTTP " + response.code }
                val body = response.body ?: error("ملف النموذج فارغ")
                val totalBytes = body.contentLength()
                check(totalBytes <= 0L || totalBytes >= MIN_MODEL_BYTES) {
                    "حجم ملف النموذج غير متوقع؛ أوقفنا التنزيل لحماية مساحة الهاتف."
                }
                val job = currentCoroutineContext()
                var downloadedBytes = 0L
                var lastReportedBytes = 0L
                FileOutputStream(partialFile).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            job.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloadedBytes += count
                            if (totalBytes > 0L &&
                                (downloadedBytes - lastReportedBytes >= 1024L * 1024L || downloadedBytes >= totalBytes)
                            ) {
                                lastReportedBytes = downloadedBytes
                                val progress = (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                                withContext(Dispatchers.Main.immediate) { onProgress(progress) }
                            }
                        }
                    }
                    output.flush()
                }
            }

            check(partialFile.isFile && partialFile.length() >= MIN_MODEL_BYTES) {
                partialFile.delete()
                "اكتمل تنزيل ملف أصغر من المتوقع؛ لم يتم اعتماد الملف."
            }
            if (modelFile.exists()) modelFile.delete()
            if (!partialFile.renameTo(modelFile)) {
                partialFile.copyTo(modelFile, overwrite = true)
                partialFile.delete()
            }
            check(modelFile.isFile && modelFile.length() >= MIN_MODEL_BYTES) {
                "تعذر حفظ ملف النموذج كاملًا."
            }
            withContext(Dispatchers.Main.immediate) { onProgress(1f) }
            modelFile
        }

    private suspend fun initializeEngine(file: File) {
        if (isReady) return
        withContext(Dispatchers.IO) {
            val cache = File(appContext.cacheDir, "litert_lm_cache").apply { mkdirs() }
            val newEngine = Engine(
                EngineConfig(
                    modelPath = file.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = cache.absolutePath
                )
            )
            try {
                newEngine.initialize()
                engine = newEngine
            } catch (error: Throwable) {
                runCatching { newEngine.close() }
                throw error
            }
        }
    }

    suspend fun createSearchQueries(request: String, seedTerms: List<String>): List<String> {
        val prompt = buildString {
            append("Create music search queries for this user request: ")
            append(request)
            if (seedTerms.isNotEmpty()) {
                append("\nUseful seed phrases: ")
                append(seedTerms.joinToString(" | "))
            }
            append("\nReturn JSON with a queries array only.")
        }
        val json = parseJson(generate(prompt, 100))
        val values = json?.optJSONArray("queries") ?: JSONArray()
        return (0 until values.length())
            .mapNotNull { values.optString(it).trim().takeIf(String::isNotBlank) }
            .distinct()
            .take(4)
            .ifEmpty { listOf(request) }
    }

    suspend fun rankTracks(request: String, candidates: List<AlbumatyLink>): List<AlbumatyLink> {
        if (candidates.isEmpty()) return emptyList()
        val limited = candidates.take(40)
        val catalog = limited.mapIndexed { index, track ->
            index.toString() + " | " + track.title.take(140)
        }.joinToString("\n")
        val prompt = buildString {
            append("User music request or mood: ")
            append(request)
            append("\nRank the following real search-result candidates by relevance. ")
            append("Return up to 30 IDs in best-first order, using only the IDs shown.\n")
            append(catalog)
            append("\nReturn JSON only: {\"ids\":[0,1]}")
        }
        val json = parseJson(generate(prompt, 100))
        val ids = json?.optJSONArray("ids") ?: JSONArray()
        val ordered = (0 until ids.length())
            .mapNotNull { index ->
                val candidateId = ids.optInt(index, -1)
                limited.getOrNull(candidateId)
            }
            .distinctBy { it.url }
        return if (ordered.isNotEmpty()) ordered else limited
    }

    private suspend fun generate(prompt: String, maxTokens: Int): String = inferenceMutex.withLock {
        withContext(Dispatchers.IO) {
            val activeEngine = engine ?: error("نموذج Qwen3 غير جاهز")
            val config = ConversationConfig(
                systemInstruction = Contents.of(SYSTEM_INSTRUCTION),
                samplerConfig = SamplerConfig(topK = 12, topP = 0.85, temperature = 0.15),
                thinkingConfig = ThinkingConfig(enableThinking = false),
                maxOutputToken = maxTokens
            )
            activeEngine.createConversation(config).use { conversation ->
                conversation.sendMessage(
                    prompt,
                    maxOutputToken = maxTokens,
                    thinkingConfig = ThinkingConfig(enableThinking = false)
                ).toString().trim()
            }
        }
    }

    private fun parseJson(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull()
    }

    override fun close() {
        runCatching { engine?.close() }
        engine = null
    }
}
