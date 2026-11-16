package com.example.djfx

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DjFxBoard(controller: DjFxController) {
    val isArabic by com.example.ui.locale.AppLanguageManager.isArabicFlow.collectAsState()
    var showBrowser by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isArabic) "لوحة السامبلر والمؤثرات" else "DJ FX / SAMPLER",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Button(
                onClick = { showBrowser = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Filled.LibraryMusic, contentDescription = if (isArabic) "تصفح المؤثرات" else "Browse FX", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (isArabic) "المكتبة" else "Library", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    controller.banks.forEach { bank ->
                        val isSelected = controller.currentBank == bank
                        Surface(
                            onClick = { controller.setBank(bank) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    controller.bankLabels[bank] ?: (if (isArabic) "بنك $bank" else "BANK $bank"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(280.dp)
                ) {
                    items(16) { index ->
                        val padKey = "${controller.currentBank}_$index"
                        val fxId = controller.padAssignments[padKey]
                        val fx = controller.allFx.find { it.id == fxId }

                        Surface(
                            onClick = {
                                if (fx != null) {
                                    controller.playFx(fx.id)
                                } else {
                                    showBrowser = true
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (fx != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            contentColor = if (fx != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            tonalElevation = if (fx != null) 4.dp else 1.dp,
                            modifier = Modifier.aspectRatio(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize().padding(4.dp)
                            ) {
                                if (fx != null) {
                                    Text(
                                        text = fx.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(
                                        onClick = { controller.removeFxFromPad(controller.currentBank, index) },
                                        modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = if (isArabic) "إزالة" else "Remove", modifier = Modifier.size(12.dp))
                                    }
                                } else {
                                    Icon(Icons.Filled.Add, contentDescription = if (isArabic) "إضافة مؤثر" else "Add FX", modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBrowser) {
        DjFxBrowserDialog(controller) { showBrowser = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DjFxBrowserDialog(controller: DjFxController, onDismiss: () -> Unit) {
    val isArabic by com.example.ui.locale.AppLanguageManager.isArabicFlow.collectAsState()
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Favorites") + controller.allFx.map { it.category }.distinct().filter { it.isNotBlank() }

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        controller.importFromUris(uris)
    }

    var selectedPadBank by remember { mutableStateOf(controller.currentBank) }
    var selectedPadIndex by remember { mutableStateOf(0) }
    var showPadSelector by remember { mutableStateOf<DjFxItem?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxSize().padding(16.dp),
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isArabic) "مكتبة المؤثرات الصوتية" else "FX Library", fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = if (isArabic) "إغلاق" else "Close") }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                Button(
                    onClick = { pickerLauncher.launch(arrayOf("audio/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = if (isArabic) "استيراد ملفات محلية" else "Import Local Files")
                    Spacer(Modifier.width(8.dp))
                    Text(if (isArabic) "استيراد ملفات محلية" else "Import Local Files")
                }

                Spacer(Modifier.height(12.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    lazyItems(categories) { cat ->
                        val catLabel = when (cat) {
                            "All" -> if (isArabic) "الكل" else "All"
                            "Favorites" -> if (isArabic) "المفضلة" else "Favorites"
                            else -> cat
                        }
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(catLabel) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                val displayed = controller.allFx.filter {
                    if (selectedCategory == "All") true
                    else if (selectedCategory == "Favorites") it.isFavorite
                    else it.category == selectedCategory
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    lazyItems(displayed) { fx ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(fx.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${fx.category} • ${fx.source}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { controller.toggleFavorite(fx.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            if (fx.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                            contentDescription = if (isArabic) "مفضلة" else "Favorite",
                                            tint = if (fx.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { controller.playPreview(fx.localUri ?: fx.sourceUrl) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = if (isArabic) "معاينة" else "Preview")
                                    }
                                    Button(
                                        onClick = { showPadSelector = fx },
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text(if (isArabic) "تعيين" else "Assign")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )

    if (showPadSelector != null) {
        AlertDialog(
            onDismissRequest = { showPadSelector = null },
            title = { Text(if (isArabic) "تعيين للزر (Pad)" else "Assign to Pad") },
            text = {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        controller.banks.forEach { b ->
                            FilterChip(
                                selected = selectedPadBank == b,
                                onClick = { selectedPadBank = b },
                                label = { Text(controller.bankLabels[b] ?: (if (isArabic) "بنك $b" else "Bank $b")) }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(16) { idx ->
                            val pKey = "${selectedPadBank}_$idx"
                            val hasFx = controller.padAssignments.containsKey(pKey)
                            Surface(
                                onClick = { selectedPadIndex = idx },
                                color = if (selectedPadIndex == idx) MaterialTheme.colorScheme.primary else if (hasFx) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.aspectRatio(1f)
                            ) {
                                Box(contentAlignment = Alignment.Center) { Text((idx + 1).toString(), fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showPadSelector?.id?.let { fxId ->
                        controller.assignFxToPad(selectedPadBank, selectedPadIndex, fxId)
                    }
                    showPadSelector = null
                    onDismiss()
                }) {
                    val bankLabel = controller.bankLabels[selectedPadBank] ?: (if (isArabic) "بنك $selectedPadBank" else "Bank $selectedPadBank")
                    Text(if (isArabic) "تعيين إلى $bankLabel رقم ${selectedPadIndex + 1}" else "Assign to $bankLabel ${selectedPadIndex + 1}")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPadSelector = null }) { Text(if (isArabic) "إلغاء" else "Cancel") }
            }
        )
    }
}
