package com.example.djfx

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Low-latency one-shot engine for the real factory and imported DJ FX samples. */
class DjFxAudioEngine(private val context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(24)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loaded = ConcurrentHashMap<String, Int>()
    private val loading = ConcurrentHashMap<String, MutableList<Float>>()
    private val pendingSampleKeys = ConcurrentHashMap<Int, String>()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            val key = pendingSampleKeys.remove(sampleId) ?: return@setOnLoadCompleteListener
            if (status != 0) {
                loaded.remove(key)
                loading.remove(key)
                Log.w(TAG, "Unable to load FX sample: $key (status=$status)")
                return@setOnLoadCompleteListener
            }
            loaded[key] = sampleId
            val volumes = loading.remove(key).orEmpty()
            volumes.forEach { volume -> soundPool.play(sampleId, volume, volume, 1, 0, 1f) }
        }
    }

    fun play(uri: String?) {
        if (uri.isNullOrBlank()) return
        val normalized = normalizeUri(uri)
        loaded[normalized]?.let { sampleId ->
            soundPool.play(sampleId, 1f, 1f, 1, 0, 1f)
            return
        }
        synchronized(loading) {
            loading[normalized]?.let { it += 1f; return }
            loading[normalized] = mutableListOf(1f)
        }
        scope.launch {
            runCatching {
                when {
                    normalized.startsWith("asset:///") -> loadAsset(normalized)
                    normalized.startsWith("file:///") -> loadFile(File(normalized.removePrefix("file://")), normalized)
                    normalized.startsWith("http://") || normalized.startsWith("https://") -> loadFile(downloadToCache(normalized), normalized)
                    else -> {
                        val file = File(normalized)
                        if (file.exists()) loadFile(file, normalized) else error("FX file does not exist: $normalized")
                    }
                }
            }.onFailure { error ->
                loading.remove(normalized)
                Log.w(TAG, "FX playback failed for $normalized", error)
            }
        }
    }

    private fun loadAsset(uri: String) {
        val assetPath = uri.removePrefix("asset:///")
        context.assets.openFd(assetPath).use { descriptor ->
            val sampleId = soundPool.load(descriptor, 1)
            pendingSampleKeys[sampleId] = uri
        }
    }

    private fun loadFile(file: File, key: String) {
        if (!file.exists() || file.length() < 128L) error("Missing/empty FX file: ${file.absolutePath}")
        val sampleId = soundPool.load(file.absolutePath, 1)
        pendingSampleKeys[sampleId] = key
    }

    private fun downloadToCache(uri: String): File {
        val extension = uri.substringBefore('?').substringAfterLast('.', "bin").take(5)
        val target = File(context.cacheDir, "djfx_${sha256(uri)}.$extension")
        if (target.exists() && target.length() >= 128L) return target
        val connection = (URL(uri).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val temporary = File(target.parentFile, "${target.name}.part")
            connection.inputStream.use { input -> FileOutputStream(temporary).use { output -> input.copyTo(output) } }
            if (temporary.length() < 128L) error("Downloaded FX sample is empty")
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            return target
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeUri(uri: String): String = when {
        uri.startsWith("asset:///") -> uri
        uri.startsWith("file:///") -> uri
        uri.startsWith("http://") || uri.startsWith("https://") -> uri
        File(uri).exists() -> "file://${File(uri).absolutePath}"
        else -> uri
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    fun release() {
        scope.cancel()
        soundPool.release()
        loaded.clear()
        loading.clear()
        pendingSampleKeys.clear()
    }

    private companion object { const val TAG = "DjFxAudioEngine" }
}
