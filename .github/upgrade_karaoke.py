from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main/java/com/example/MainActivity.kt'
MIC = ROOT / 'app/src/main/java/com/example/player/MicController.kt'

MAIN_MARKER = '// KARAOKE_DJ_ENGLISH_V2'
MIC_MARKER = '// KARAOKE_DSP_V2'

NEW_MIC_SCREEN = r'''@Composable
fun MicScreen(micController: MicController, scope: kotlinx.coroutines.CoroutineScope) {
    val context = LocalContext.current
    var inputExpanded by remember { mutableStateOf(false) }
    var outputExpanded by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) micController.toggleMic(true, scope)
        else Toast.makeText(context, "Microphone permission is required", Toast.LENGTH_SHORT).show()
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
                        Text(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) micController.selectedInputDevice?.productName?.toString() ?: "System Default Mic" else "System Default Mic", maxLines = 1)
                    }
                    DropdownMenu(inputExpanded, { inputExpanded = false }) {
                        DropdownMenuItem(text = { Text("System Default Mic") }, onClick = { micController.selectedInputDevice = null; inputExpanded = false })
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) micController.inputDevices.forEach { device ->
                            DropdownMenuItem(text = { Text(device.productName?.toString()?.ifBlank { "Audio Input ${device.id}" } ?: "Audio Input ${device.id}") }, onClick = { micController.selectedInputDevice = device; inputExpanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Output Device", style = MaterialTheme.typography.labelSmall)
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { outputExpanded = true }, Modifier.fillMaxWidth()) {
                        Text(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) micController.selectedOutputDevice?.productName?.toString() ?: "System Default Output" else "System Default Output", maxLines = 1)
                    }
                    DropdownMenu(outputExpanded, { outputExpanded = false }) {
                        DropdownMenuItem(text = { Text("System Default Output") }, onClick = { micController.selectedOutputDevice = null; outputExpanded = false })
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) micController.outputDevices.forEach { device ->
                            DropdownMenuItem(text = { Text(device.productName?.toString()?.ifBlank { "Audio Output ${device.id}" } ?: "Audio Output ${device.id}") }, onClick = { micController.selectedOutputDevice = device; outputExpanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("${micController.routingStatus}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("DJ Effects", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(micController.echoFxEnabled, { micController.echoFxEnabled = !micController.echoFxEnabled }, label = { Text("Echo") }, modifier = Modifier.weight(1f))
                    FilterChip(micController.reverbFxEnabled, { micController.reverbFxEnabled = !micController.reverbFxEnabled }, label = { Text("Reverb") }, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(micController.flangerFxEnabled, { micController.flangerFxEnabled = !micController.flangerFxEnabled }, label = { Text("Flanger") }, modifier = Modifier.weight(1f))
                    FilterChip(micController.beatFxEnabled, { micController.beatFxEnabled = !micController.beatFxEnabled }, label = { Text("Beat FX") }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text("Vocal Preset", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MicFilter.values().toList()) { filter ->
                        FilterChip(filter == micController.currentFilter, { micController.currentFilter = filter }, label = { Text(filter.displayName) })
                    }
                }
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("AEC & Noise Suppression enabled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
'''


def patch_main():
    text = MAIN.read_text(encoding='utf-8')
    if MAIN_MARKER in text:
        return False
    pattern = r'@Composable\nfun MicScreen\(micController: MicController, scope: kotlinx\.coroutines\.CoroutineScope\) \{.*?\n\}\n\n@Composable\nfun FullPlayerScreen'
    replacement = NEW_MIC_SCREEN.rstrip() + '\n\n@Composable\nfun FullPlayerScreen'
    new_text, n = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if n != 1:
        raise SystemExit('Could not locate MicScreen in MainActivity.kt')
    new_text = new_text.replace('fun MicScreen(micController: MicController, scope: kotlinx.coroutines.CoroutineScope) {', MAIN_MARKER + '\nfun MicScreen(micController: MicController, scope: kotlinx.coroutines.CoroutineScope) {', 1)
    MAIN.write_text(new_text, encoding='utf-8')
    return True


def patch_mic():
    text = MIC.read_text(encoding='utf-8')
    if MIC_MARKER in text:
        return False
    text = text.replace('import kotlinx.coroutines.*', 'import kotlinx.coroutines.*\nimport kotlin.math.PI\nimport kotlin.math.sin\n', 1)
    old_enum = '''enum class MicFilter(val displayName: String) {\n    NORMAL("عادي (طبيعي)"),\n    STUDIO_REVERB("صدى استوديو (كاريوكي)"),\n    CHIPMUNK("صوت كرتون (سنجاب)"),\n    MONSTER("صوت عميق (وحش)"),\n    ROBOT("صوت إلكتروني (روبوت)")\n}'''
    new_enum = '''enum class MicFilter(val displayName: String) {\n    NORMAL("Clean"),\n    STUDIO_REVERB("Studio Reverb"),\n    CHIPMUNK("Chipmunk"),\n    MONSTER("Monster"),\n    ROBOT("Robot")\n}\n\nenum class BeatFxDivision(val displayName: String, val beats: Float) {\n    HALF("1/2 Beat", 0.5f),\n    QUARTER("1/4 Beat", 0.25f),\n    THREE_QUARTER("3/4 Beat", 0.75f),\n    ONE("1 Beat", 1f)\n}'''
    if old_enum not in text:
        raise SystemExit('MicFilter enum not found')
    text = text.replace(old_enum, new_enum, 1)
    marker_props = '''    var echoLevel by mutableStateOf(0.3f)\n    var currentFilter by mutableStateOf(MicFilter.STUDIO_REVERB)\n'''
    add_props = '''    var echoLevel by mutableStateOf(0.3f)\n    var currentFilter by mutableStateOf(MicFilter.STUDIO_REVERB)\n    var echoFxEnabled by mutableStateOf(true)\n    var reverbFxEnabled by mutableStateOf(true)\n    var flangerFxEnabled by mutableStateOf(false)\n    var beatFxEnabled by mutableStateOf(false)\n    var reverbLevel by mutableStateOf(0.28f)\n    var flangerMix by mutableStateOf(0.35f)\n    var filterMix by mutableStateOf(0.55f)\n    var bpm by mutableStateOf(120f)\n    var beatFxDivision by mutableStateOf(BeatFxDivision.QUARTER)\n'''
    if marker_props not in text:
        raise SystemExit('Mic properties block not found')
    text = text.replace(marker_props, add_props, 1)

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useBluetoothHfp) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                if (selectedOutputDevice?.id == inputDevice?.id) audioManager.setCommunicationDevice(inputDevice)
            }
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(bufferSize).setTransferMode(AudioTrack.MODE_STREAM).build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) audioTrack?.setPreferredDevice(selectedOutputDevice)
            val sessionId = audioRecord?.audioSessionId ?: 0
            if (sessionId != 0) {
                if (AcousticEchoCanceler.isAvailable()) echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                if (NoiseSuppressor.isAvailable()) noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }
            audioRecord?.startRecording()
            if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw IllegalStateException("AudioRecord failed to start")
            audioTrack?.play()
            isMicEnabled = true
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
                        when (activeFilter) {
                            MicFilter.CHIPMUNK -> sample *= 1.12f
                            MicFilter.MONSTER -> sample *= 0.72f
                            MicFilter.ROBOT -> sample *= if ((i / 24) % 2 == 0) 1f else 0.55f
                            else -> Unit
                        }
                        val readDelay = fun(frames: Int): Float {
                            val idx = (writeIdx - frames + delayBuffer.size) % delayBuffer.size
                            return delayBuffer[idx].toFloat() / Short.MAX_VALUE.toFloat()
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
                            else -> when {
                                filterMix <= 0f -> combined
                                else -> combined * (1f - filterMix) + lowPass * filterMix
                            }
                        }
                        val beatEcho = if (beatFxEnabled) readDelay(beatDelay) * 0.35f else 0f
                        val output = (filtered + beatEcho).coerceIn(-1f, 1f) * micVolume
                        val outShort = (output.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                        buffer[i] = outShort
                        delayBuffer[writeIdx] = outShort
                        writeIdx = (writeIdx + 1) % delayBuffer.size
                    }
                    audioTrack?.write(buffer, 0, read)
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
    text = MIC_MARKER + '\n' + text
    MIC.write_text(text, encoding='utf-8')
    return True

changed = patch_main()
changed2 = patch_mic()
print(f'MainActivity patched: {changed}; MicController patched: {changed2}')
