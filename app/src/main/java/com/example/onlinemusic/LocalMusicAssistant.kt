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
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Runs Qwen3-0.6B completely on-device through Google's LiteRT-LM runtime.
 * The model is downloaded only after the user explicitly taps the download button.
 */
class LocalMusicAssistant(context: Context) : AutoCloseable {
    companion object {
        private const val MODEL_URL =
            "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm"
        private const val MODEL_BYTES = 344_686_592L
        private const val MODEL_SHA256 = "96491bb0f78fa68098bf8f1d3628194c97041aff7894f8cc29303b3acdb877a9"
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
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.MINUTES)
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
        val valid = withContext(Dispatchers.IO) { modelFile.isFile && isValidModelFile(modelFile) }
        if (!valid) {
            modelFile.delete()
            return false
        }
        ensureDeviceMemory()
        initializeEngine(modelFile)
        try {
            verifyInference()
        } catch (error: Throwable) {
            close()
            throw error
        }
        return isReady
    }

    suspend fun downloadAndInitialize(onProgress: suspend (Float) -> Unit) {
        if (isReady) return
        ensureDeviceMemory()
        val downloaded = downloadModel(onProgress)
        initializeEngine(downloaded)
        try {
            verifyInference()
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    private fun ensureDeviceMemory() {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        val minimumRam = 4L * 1024L * 1024L * 1024L
        val minimumAvailable = 2_500L * 1024L * 1024L
        check(info.totalMem >= minimumRam && info.availMem >= minimumAvailable) {
            "تشغيل Qwen3 يحتاج 4 جيجابايت RAM على الأقل ونحو 2.5 جيجابايت ذاكرة متاحة حاليًا. أغلق التطبيقات الثقيلة ثم حاول مجددًا."
        }
    }

    private fun ensureStorageAvailable() {
        modelDirectory.mkdirs()
        val stats = android.os.StatFs(modelDirectory.absolutePath)
        val requiredBytes = MODEL_BYTES + 96L * 1024L * 1024L
        check(stats.availableBytes >= requiredBytes) {
            "يلزم توفير مساحة فارغة لا تقل عن 425 ميجابايت لتنزيل نموذج Qwen3 والتحقق منه."
        }
    }

    private suspend fun verifyInference() {
        val reply = generate("Reply with exactly READY and nothing else.", 12)
        check(reply.isNotBlank()) { "تم تحميل النموذج لكن اختبار الاستدلال لم يُرجع أي نتيجة." }
    }

    private suspend fun downloadModel(onProgress: suspend (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            if (modelFile.isFile && isValidModelFile(modelFile)) {
                withContext(Dispatchers.Main.immediate) { onProgress(1f) }
                return@withContext modelFile
            }
            ensureStorageAvailable()
            modelDirectory.mkdirs()
            var resumeOffset = partialFile.takeIf { it.isFile }?.length() ?: 0L
            if (resumeOffset < 0L || resumeOffset >= MODEL_BYTES) {
                partialFile.delete()
                resumeOffset = 0L
            }

            val requestBuilder = Request.Builder()
                .url(MODEL_URL)
                .header("User-Agent", "DJ-OnDevice-Music-Assistant")
                .header("Accept", "application/octet-stream")
            if (resumeOffset > 0L) requestBuilder.header("Range", "bytes=$resumeOffset-")

            client.newCall(requestBuilder.build()).execute().use { response ->
                check(response.isSuccessful) { "فشل تنزيل النموذج: HTTP " + response.code }
                val append = resumeOffset > 0L && response.code == 206
                if (resumeOffset > 0L && response.code == 200) {
                    partialFile.delete()
                    resumeOffset = 0L
                }
                if (append) {
                    val contentRange = response.header("Content-Range").orEmpty()
                    check(
                        contentRange.startsWith("bytes $resumeOffset-") &&
                            contentRange.substringAfterLast('/').toLongOrNull() == MODEL_BYTES
                    ) { "استجابة استكمال التنزيل غير مطابقة؛ أعد المحاولة." }
                } else {
                    check(response.code == 200) { "تعذر استكمال تنزيل Qwen3: HTTP " + response.code }
                }

                val body = response.body ?: error("ملف النموذج فارغ")
                val expectedBodyBytes = MODEL_BYTES - resumeOffset
                val totalBytes = body.contentLength()
                check(totalBytes <= 0L || totalBytes == expectedBodyBytes) {
                    "حجم ملف Qwen3 غير مطابق للنسخة المعتمدة؛ أوقفنا التنزيل."
                }

                val job = currentCoroutineContext()
                var downloadedBytes = resumeOffset
                var lastReportedBytes = resumeOffset
                val digest = MessageDigest.getInstance("SHA-256")
                if (append && partialFile.isFile) {
                    partialFile.inputStream().buffered().use { input ->
                        val existingBuffer = ByteArray(256 * 1024)
                        while (true) {
                            val count = input.read(existingBuffer)
                            if (count < 0) break
                            digest.update(existingBuffer, 0, count)
                        }
                    }
                }
                FileOutputStream(partialFile, append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            job.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            downloadedBytes += count
                            if (downloadedBytes - lastReportedBytes >= 1024L * 1024L || downloadedBytes >= MODEL_BYTES) {
                                lastReportedBytes = downloadedBytes
                                val progress = (downloadedBytes.toFloat() / MODEL_BYTES).coerceIn(0f, 0.999f)
                                withContext(Dispatchers.Main.immediate) { onProgress(progress) }
                            }
                        }
                    }
                    output.flush()
                    output.fd.sync()
                }
                check(downloadedBytes == MODEL_BYTES) {
                    "انقطع تنزيل النموذج قبل اكتماله. اضغط تحميل مرة أخرى لاستكماله من حيث توقف."
                }
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                check(actualHash == MODEL_SHA256) {
                    partialFile.delete()
                    "فشل التحقق من بصمة Qwen3 الرسمية؛ حُذف الملف غير السليم تلقائيًا."
                }
            }

            if (modelFile.exists()) modelFile.delete()
            if (!partialFile.renameTo(modelFile)) {
                partialFile.copyTo(modelFile, overwrite = true)
                partialFile.delete()
            }
            check(modelFile.isFile && isValidModelFile(modelFile)) {
                modelFile.delete()
                "تعذر التحقق من ملف النموذج بعد حفظه في مجلد التطبيق."
            }
            withContext(Dispatchers.Main.immediate) { onProgress(1f) }
            modelFile
        }

    private fun isValidModelFile(file: File): Boolean {
        if (!file.isFile || file.length() != MODEL_BYTES) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == MODEL_SHA256
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
