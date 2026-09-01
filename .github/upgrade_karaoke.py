from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main/java/com/example/MainActivity.kt'
MIC = ROOT / 'app/src/main/java/com/example/player/MicController.kt'
SERVICE = ROOT / 'app/src/main/java/com/example/player/MusicService.kt'
MANIFEST = ROOT / 'app/src/main/AndroidManifest.xml'

MARKER = '// KARAOKE_MIC_PAGE_V4'

NEW_MIC_SCREEN = r'''@Composable
fun MicScreen(micController: MicController, context: Context, scope: kotlinx.coroutines.CoroutineScope) {
    var inputExpanded by remember { mutableStateOf(false) }
    var outputExpanded by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) micController.toggleMic(true, scope)
        else Toast.makeText(context, "Microphone permission is required", Toast.LENGTH_SHORT).show()
    }

    val saveRecordingLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri ->
        if (uri == null) {
            micController.discardPendingRecording()
        } else {
            scope.launch {
                val ok = withContext(kotlinx.coroutines.Dispatchers.IO) { micController.savePendingRecording(uri) }
                Toast.makeText(context, if (ok) "Recording saved" else "Unable to save recording", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Karaoke Studio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Live vocal monitor with DJ-style effects", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier.size(116.dp).clip(CircleShape)
                .background(if (micController.isMicEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    if (micController.isMicEnabled) micController.toggleMic(false, scope)
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(if (micController.isMicEnabled) Icons.Filled.Mic else Icons.Filled.MicOff, null, Modifier.size(46.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(if (micController.isMicEnabled) "LIVE MONITOR ON" else "Tap to enable microphone", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Audio Routing", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Input Device", style = MaterialTheme.typography.labelSmall)
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { inputExpanded = true }, Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(micController.selectedInputDevice?.displayName() ?: "System Default Mic", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    DropdownMenu(inputExpanded, { inputExpanded = false }) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                            text = { Text("System Default Mic") },
                            onClick = { micController.selectInputDevice(null, scope); inputExpanded = false }
                        )
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) micController.inputDevices.forEach { device ->
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                                text = { Text(device.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = { micController.selectInputDevice(device, scope); inputExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Output Device", style = MaterialTheme.typography.labelSmall)
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { outputExpanded = true }, Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(micController.selectedOutputDevice?.displayName() ?: "System Default Output", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    DropdownMenu(outputExpanded, { outputExpanded = false }) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Filled.VolumeUp, contentDescription = null) },
                            text = { Text("System Default Output") },
                            onClick = { micController.selectOutputDevice(null); outputExpanded = false }
                        )
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) micController.outputDevices.forEach { device ->
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Filled.VolumeUp, contentDescription = null) },
                                text = { Text(device.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = { micController.selectOutputDevice(device); outputExpanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { micController.refreshDevices() }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh connected devices")
                }
                Text("${micController.routingStatus}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Mix & FX Amount", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("Mic Volume: ${(micController.micVolume * 100).toInt()}%")
                Slider(micController.micVolume, { micController.micVolume = it }, valueRange = 0f..2f)
                Text("Echo: ${(micController.echoLevel * 100).toInt()}%")
                Slider(micController.echoLevel, { micController.echoLevel = it }, valueRange = 0f..1f)
                Text("Reverb: ${(micController.reverbLevel * 100).toInt()}%")
                Slider(micController.reverbLevel, { micController.reverbLevel = it }, valueRange = 0f..1f)
                Text("Flanger: ${(micController.flangerMix * 100).toInt()}%")
                Slider(micController.flangerMix, { micController.flangerMix = it }, valueRange = 0f..1f)
                Text("Filter: ${(micController.filterMix * 100).toInt()}%")
                Slider(micController.filterMix, { micController.filterMix = it }, valueRange = 0f..1f)
                Spacer(Modifier.height(4.dp))
                Text("Vocal Filters", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MicFilter.values().toList()) { filter ->
                        FilterChip(
                            selected = filter == micController.currentFilter,
                            onClick = { micController.currentFilter = filter },
                            label = { Text(filter.displayName) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Beat FX", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("BPM: ${micController.bpm.toInt()}")
                Slider(micController.bpm, { micController.bpm = it }, valueRange = 70f..180f)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(BeatFxDivision.values().toList()) { div ->
                        FilterChip(div == micController.beatFxDivision, { micController.beatFxDivision = div }, label = { Text(div.displayName) })
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("AEC & Noise Suppression", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Active voice cleanup for the microphone", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = micController.voiceProcessingEnabled,
                        onCheckedChange = { micController.setVoiceProcessingEnabled(it) }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (micController.voiceProcessingEnabled) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FiberManualRecord, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Recording", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Record the current processed microphone output", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(micController.recordingDurationText, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    enabled = micController.isMicEnabled || micController.isOutputRecording,
                    onClick = {
                        if (micController.isOutputRecording) {
                            micController.stopOutputRecording()
                            saveRecordingLauncher.launch(micController.suggestedRecordingName())
                        } else if (micController.startOutputRecording()) {
                            Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Enable the microphone first", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (micController.isOutputRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (micController.isOutputRecording) "Stop & Save" else "Start Recording")
                }
                Text(micController.recordingStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (micController.isMicEnabled) "Microphone monitor is active" else "Microphone monitor is off", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
'''


def replace_once(text: str, pattern: str, replacement: str, label: str) -> str:
    new_text, n = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if n != 1:
        raise SystemExit(f'Could not patch {label}')
    return new_text


def patch_main():
    text = MAIN.read_text(encoding='utf-8')
    if MARKER in text:
        return
    if 'import kotlinx.coroutines.withContext' not in text:
        text = text.replace('import kotlinx.coroutines.launch\n', 'import kotlinx.coroutines.launch\nimport kotlinx.coroutines.withContext\n', 1)
    text = replace_once(
        text,
        r'@Composable\nfun MicScreen\(micController: MicController, context: Context, scope: kotlinx\.coroutines\.CoroutineScope\) \{.*?\n\}\n\n@Composable\nfun FullPlayerScreen',
        MARKER + '\n' + NEW_MIC_SCREEN.rstrip() + '\n\n@Composable\nfun FullPlayerScreen',
        'MicScreen'
    )
    MAIN.write_text(text, encoding='utf-8')


def patch_mic():
    text = MIC.read_text(encoding='utf-8')
    if MARKER in text:
        return
    for old, new in [
        ('import android.content.Context\n', 'import android.content.Context\nimport android.content.Intent\n'),
        ('import android.content.Intent\n', 'import android.content.Intent\nimport android.net.Uri\n'),
        ('import androidx.compose.runtime.setValue\n', 'import androidx.compose.runtime.setValue\nimport androidx.core.content.ContextCompat\n'),
        ('import kotlin.math.sin\n', 'import kotlin.math.sin\nimport java.io.File\nimport java.io.RandomAccessFile\n'),
    ]:
        if new.splitlines()[1] not in text:
            text = text.replace(old, new, 1)

    old_enum = '''enum class MicFilter(val displayName: String) {\n    NORMAL("Clean"),\n    STUDIO_REVERB("Studio Reverb"),\n    CHIPMUNK("Chipmunk"),\n    MONSTER("Monster"),\n    ROBOT("Robot")\n}'''
    new_enum = '''enum class MicFilter(val displayName: String) {\n    NORMAL("Clean"),\n    STUDIO_REVERB("Studio Reverb"),\n    CHIPMUNK("Chipmunk"),\n    MONSTER("Monster"),\n    ROBOT("Robot"),\n    TELEPHONE("Telephone"),\n    RADIO("Radio"),\n    MEGAPHONE("Megaphone"),\n    CHORUS("Chorus"),\n    TREMOLO("Tremolo"),\n    BASS_BOOST("Bass Boost")\n}'''
    if old_enum in text:
        text = text.replace(old_enum, new_enum, 1)

    prop_anchor = '    var beatFxDivision by mutableStateOf(BeatFxDivision.QUARTER)\n'
    add_props = '''    var beatFxDivision by mutableStateOf(BeatFxDivision.QUARTER)\n\n    var voiceProcessingEnabled by mutableStateOf(true)\n        private set\n    var isOutputRecording by mutableStateOf(false)\n        private set\n    var recordingStatus by mutableStateOf("Ready to record")\n        private set\n    var recordingDurationText by mutableStateOf("00:00")\n        private set\n\n    private var pendingRecordingFile: File? = null\n    private var recordingWriter: RandomAccessFile? = null\n    private var recordedPcmBytes = 0L\n    private var recordingStartedAt = 0L\n    private var recordingTickerJob: Job? = null\n    private val recordingLock = Any()\n'''
    if 'var voiceProcessingEnabled by' not in text:
        if prop_anchor not in text:
            raise SystemExit('Mic property anchor not found')
        text = text.replace(prop_anchor, add_props, 1)

    start = text.index('    private fun startMic(coroutineScope: CoroutineScope) {')
    end = text.index('    @SuppressLint("MissingPermission")\n    private fun applyInputRouting()', start)
    new_start = r'''    private fun startMic(coroutineScope: CoroutineScope) {
        if (isMicEnabled) return
        try {
            val inputDevice = selectedInputDevice
            val useBluetoothHfp = inputDevice?.isBluetoothSco() == true
            val audioSource = if (useBluetoothHfp) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC
            audioRecord = AudioRecord(audioSource, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) audioRecord?.setPreferredDevice(inputDevice)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useBluetoothHfp) audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(bufferSize).setTransferMode(AudioTrack.MODE_STREAM).build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) audioTrack?.setPreferredDevice(selectedOutputDevice)
            val sessionId = audioRecord?.audioSessionId ?: 0
            if (sessionId != 0) {
                if (AcousticEchoCanceler.isAvailable()) echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = voiceProcessingEnabled }
                if (NoiseSuppressor.isAvailable()) noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = voiceProcessingEnabled }
            }
            audioRecord?.startRecording()
            if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw IllegalStateException("AudioRecord failed to start")
            audioTrack?.play()
            isMicEnabled = true
            startMicForegroundService()
            AudioPlayerController.updateGlobalPreferredAudioDevice(selectedOutputDevice)
            updateRoutingStatus()
            recordingJob = coroutineScope.launch(Dispatchers.IO) {
                val buffer = ShortArray(bufferSize / 2)
                val delayBuffer = ShortArray(sampleRate)
                var writeIdx = 0
                var lowPass = 0f
                while (isActive && isMicEnabled) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read <= 0) continue
                    val activeFilter = currentFilter
                    val currentBpm = bpm.coerceIn(70f, 180f)
                    val beatDelay = ((sampleRate * 60f / currentBpm) * beatFxDivision.beats).toInt().coerceIn(1, delayBuffer.size - 1)
                    for (i in 0 until read) {
                        var sample = buffer[i].toFloat() / Short.MAX_VALUE.toFloat()
                        val readDelay = fun(frames: Int): Float {
                            val idx = (writeIdx - frames + delayBuffer.size) % delayBuffer.size
                            return delayBuffer[idx].toFloat() / Short.MAX_VALUE.toFloat()
                        }
                        if (voiceProcessingEnabled && kotlin.math.abs(sample) < 0.018f) sample *= 0.12f
                        when (activeFilter) {
                            MicFilter.CHIPMUNK -> sample *= 1.12f
                            MicFilter.MONSTER -> sample *= 0.72f
                            MicFilter.ROBOT -> sample *= if ((i / 24) % 2 == 0) 1f else 0.55f
                            MicFilter.TELEPHONE -> {
                                lowPass += 0.16f * (sample - lowPass)
                                sample = ((sample - lowPass) * 1.8f).coerceIn(-1f, 1f)
                            }
                            MicFilter.RADIO -> {
                                lowPass += 0.2f * (sample - lowPass)
                                sample = (lowPass * 3.2f).coerceIn(-1f, 1f)
                            }
                            MicFilter.MEGAPHONE -> {
                                lowPass += 0.24f * (sample - lowPass)
                                sample = (lowPass * 4f).coerceIn(-1f, 1f)
                            }
                            MicFilter.CHORUS -> {
                                val lfo = (sin(2.0 * PI * (writeIdx.toDouble() / sampleRate) * 0.45) + 1.0) * 0.5
                                val d = (sampleRate * (0.012 + 0.006 * lfo)).toInt().coerceIn(1, delayBuffer.size - 1)
                                sample += readDelay(d) * 0.55f
                            }
                            MicFilter.TREMOLO -> {
                                val trem = 0.55f + 0.45f * sin(2.0 * PI * writeIdx.toDouble() / sampleRate * 5.5).toFloat()
                                sample *= trem
                            }
                            MicFilter.BASS_BOOST -> {
                                lowPass += 0.08f * (sample - lowPass)
                                sample = (sample + lowPass * 0.75f).coerceIn(-1f, 1f)
                            }
                            else -> Unit
                        }
                        val echo = if (echoFxEnabled) readDelay((sampleRate * 0.24f).toInt()) * echoLevel else 0f
                        val reverb = if (reverbFxEnabled) (readDelay((sampleRate * 0.045f).toInt()) * 0.24f + readDelay((sampleRate * 0.085f).toInt()) * 0.16f) * reverbLevel else 0f
                        val flanger = if (flangerFxEnabled) {
                            val lfo = (sin(2.0 * PI * (writeIdx.toDouble() / sampleRate) * 0.35) + 1.0) * 0.5
                            val d = (sampleRate * (0.001 + 0.004 * lfo)).toInt().coerceIn(1, delayBuffer.size - 1)
                            readDelay(d) * flangerMix
                        } else 0f
                        val combined = sample + echo + reverb + flanger
                        lowPass += 0.12f * (combined - lowPass)
                        val filtered = when (activeFilter) {
                            MicFilter.STUDIO_REVERB -> combined
                            else -> if (filterMix <= 0f) combined else combined * (1f - filterMix) + lowPass * filterMix
                        }
                        val beatEcho = if (beatFxEnabled) readDelay(beatDelay) * 0.35f else 0f
                        val output = (filtered + beatEcho).coerceIn(-1f, 1f) * micVolume
                        val outShort = (output.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                        buffer[i] = outShort
                        delayBuffer[writeIdx] = outShort
                        writeIdx = (writeIdx + 1) % delayBuffer.size
                    }
                    audioTrack?.write(buffer, 0, read)
                    appendRecordingPcm(buffer, read)
                }
            }
        } catch (t: Throwable) {
            routingStatus = "Microphone start failed: ${t.message ?: "Unknown error"}"
            t.printStackTrace()
            stopMic()
        }
    }
'''
    text = text[:start] + new_start + text[end:]

    insert_before = '    @SuppressLint("MissingPermission")\n    private fun applyInputRouting()'
    helpers = r'''    fun setVoiceProcessingEnabled(enabled: Boolean) {
        voiceProcessingEnabled = enabled
        try { echoCanceler?.enabled = enabled } catch (_: Throwable) { }
        try { noiseSuppressor?.enabled = enabled } catch (_: Throwable) { }
        recordingStatus = if (enabled) "AEC + noise suppression enabled" else "Voice cleanup disabled"
    }

    private fun startMicForegroundService() {
        try {
            val intent = Intent(context, MusicService::class.java).setAction(MusicService.ACTION_MIC_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        } catch (t: Throwable) {
            routingStatus = "Microphone service start failed: ${t.message ?: "Unknown error"}"
        }
    }

    private fun stopMicForegroundService() {
        try {
            context.startService(Intent(context, MusicService::class.java).setAction(MusicService.ACTION_MIC_STOP))
        } catch (_: Throwable) { }
    }

    private fun writeWavHeader(file: RandomAccessFile, dataLength: Long) {
        val byteRate = sampleRate * 2
        val totalLength = 36L + dataLength
        file.seek(0)
        file.writeBytes("RIFF")
        file.writeInt(Integer.reverseBytes(totalLength.toInt()))
        file.writeBytes("WAVE")
        file.writeBytes("fmt ")
        file.writeInt(Integer.reverseBytes(16))
        file.writeShort(java.lang.Short.reverseBytes(1).toInt())
        file.writeShort(java.lang.Short.reverseBytes(1).toInt())
        file.writeInt(Integer.reverseBytes(sampleRate))
        file.writeInt(Integer.reverseBytes(byteRate))
        file.writeShort(java.lang.Short.reverseBytes(2).toInt())
        file.writeShort(java.lang.Short.reverseBytes(16).toInt())
        file.writeBytes("data")
        file.writeInt(Integer.reverseBytes(dataLength.toInt()))
    }

    fun startOutputRecording(): Boolean {
        if (!isMicEnabled || isOutputRecording) return false
        return try {
            val file = File(context.cacheDir, "mic_output_${System.currentTimeMillis()}.wav")
            val writer = RandomAccessFile(file, "rw")
            writeWavHeader(writer, 0L)
            synchronized(recordingLock) {
                pendingRecordingFile = null
                recordingWriter = writer
                recordedPcmBytes = 0L
                recordingStartedAt = System.currentTimeMillis()
                isOutputRecording = true
                recordingStatus = "Recording processed microphone output"
                recordingDurationText = "00:00"
            }
            recordingTickerJob?.cancel()
            recordingTickerJob = CoroutineScope(Dispatchers.Main.immediate).launch {
                while (isActive && isOutputRecording) {
                    val elapsed = (System.currentTimeMillis() - recordingStartedAt).coerceAtLeast(0L) / 1000L
                    recordingDurationText = "%02d:%02d".format(elapsed / 60L, elapsed % 60L)
                    delay(500L)
                }
            }
            true
        } catch (t: Throwable) {
            recordingStatus = "Unable to start recording: ${t.message ?: "Unknown error"}"
            false
        }
    }

    private fun appendRecordingPcm(buffer: ShortArray, count: Int) {
        synchronized(recordingLock) {
            val writer = recordingWriter ?: return
            if (!isOutputRecording) return
            val bytes = ByteArray(count * 2)
            var p = 0
            for (i in 0 until count) {
                val v = buffer[i].toInt()
                bytes[p++] = (v and 0xff).toByte()
                bytes[p++] = ((v ushr 8) and 0xff).toByte()
            }
            try {
                writer.write(bytes)
                recordedPcmBytes += bytes.size.toLong()
            } catch (t: Throwable) {
                recordingStatus = "Recording write failed: ${t.message ?: "Unknown error"}"
            }
        }
    }

    fun stopOutputRecording(): Boolean {
        synchronized(recordingLock) {
            if (!isOutputRecording) return pendingRecordingFile?.exists() == true
            isOutputRecording = false
            recordingTickerJob?.cancel()
            recordingTickerJob = null
            val writer = recordingWriter
            recordingWriter = null
            try {
                writer?.let {
                    writeWavHeader(it, recordedPcmBytes)
                    it.fd.sync()
                    it.close()
                }
            } catch (t: Throwable) {
                recordingStatus = "Unable to finalize recording: ${t.message ?: "Unknown error"}"
            }
            pendingRecordingFile = context.cacheDir.listFiles()
                ?.filter { f -> f.name.startsWith("mic_output_") && f.extension == "wav" }
                ?.maxByOrNull { it.lastModified() }
            recordingStatus = if (pendingRecordingFile?.exists() == true) "Choose a location and filename to save" else "Recording stopped"
            return pendingRecordingFile?.exists() == true
        }
    }

    fun suggestedRecordingName(): String = "DJ_Mic_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())}.wav"

    suspend fun savePendingRecording(uri: Uri): Boolean {
        val source = pendingRecordingFile ?: return false
        return try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: return false
            source.delete()
            pendingRecordingFile = null
            recordingStatus = "Recording saved successfully"
            true
        } catch (t: Throwable) {
            recordingStatus = "Save failed: ${t.message ?: "Unknown error"}"
            false
        }
    }

    fun discardPendingRecording() {
        pendingRecordingFile?.delete()
        pendingRecordingFile = null
        recordingStatus = "Recording discarded"
    }

'''
    if 'fun startOutputRecording()' not in text:
        text = text.replace(insert_before, helpers + insert_before, 1)

    text = text.replace('    private fun stopMic() {\n        isMicEnabled = false\n', '    private fun stopMic() {\n        isMicEnabled = false\n        if (isOutputRecording) stopOutputRecording()\n        stopMicForegroundService()\n', 1)
    MIC.write_text(text, encoding='utf-8')


def patch_service():
    text = SERVICE.read_text(encoding='utf-8')
    if 'ACTION_MIC_START' not in text:
        text = text.replace('        const val ACTION_STOP = "com.example.action.STOP"\n', '        const val ACTION_STOP = "com.example.action.STOP"\n        const val ACTION_MIC_START = "com.example.action.MIC_START"\n        const val ACTION_MIC_STOP = "com.example.action.MIC_STOP"\n', 1)
    if 'MIC_NOTIFICATION_ID' not in text:
        text = text.replace('        const val NOTIFICATION_ID = 1001\n', '        const val NOTIFICATION_ID = 1001\n        const val MIC_NOTIFICATION_ID = 1002\n', 1)
    if 'private var micActive' not in text:
        text = text.replace('    private lateinit var mediaSession: MediaSessionCompat\n', '    private lateinit var mediaSession: MediaSessionCompat\n    private var micActive = false\n', 1)
    if 'ACTION_MIC_START ->' not in text:
        text = text.replace('            ACTION_STOP -> {\n                playerController?.pause()\n                stopForeground(STOP_FOREGROUND_REMOVE)\n                stopSelf()\n            }\n', '            ACTION_STOP -> {\n                playerController?.pause()\n                stopForeground(STOP_FOREGROUND_REMOVE)\n                stopSelf()\n            }\n            ACTION_MIC_START -> {\n                micActive = true\n                if (playerController?.isPlaying != true) updateMicNotification()\n            }\n            ACTION_MIC_STOP -> {\n                micActive = false\n                if (playerController?.isPlaying != true) {\n                    stopForeground(STOP_FOREGROUND_REMOVE)\n                    stopSelf()\n                }\n            }\n', 1)
    if 'private fun updateMicNotification()' not in text:
        anchor = '    fun updateNotification(title: String, artist: String, isPlaying: Boolean) {\n'
        notif = '''    private fun updateMicNotification() {\n        val notificationIntent = Intent(this, MainActivity::class.java)\n        val pendingIntent = PendingIntent.getActivity(this, 99, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)\n        val notification = NotificationCompat.Builder(this, CHANNEL_ID)\n            .setContentTitle("DJ Microphone")\n            .setContentText("Live microphone monitor is running")\n            .setSmallIcon(android.R.drawable.ic_btn_speak_now)\n            .setContentIntent(pendingIntent)\n            .setOngoing(true)\n            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)\n            .build()\n        startForeground(MIC_NOTIFICATION_ID, notification)\n    }\n\n'''
        text = text.replace(anchor, notif + anchor, 1)
    if 'if (micActive && !isPlaying)' not in text:
        text = text.replace('    fun updateNotification(title: String, artist: String, isPlaying: Boolean) {\n', '    fun updateNotification(title: String, artist: String, isPlaying: Boolean) {\n        if (micActive && !isPlaying) {\n            updateMicNotification()\n            return\n        }\n', 1)
    SERVICE.write_text(text, encoding='utf-8')


def patch_manifest():
    text = MANIFEST.read_text(encoding='utf-8')
    if 'android.permission.FOREGROUND_SERVICE_MICROPHONE' not in text:
        text = text.replace('    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA" />\n', '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA" />\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />\n', 1)
    text = text.replace('android:foregroundServiceType="mediaPlayback"', 'android:foregroundServiceType="mediaPlayback|microphone"', 1)
    MANIFEST.write_text(text, encoding='utf-8')


patch_main()
patch_mic()
patch_service()
patch_manifest()
print('Applied karaoke microphone page V4 changes.')
