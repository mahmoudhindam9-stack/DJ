package com.example

import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.utils.MusicScanner
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.rotate
import android.Manifest

import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.*
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.layout.ContentScale
import com.example.model.*
import com.example.player.*
import kotlinx.coroutines.*

@Composable
fun EqualizerScreen(eqController: EqualizerController) {
    // SAFE_EQ_DOLBY_V3
    val context = LocalContext.current
    Column(
        modifier = Modifier
  .fillMaxSize()
  .padding(16.dp)
    ) {
        Row(
  modifier = Modifier.fillMaxWidth(),
  horizontalArrangement = Arrangement.SpaceBetween,
  verticalAlignment = Alignment.CenterVertically
        ) {
  Column {
      Text(
          text = "Detailed Equalizer",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold
      )
      Text(
          text = "10-Band Audio Frequency Processor",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
      )
  }
  Switch(
      checked = eqController.isEnabled,
      onCheckedChange = { eqController.toggleEnable() }
  )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dolby Atmos", fontWeight = FontWeight.Bold)
                    Text("Use the phone's hardware/vendor audio processing without stacking aggressive EQ.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = {
                    val packages = listOf("com.dolby.daxappui2", "com.dolby.daxappui")
                    val intent = packages.asSequence().mapNotNull { pkg -> context.packageManager.getLaunchIntentForPackage(pkg) }.firstOrNull()
                    if (intent != null) context.startActivity(intent)
                    else Toast.makeText(context, "Dolby Atmos is not available on this device", Toast.LENGTH_SHORT).show()
                }) { Text("Open Dolby") }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("EQ Presets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
  items(eqController.presets) { preset ->
      FilterChip(
          selected = eqController.selectedPreset == preset,
          onClick = { eqController.applyPreset(preset) },
          label = { Text(preset) }
      )
  }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Card(
  modifier = Modifier
      .fillMaxWidth()
      .weight(1f),
  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
  Column(
      modifier = Modifier
          .fillMaxSize()
          .padding(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
  ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
      ) {
          Text(
              text = "10-BAND EQ (dB GAIN)",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
          )
          Text(
              text = if (eqController.isEnabled) "ACTIVE" else "BYPASSED (High Quality)",
              style = MaterialTheme.typography.labelSmall,
              color = if (eqController.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
          )
      }

      Spacer(modifier = Modifier.height(12.dp))

      Row(
          modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
      ) {
          eqController.bands.forEachIndexed { index, band ->
              Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier
                      .weight(1f)
                      .fillMaxHeight(),
                  verticalArrangement = Arrangement.SpaceBetween
              ) {
                  Text(
                      text = if (band.currentLevelDb > 0) "+${band.currentLevelDb}" else "${band.currentLevelDb}",
                      style = MaterialTheme.typography.labelSmall,
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold
                  )

                  VerticalFader(
                      value = band.currentLevelDb.toFloat(),
                      onValueChange = { newVal ->
                          eqController.updateBandLevel(index, newVal.toInt())
                      },
                      modifier = Modifier
                          .weight(1f)
                          .fillMaxHeight()
                  )

                  Text(
                      text = band.name,
                      style = androidx.compose.ui.text.TextStyle(fontSize = 8.sp),
                      fontWeight = FontWeight.Bold,
                      maxLines = 1
                  )
              }
          }
      }
  }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
  Column(modifier = Modifier.padding(16.dp)) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
      ) {
          Text("BASS BOOST", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
          Text(
              "${(eqController.bassBoostLevel * 100).toInt()}%",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
          )
      }
      Slider(
          value = eqController.bassBoostLevel,
          onValueChange = { eqController.updateBassBoost(it) },
          valueRange = 0f..1f,
          modifier = Modifier.fillMaxWidth()
      )
  }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
  Column(modifier = Modifier.padding(16.dp)) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
      ) {
          Text("TREBLE BOOST", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
          Text(
              "${(eqController.trebleBoostLevel * 100).toInt()}%",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
          )
      }
      Slider(
          value = eqController.trebleBoostLevel,
          onValueChange = { eqController.updateTrebleBoost(it) },
          valueRange = 0f..1f,
          modifier = Modifier.fillMaxWidth()
      )
  }
        }
    }
}

@Composable
fun VerticalFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestValue by rememberUpdatedState(value)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(44.dp)
            .pointerInput(Unit) {
                var workingValue = latestValue
                detectVerticalDragGestures(
                    onDragStart = {
                        workingValue = latestValue
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val height = size.height.toFloat()
                        if (height > 0f) {
                            val delta = -dragAmount / height * 12f
                            workingValue = (workingValue + delta).coerceIn(-6f, 6f)
                            onValueChange(workingValue)
                        }
                    },
                    onDragEnd = {},
                    onDragCancel = {}
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val height = size.height.toFloat()
                    if (height > 0f) {
                        val fraction = 1f - (offset.y / height)
                        val newVal = (-6f + fraction * 12f).coerceIn(-12f, 12f)
                        onValueChange(newVal)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight(0.85f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(3.dp))
        )
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        )
        val fraction = ((latestValue + 6f) / 12f).coerceIn(0f, 1f)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxHeight(0.85f)
                .width(44.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            val trackH = maxHeight
            val thumbY = trackH * fraction - 14.dp
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .offset(y = -thumbY)
                    .shadow(4.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
                )
            }
        }
    }
}