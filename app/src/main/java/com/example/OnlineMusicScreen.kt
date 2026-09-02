package com.example

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val ALBUMATY_URL = "https://www.albumaty.com/cat/1.html"

/**
 * Online music discovery screen backed by Albumaty's public web experience.
 *
 * This intentionally does not scrape or bypass protected media URLs. Playback
 * and download actions remain those exposed by the source website itself.
 */
@Composable
fun OnlineMusicScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var loading by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.loadsImagesAutomatically = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            loading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            loading = false
                        }

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url.toString()
                            return if (url.startsWith("https://www.albumaty.com/") ||
                                url.startsWith("http://www.albumaty.com/")) {
                                false
                            } else {
                                false
                            }
                        }
                    }

                    setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                        try {
                            val request = DownloadManager.Request(Uri.parse(url))
                                .setMimeType(mimeType ?: "audio/mpeg")
                                .setTitle("Albumaty music")
                                .setDescription("Downloading music from the source website")
                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "DJ-Music")
                                .addRequestHeader("User-Agent", userAgent ?: "")

                            val cookie = CookieManager.getInstance().getCookie(url)
                            if (!cookie.isNullOrBlank()) {
                                request.addRequestHeader("Cookie", cookie)
                            }

                            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                            manager.enqueue(request)
                            Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to start download", Toast.LENGTH_SHORT).show()
                        }
                    })

                    loadUrl(ALBUMATY_URL)
                }
            },
            update = { webView ->
                if (webView.url == null) webView.loadUrl(ALBUMATY_URL)
            }
        )

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
