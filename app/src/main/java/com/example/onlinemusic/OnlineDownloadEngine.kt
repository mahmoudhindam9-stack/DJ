package com.example.onlinemusic

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Single hardened downloader for all online music providers.
 * Preserves already-percent-encoded URLs, follows redirects, sends browser-like
 * headers, retries transient HTTP failures, verifies bytes, and optionally
 * reports byte progress to the caller.
 */
object OnlineDownloadEngine {
    private const val USER_AGENT = "Mozilla/5.0 (Android 14; Mobile) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
    private const val DEFAULT_REFERER = "https://www.albumaty.com/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun downloadToUri(
        rawUrl: String,
        resolver: ContentResolver,
        destination: Uri,
        referer: String? = null,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null
    ): Long = withContext(Dispatchers.IO) {
        val url = safeHttpUrl(rawUrl)
        var lastError: Throwable? = null

        repeat(3) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "audio/mpeg,audio/*;q=0.95,*/*;q=0.8")
                    .header("Accept-Language", "ar,en-US;q=0.9,en;q=0.8")
                    .header("Referer", referer?.takeIf { it.isNotBlank() } ?: DEFAULT_REFERER)
                    .header("Connection", "keep-alive")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val code = response.code
                        if (code == 408 || code == 429 || code in 500..599) {
                            throw TransientDownloadException("HTTP $code")
                        }
                        error("فشل تنزيل الملف: HTTP $code")
                    }

                    val body = response.body ?: error("ملف الصوت فارغ")
                    val contentLength = body.contentLength()
                    var total = 0L
                    var lastReported = 0L
                    resolver.openOutputStream(destination, "w")?.use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (count > 0) {
                                    output.write(buffer, 0, count)
                                    total += count
                                    if (onProgress != null && (total - lastReported >= 256 * 1024 || (contentLength > 0 && total >= contentLength))) {
                                        onProgress(total, contentLength)
                                        lastReported = total
                                    }
                                }
                            }
                            output.flush()
                        }
                    } ?: error("تعذر فتح مكان الحفظ")

                    if (total <= 0L) error("تم تنزيل ملف صوتي فارغ")
                    if (contentLength > 0L && total != contentLength) {
                        error("اكتمل التنزيل بشكل غير صحيح ($total/$contentLength bytes)")
                    }
                    onProgress?.invoke(total, contentLength)
                    return@withContext total
                }
            } catch (t: Throwable) {
                lastError = t
                if (t !is TransientDownloadException || attempt == 2) throw t
                delay(350L * (attempt + 1))
            }
        }

        throw lastError ?: IllegalStateException("فشل تنزيل الملف")
    }

    private fun safeHttpUrl(raw: String): String {
        val value = raw.replace("&amp;", "&").trim()
        require(value.startsWith("http://", true) || value.startsWith("https://", true)) {
            "رابط تنزيل غير صالح"
        }
        return runCatching { URI(value).toASCIIString() }.getOrElse {
            value.replace(" ", "%20")
        }
    }

    private class TransientDownloadException(message: String) : Exception(message)
}
