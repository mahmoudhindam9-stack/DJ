package com.example.onlinemusic

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OnlineMusicScreen(viewModel: OnlineMusicViewModel) {
    var openedUrl by remember { mutableStateOf<String?>(null) }
    if (openedUrl != null) {
        AlbumatyDetailWebView(openedUrl!!, onBack = { openedUrl = null })
        return
    }

    LaunchedEffect(Unit) { viewModel.loadHome() }
    var query by remember { mutableStateOf("") }

    val filteredAlbums = remember(query, viewModel.home.albums) {
        viewModel.home.albums.filter { it.title.contains(query.trim(), ignoreCase = true) }
    }
    val filteredSongs = remember(query, viewModel.home.songs) {
        viewModel.home.songs.filter { it.title.contains(query.trim(), ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.MusicNote, null, Modifier.size(28.dp))
            Spacer(Modifier.size(8.dp))
            Text("ألبوماتي", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.loadHome(true) }) {
                Icon(Icons.Filled.Refresh, "تحديث")
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("ابحث في ألبوماتي وأغانيه") }
        )

        if (viewModel.isLoading && viewModel.home.albums.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (viewModel.errorMessage != null && viewModel.home.albums.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(viewModel.errorMessage!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.loadHome(true) }) { Text("إعادة المحاولة") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    AlbumatySection("الأقسام") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(viewModel.home.categories) { item ->
                                AlbumatyChip(item.title) { openedUrl = item.url }
                            }
                        }
                    }
                }
                item {
                    AlbumatySection("جديد الألبومات") {
                        AlbumatyLinks(filteredAlbums) { openedUrl = it.url }
                    }
                }
                item {
                    AlbumatySection("جديد الأغاني") {
                        AlbumatyLinks(filteredSongs) { openedUrl = it.url }
                    }
                }
                item {
                    AlbumatySection("الفنانين") {
                        AlbumatyLinks(viewModel.home.artists) { openedUrl = it.url }
                    }
                }
                item {
                    Text(
                        "المحتوى يتم تحميله من ألبوماتي داخل التطبيق، وعند فتح أغنية أو ألبوم تظهر صفحته الأصلية داخل التطبيق.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumatySection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun AlbumatyLinks(items: List<AlbumatyLink>, onOpen: (AlbumatyLink) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(item) }) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(42.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.MusicNote, null) }
                    Spacer(Modifier.size(10.dp))
                    Text(item.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Filled.OpenInNew, null, Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun AlbumatyChip(title: String, onClick: () -> Unit) {
    Card(Modifier.clickable { onClick() }) {
        Text(title, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), maxLines = 1)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AlbumatyDetailWebView(url: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val webView = remember(url, context) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.loadsImagesAutomatically = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            loadUrl(url)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "رجوع") }
            Text("ألبوماتي", style = MaterialTheme.typography.titleMedium)
        }
        AndroidView(webView, modifier = Modifier.fillMaxSize())
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
}
