package com.example.cloud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun DjCloudSyncScreen(
    manager: DjCloudSyncManager,
    onBack: () -> Unit
) {
    val user by manager.user.collectAsState()
    val devices by manager.devices.collectAsState()
    val playback by manager.playback.collectAsState()
    val status by manager.status.collectAsState()
    val managerError by manager.error.collectAsState()
    val notice by manager.notice.collectAsState()

    val scope = rememberCoroutineScope()
    var signUpMode by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var localNotice by remember { mutableStateOf<String?>(null) }

    val activeDeviceId = playback?.activeDeviceId
    val isThisDeviceOwner = activeDeviceId == manager.deviceId
    val activeDeviceName = devices.firstOrNull { it.id == activeDeviceId }?.name

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Icon(Icons.Filled.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("DJ Cloud", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("One account • Android and Web", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusIndicator(status)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (user == null) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            if (signUpMode) "Create your DJ account" else "Sign in to DJ Cloud",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Use the same email and password on your Android phone and DJ Web. Your local music files remain on each device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (signUpMode) {
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Display name") },
                                singleLine = true
                            )
                        }
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Email") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )

                        (localError ?: managerError)?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    localError = null
                                    try {
                                        if (signUpMode) manager.signUp(displayName, email, password)
                                        else manager.signIn(email, password)
                                        password = ""
                                        localNotice = "DJ Cloud connected."
                                    } catch (failure: Exception) {
                                        localError = failure.message ?: "Could not connect to DJ Cloud."
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy && email.contains("@") && password.length >= 8,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                if (busy) "Please wait…" else if (signUpMode) "Create account" else "Sign in",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(
                            onClick = {
                                signUpMode = !signUpMode
                                localError = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (signUpMode) "Already have an account? Sign in" else "New to DJ Cloud? Create an account")
                        }
                    }
                }

                InfoCard(
                    title = "What syncs",
                    body = "Your playlists, track details, favorites, device list, and playback handoff are shared. Local MP3 files are not uploaded. To continue a local track on another phone, that same file must also exist on the other phone."
                )
            } else {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Signed in", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(user?.email.orEmpty(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("This phone: " + android.os.Build.MODEL, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        localError = null
                                        try {
                                            manager.refresh()
                                            localNotice = "Devices refreshed."
                                        } catch (failure: Exception) {
                                            localError = failure.message
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                enabled = !busy
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Refresh")
                            }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        manager.signOut()
                                        localNotice = "Signed out."
                                    }
                                },
                                enabled = !busy
                            ) {
                                Icon(Icons.Filled.Logout, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Sign out")
                            }
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Your devices", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.weight(1f))
                            Text(devices.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        }
                        if (devices.isEmpty()) {
                            Text("No devices registered yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        } else {
                            devices.forEach { device ->
                                DeviceRow(
                                    device = device,
                                    isCurrent = device.id == manager.deviceId,
                                    isOutput = device.id == activeDeviceId
                                )
                            }
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Playback output", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        when {
                            activeDeviceId == null -> Text("No device owns playback yet.", style = MaterialTheme.typography.bodySmall)
                            isThisDeviceOwner -> Text("This phone currently owns the audio output.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            else -> Text("Audio is controlled by " + (activeDeviceName ?: "another device") + ".", style = MaterialTheme.typography.bodySmall)
                        }
                        if (!isThisDeviceOwner) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        localError = null
                                        localNotice = null
                                        try {
                                            manager.takeControl()
                                        } catch (failure: Exception) {
                                            localError = failure.message ?: "Could not move playback."
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (busy) "Switching…" else "Move playback to this phone", fontWeight = FontWeight.Bold)
                            }
                        }
                        if (playback?.currentTrack != null) {
                            Text(
                                playback?.currentTrack?.optString("title").orEmpty(),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                playback?.currentTrack?.optString("artist").orEmpty(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    }
                }

                InfoCard(
                    title = "Local music stays local",
                    body = "DJ Cloud shares track information and online playback links. It does not copy your phone's private music files to the cloud."
                )
            }

            (localError ?: managerError)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            (localNotice ?: notice)?.let {
                TextButton(onClick = { localNotice = null; manager.clearNotice() }) {
                    Text(it)
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(status: String) {
    val color = when (status) {
        "live" -> MaterialTheme.colorScheme.primary
        "offline" -> MaterialTheme.colorScheme.error
        "connecting" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = color.copy(alpha = 0.13f),
        contentColor = color,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            when (status) {
                "live" -> "LIVE"
                "offline" -> "OFFLINE"
                "connecting" -> "CONNECTING"
                else -> "SIGNED OUT"
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DeviceRow(device: DjCloudDevice, isCurrent: Boolean, isOutput: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isOutput) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            when (device.platform) {
                "android" -> Icons.Filled.Smartphone
                "desktop" -> Icons.Filled.Laptop
                else -> Icons.Filled.Devices
            },
            contentDescription = null,
            tint = if (isOutput) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                device.name + if (isCurrent) " (This phone)" else "",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                when {
                    isOutput -> "Current audio output"
                    device.online -> "Online"
                    else -> "Last seen"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (isOutput) Text("PLAYING", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
