// KARAOKE_DSP_V2
package com.example.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.sin
import java.io.File
import java.io.RandomAccessFile
import java.io.FileInputStream
import java.io.FileOutputStream


enum class MicFilter(val displayName: String) {
    NORMAL("Clean"), STUDIO_REVERB("Studio Reverb"), CHIPMUNK("Chipmunk"), MONSTER("Monster"), ROBOT("Robot"),
    TELEPHONE("Telephone"), RADIO("Radio"), MEGAPHONE("Megaphone"), CHORUS("Chorus"), TREMOLO("Tremolo"), BASS_BOOST("Bass Boost")
}

enum class BeatFxDivision(val displayName: String, val beats: Float) {
    HALF("1/2 Beat", 0.5f),
    QUARTER("1/4 Beat", 0.25f),
    THREE_QUARTER("3/4 Beat", 0.75f),
    ONE("1 Beat", 1f)
}

private fun AudioDeviceInfo.isSupportedInputDevice(): Boolean {
    return when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC -> isSource
        else -> isSource && !isSink
    }
}

private fun AudioDeviceInfo.isSupportedOutputDevice(): Boolean {
    return when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC -> isSink
        else -> isSink && !isSource
    }
}

private fun AudioDeviceInfo.isBluetoothOutputDevice(): Boolean {
    return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
        type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
        type == AudioDeviceInfo.TYPE_BLE_SPEAKER
}

private fun AudioDeviceInfo.displayName(): String {
    val product = productName?.toString()?.trim().orEmpty()
    val fallback = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Microphone"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone Speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Audio"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Headset / Mic"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE Headset"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE Speaker"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset / Mic"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Audio"
        else -> "Audio Device #$id"
    }
    return if (product.isNotEmpty()) product else fallback
}

class MicController(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    var isMicEnabled by mutableStateOf(false)
        private set

    var micVolume by mutableStateOf(1.2f)
    var echoLevel by mutableStateOf(0.3f)
    var currentFilter by mutableStateOf(MicFilter.STUDIO_REVERB)
    var echoFxEnabled by mutableStateOf(true)
    var reverbFxEnabled by mutableStateOf(true)
    var flangerFxEnabled by mutableStateOf(false)
    var beatFxEnabled by mutableStateOf(true)
    var reverbLevel by mutableStateOf(0.28f)
    var flangerMix by mutableStateOf(0.35f)
    var filterMix by mutableStateOf(0.55f)
    var bpm by mutableStateOf(120f)
    var beatFxDivision by mutableStateOf(BeatFxDivision.QUARTER)

    var voiceProcessingEnabled by mutableStateOf(true)
        private set
    var isOutputRecording by mutableStateOf(false)
        private set
    var recordingStatus by mutableStateOf("Ready to record")
        private set
    var recordingDurationText by mutableStateOf("00:00")
        private set
    private var pendingRecordingFile: File? = null
    private var recordingFile: File? = null
    private var recordingWriter: RandomAccessFile? = null
    private var recordedPcmBytes = 0L
    private var recordingStartedAt = 0L
    private var recordingTickerJob: Job? = null
    private val recordingLock = Any()

    var inputDevices by mutableStateOf<List<AudioDeviceInfo>>(emptyList())
        private set
    var outputDevices by mutableStateOf<List<AudioDeviceInfo>>(emptyList())
        private set

    // Backing state holders let us keep custom routing side effects while
    // remaining compatible with Compose's delegated mutableStateOf properties.
    private var selectedInputState by mutableStateOf<AudioDeviceInfo?>(null)
    private var selectedOutputState by mutableStateOf<AudioDeviceInfo?>(null)

    var selectedInputDevice: AudioDeviceInfo?
        get() = selectedInputState
        set(value) {
            selectedInputState = value
            if (isMicEnabled) applyInputRouting()
        }

    var selectedOutputDevice: AudioDeviceInfo?
        get() = selectedOutputState
        set(value) {
            selectedOutputState = value
            // Keep the live microphone monitor and the main Media3 player in sync.
            if (isMicEnabled) applyOutputRouting()
            AudioPlayerController.updateGlobalPreferredAudioDevice(value)
        }

    var routingStatus by mutableStateOf("جاهز لتوجيه الصوت")
        private set

    private val sampleRate = 44100
    private val minBufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(2048)
    private val bufferSize = minBufferSize * 2

    init {
        requestBluetoothPermissionsIfNeeded()
        refreshDevices()
    }

    private fun requestBluetoothPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val activity = context as? Activity ?: return
        val missing = buildList {
            if (activity.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                add(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (activity.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                add(android.Manifest.permission.BLUETOOTH_SCAN)
            }
        }
        if (missing.isNotEmpty()) activity.requestPermissions(missing.toTypedArray(), BLUETOOTH_PERMISSION_REQUEST_CODE)
    }

    @SuppressLint("MissingPermission")
    fun refreshDevices() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                routingStatus = "Allow Bluetooth access, then refresh devices"
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val allInputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
                val allOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
                inputDevices = allInputs.filter { it.isSupportedInputDevice() }.distinctBy { it.id }
                    .sortedWith(compareBy({ it.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO }, { it.displayName() }))
                outputDevices = allOutputs.filter { it.isSupportedOutputDevice() }.distinctBy { it.id }
                    .sortedWith(compareBy({ !it.isBluetoothOutputDevice() }, { it.displayName() }))
                routingStatus = when {
                    inputDevices.isEmpty() && outputDevices.isEmpty() -> "No supported audio devices detected"
                    else -> "${inputDevices.size} input device(s) • ${outputDevices.size} output device(s)"
                }
            }
        } catch (t: Throwable) {
            routingStatus = "Unable to read audio devices: ${t.message ?: "Unknown error"}"
        }
    }

    @SuppressLint("MissingPermission")
    fun toggleMic(enabled: Boolean, coroutineScope: CoroutineScope) {
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestBluetoothPermissionsIfNeeded()
                routingStatus = "امنح إذن Bluetooth ثم شغّل الميكروفون مرة أخرى"
                return
            }
            startMic(coroutineScope)
        } else {
            stopMic()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startMic(coroutineScope: CoroutineScope) {
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
            updateRoutingStatus()
            recordingJob = coroutineScope.launch(Dispatchers.IO) {
                val buffer = ShortArray(bufferSize / 2)
                val delayBuffer = ShortArray(sampleRate)
                val delayCap = delayBuffer.size
                var writeIdx = 0
                var lowPass = 0f
                var lfoPhase = 0.0

                val echoDelayFrames = (sampleRate * 0.24f).toInt().coerceIn(1, delayCap - 1)
                val rev1Frames = (sampleRate * 0.045f).toInt().coerceIn(1, delayCap - 1)
                val rev2Frames = (sampleRate * 0.085f).toInt().coerceIn(1, delayCap - 1)

                while (isActive && isMicEnabled) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read <= 0) continue

                    val activeFilter = currentFilter
                    val currentBpm = bpm.coerceIn(70f, 180f)
                    val beatDelay = ((sampleRate * 60f / currentBpm) * beatFxDivision.beats).toInt().coerceIn(1, delayCap - 1)

                    for (i in 0 until read) {
                        var sample = buffer[i].toFloat() / 32768f
                        if (voiceProcessingEnabled && kotlin.math.abs(sample) < 0.012f) {
                            sample *= 0.08f
                        }

                        lfoPhase += 1.0 / sampleRate
                        if (lfoPhase > 100.0) lfoPhase -= 100.0

                        when (activeFilter) {
                            MicFilter.CHIPMUNK -> {
                                val d = (sampleRate * 0.008).toInt()
                                val idx = (writeIdx - d + delayCap) % delayCap
                                sample = sample * 0.6f + (delayBuffer[idx].toFloat() / 32768f) * 0.6f
                            }
                            MicFilter.MONSTER -> {
                                lowPass += 0.25f * (sample - lowPass)
                                sample = lowPass * 1.5f
                            }
                            MicFilter.ROBOT -> {
                                val carrier = sin(2.0 * PI * 160.0 * lfoPhase).toFloat()
                                sample *= carrier * 1.2f
                            }
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
                                val lfo = (sin(2.0 * PI * lfoPhase * 0.45) + 1.0) * 0.5
                                val d = (sampleRate * (0.012 + 0.006 * lfo)).toInt().coerceIn(1, delayCap - 1)
                                val idx = (writeIdx - d + delayCap) % delayCap
                                sample += (delayBuffer[idx].toFloat() / 32768f) * 0.55f
                            }
                            MicFilter.TREMOLO -> {
                                val trem = (0.55 + 0.45 * sin(2.0 * PI * lfoPhase * 5.5)).toFloat()
                                sample *= trem
                            }
                            MicFilter.BASS_BOOST -> {
                                lowPass += 0.08f * (sample - lowPass)
                                sample = (sample + lowPass * 0.75f).coerceIn(-1f, 1f)
                            }
                            else -> Unit
                        }

                        val echoIdx = (writeIdx - echoDelayFrames + delayCap) % delayCap
                        val echo = if (echoFxEnabled) (delayBuffer[echoIdx].toFloat() / 32768f) * echoLevel else 0f

                        val r1Idx = (writeIdx - rev1Frames + delayCap) % delayCap
                        val r2Idx = (writeIdx - rev2Frames + delayCap) % delayCap
                        val reverb = if (reverbFxEnabled) {
                            ((delayBuffer[r1Idx].toFloat() / 32768f) * 0.24f + (delayBuffer[r2Idx].toFloat() / 32768f) * 0.16f) * reverbLevel
                        } else 0f

                        val flanger = if (flangerFxEnabled) {
                            val lfo = (sin(2.0 * PI * lfoPhase * 0.35) + 1.0) * 0.5
                            val d = (sampleRate * (0.001 + 0.004 * lfo)).toInt().coerceIn(1, delayCap - 1)
                            val fIdx = (writeIdx - d + delayCap) % delayCap
                            (delayBuffer[fIdx].toFloat() / 32768f) * flangerMix
                        } else 0f

                        val combined = sample + echo + reverb + flanger
                        lowPass += 0.12f * (combined - lowPass)

                        val filtered = if (activeFilter == MicFilter.STUDIO_REVERB) combined
                        else if (filterMix <= 0f) combined
                        else combined * (1f - filterMix) + lowPass * filterMix

                        val beatIdx = (writeIdx - beatDelay + delayCap) % delayCap
                        val beatEcho = if (beatFxEnabled) (delayBuffer[beatIdx].toFloat() / 32768f) * 0.35f else 0f

                        val output = (filtered + beatEcho).coerceIn(-1f, 1f) * micVolume
                        val outShort = (output.coerceIn(-1f, 1f) * 32767f).toInt().toShort()

                        buffer[i] = outShort
                        delayBuffer[writeIdx] = outShort
                        writeIdx = (writeIdx + 1) % delayCap
                    }

                    audioTrack?.write(buffer, 0, read)
                    appendRecordingPcm(buffer, read)
                }
            }
        } catch (t: Throwable) { routingStatus = "Microphone start failed: ${t.message ?: "Unknown error"}"; t.printStackTrace(); stopMic() }
    }





    fun toggleVoiceProcessing(enabled: Boolean) {
        voiceProcessingEnabled = enabled
        try { echoCanceler?.enabled = enabled } catch (_: Throwable) { }
        try { noiseSuppressor?.enabled = enabled } catch (_: Throwable) { }
        recordingStatus = if (enabled) "AEC + noise suppression enabled" else "Voice cleanup disabled"
    }

    private fun startMicForegroundService() {
        try {
            val i = Intent(context, MusicService::class.java).setAction(MusicService.ACTION_MIC_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, i) else context.startService(i)
        } catch (t: Throwable) { routingStatus = "Microphone service start failed: ${t.message ?: "Unknown error"}" }
    }

    private fun stopMicForegroundService() { try { context.startService(Intent(context, MusicService::class.java).setAction(MusicService.ACTION_MIC_STOP)) } catch (_: Throwable) { } }

    private fun writeWavHeader(file: RandomAccessFile, dataLength: Long) {
        val byteRate = sampleRate * 2; val totalLength = 36L + dataLength
        file.seek(0); file.writeBytes("RIFF"); file.writeInt(Integer.reverseBytes(totalLength.toInt())); file.writeBytes("WAVEfmt ")
        file.writeInt(Integer.reverseBytes(16)); file.writeShort(java.lang.Short.reverseBytes(1).toInt()); file.writeShort(java.lang.Short.reverseBytes(1).toInt())
        file.writeInt(Integer.reverseBytes(sampleRate)); file.writeInt(Integer.reverseBytes(byteRate)); file.writeShort(java.lang.Short.reverseBytes(2).toInt()); file.writeShort(java.lang.Short.reverseBytes(16).toInt())
        file.writeBytes("data"); file.writeInt(Integer.reverseBytes(dataLength.toInt()))
    }

    fun startOutputRecording(): Boolean {
        if (!isMicEnabled || isOutputRecording) return false
        return try {
            val file = File(context.cacheDir, "mic_output_${System.currentTimeMillis()}.wav"); val writer = RandomAccessFile(file, "rw"); writeWavHeader(writer, 0L)
            synchronized(recordingLock) { recordingFile = file; pendingRecordingFile = null; recordingWriter = writer; recordedPcmBytes = 0L; recordingStartedAt = System.currentTimeMillis(); isOutputRecording = true; recordingDurationText = "00:00"; recordingStatus = "Recording processed microphone output" }
            recordingTickerJob?.cancel(); recordingTickerJob = CoroutineScope(Dispatchers.Main.immediate).launch {
                while (isActive && isOutputRecording) { val s = ((System.currentTimeMillis() - recordingStartedAt) / 1000L).coerceAtLeast(0L); recordingDurationText = "%02d:%02d".format(s / 60L, s % 60L); delay(500L) }
            }; true
        } catch (t: Throwable) { recordingStatus = "Unable to start recording: ${t.message ?: "Unknown error"}"; false }
    }

    private fun appendRecordingPcm(buffer: ShortArray, count: Int) {
        synchronized(recordingLock) {
            val writer = recordingWriter ?: return; if (!isOutputRecording) return; val bytes = ByteArray(count * 2); var p = 0
            for (i in 0 until count) { val v = buffer[i].toInt(); bytes[p++] = (v and 0xff).toByte(); bytes[p++] = ((v ushr 8) and 0xff).toByte() }
            try { writer.write(bytes); recordedPcmBytes += bytes.size.toLong() } catch (t: Throwable) { recordingStatus = "Recording write failed: ${t.message ?: "Unknown error"}" }
        }
    }

    fun stopOutputRecording(): Boolean {
        synchronized(recordingLock) {
            if (!isOutputRecording) return pendingRecordingFile?.exists() == true
            isOutputRecording = false; recordingTickerJob?.cancel(); recordingTickerJob = null; val writer = recordingWriter; recordingWriter = null
            try { writer?.let { writeWavHeader(it, recordedPcmBytes); it.fd.sync(); it.close() } } catch (t: Throwable) { recordingStatus = "Unable to finalize recording: ${t.message ?: "Unknown error"}" }
            pendingRecordingFile = recordingFile; recordingFile = null; recordingStatus = if (pendingRecordingFile?.exists() == true) "Choose a location and filename to save" else "Recording stopped"; return pendingRecordingFile?.exists() == true
        }
    }

    fun suggestedRecordingName(format: String = "WAV"): String {
        val ext = if (format.equals("MP3", ignoreCase = true)) "mp3" else "wav"
        return "DJ_Mic_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())}.$ext"
    }

    suspend fun savePendingRecording(uri: Uri, format: String = "WAV"): Boolean {
        val source = pendingRecordingFile ?: return false
        return try {
            val sourceToSave = if (format.equals("MP3", ignoreCase = true)) {
                val mp3 = File.createTempFile("dj_mic_", ".mp3", context.cacheDir)
                if (!encodeWavToMp3(source, mp3)) {
                    mp3.delete()
                    recordingStatus = "MP3 encoding is not available on this device"
                    return false
                }
                mp3
            } else source

            context.contentResolver.openOutputStream(uri)?.use { out ->
                sourceToSave.inputStream().use { it.copyTo(out) }
            } ?: return false

            if (sourceToSave != source) sourceToSave.delete()
            source.delete()
            pendingRecordingFile = null
            recordingStatus = "Recording saved successfully"
            true
        } catch (t: Throwable) {
            recordingStatus = "Save failed: ${t.message ?: "Unknown error"}"
            false
        }
    }

    private fun encodeWavToMp3(source: File, target: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        val encoder = try { MediaCodec.createEncoderByType("audio/mpeg") } catch (_: Throwable) { return false }
        var inputStream: FileInputStream? = null
        var outputStream: FileOutputStream? = null
        return try {
            val format = MediaFormat.createAudioFormat("audio/mpeg", sampleRate, 1)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 192000)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            inputStream = FileInputStream(source).also { it.skip(44) }
            outputStream = FileOutputStream(target)
            val info = MediaCodec.BufferInfo()
            val pcm = ByteArray(bufferSize * 2)
            var inputDone = false
            var outputDone = false
            var bytesSubmitted = 0L
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = encoder.dequeueInputBuffer(10000)
                    if (inIndex >= 0) {
                        val inBuffer = encoder.getInputBuffer(inIndex) ?: return false
                        inBuffer.clear()
                        val read = inputStream.read(pcm)
                        if (read < 0) {
                            encoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else if (read > 0) {
                            inBuffer.put(pcm, 0, read)
                            val ptsUs = bytesSubmitted * 1000000L / (sampleRate * 2L)
                            encoder.queueInputBuffer(inIndex, 0, read, ptsUs, 0)
                            bytesSubmitted += read.toLong()
                        }
                    }
                }

                when (val outIndex = encoder.dequeueOutputBuffer(info, 10000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED, MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outIndex >= 0) {
                        val outBuffer = encoder.getOutputBuffer(outIndex)
                        if (outBuffer != null && info.size > 0) {
                            outBuffer.position(info.offset)
                            outBuffer.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size)
                            outBuffer.get(bytes)
                            outputStream.write(bytes)
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    }
                }
            }
            true
        } catch (_: Throwable) {
            false
        } finally {
            try { inputStream?.close() } catch (_: Throwable) { }
            try { outputStream?.close() } catch (_: Throwable) { }
            try { encoder.stop() } catch (_: Throwable) { }
            try { encoder.release() } catch (_: Throwable) { }
        }
    }

    fun discardPendingRecording() { pendingRecordingFile?.delete(); pendingRecordingFile = null; recordingStatus = "Recording discarded" }


    @SuppressLint("MissingPermission")
    private fun applyInputRouting() {
        try {
            val record = audioRecord ?: return
            val device = selectedInputDevice
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val accepted = record.setPreferredDevice(device)
                if (!accepted && device != null) {
                    routingStatus = "تعذر توجيه الميكروفون إلى ${device.displayName()}"
                    return
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device?.isBluetoothSco() == true) {
                // Keep communication mode for SCO/HFP capture, but do not bind the output
                // to this same device. The output is routed independently below.
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            }
            applyOutputRouting()
            updateRoutingStatus()
        } catch (t: Throwable) {
            routingStatus = "تعذر تغيير مصدر الإدخال: ${t.message ?: "خطأ"}"
        }
    }

    private fun applyOutputRouting() {
        try {
            audioTrack?.let { track ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val accepted = track.setPreferredDevice(selectedOutputDevice)
                    if (!accepted && selectedOutputDevice != null) {
                        routingStatus = "تعذر توجيه الصوت إلى ${selectedOutputDevice?.displayName()}"
                        return
                    }
                }
            }

            AudioPlayerController.updateGlobalPreferredAudioDevice(selectedOutputDevice)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val inputNeedsCommunicationMode = selectedInputDevice?.isBluetoothSco() == true
                if (inputNeedsCommunicationMode) {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                } else if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) {
                    audioManager.mode = AudioManager.MODE_NORMAL
                    // Do not call clearCommunicationDevice(): this implementation no longer
                    // uses a global communication-device lock for independent routing.
                }
            }
            updateRoutingStatus()
        } catch (t: Throwable) {
            routingStatus = "تعذر تغيير مخرج الصوت: ${t.message ?: "خطأ"}"
        }
    }

    private fun updateRoutingStatus() {
        if (!isMicEnabled) return
        val actualInput = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) audioRecord?.routedDevice else null
        val actualOutput = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) audioTrack?.routedDevice else null
        val inputName = actualInput?.displayName() ?: selectedInputDevice?.displayName() ?: "تلقائي"
        val outputName = actualOutput?.displayName() ?: selectedOutputDevice?.displayName() ?: "تلقائي"
        val independent = selectedInputDevice != null && selectedOutputDevice != null && selectedInputDevice?.id != selectedOutputDevice?.id
        val suffix = if (independent) "  •  مستقل" else ""
        routingStatus = "الإدخال: $inputName  •  الإخراج: $outputName$suffix"
    }

    /** Apply an input selection immediately. If live monitoring is active, the recorder is restarted
     * so Android gets a fresh preferred input route instead of keeping the previous device. */
    fun selectInputDevice(device: AudioDeviceInfo?, coroutineScope: CoroutineScope) {
        selectedInputState = device
        if (isMicEnabled) {
            stopMic()
            startMic(coroutineScope)
        } else {
            routingStatus = if (device == null) "Input: System Default Mic" else "Input: ${device.displayName()}"
        }
    }

    /** Apply an output selection to both the live mic monitor and the Media3 music player. */
    fun selectOutputDevice(device: AudioDeviceInfo?) {
        selectedOutputState = device
        if (isMicEnabled) applyOutputRouting() else AudioPlayerController.updateGlobalPreferredAudioDevice(device)
        routingStatus = if (device == null) "Output: System Default" else "Output: ${device.displayName()}"
    }

    private fun stopMic() {
        isMicEnabled = false
        if (isOutputRecording) stopOutputRecording()
        stopMicForegroundService()
        recordingJob?.cancel()
        recordingJob = null
        try {
            echoCanceler?.enabled = false
            echoCanceler?.release()
            echoCanceler = null
        } catch (_: Throwable) { }
        try {
            noiseSuppressor?.enabled = false
            noiseSuppressor?.release()
            noiseSuppressor = null
        } catch (_: Throwable) { }
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Throwable) { }
        audioRecord = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Throwable) { }
        audioTrack = null

        // We do not own a global communication-device selection anymore; only reset the
        // communication mode used to make SCO/HFP capture available.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.mode = AudioManager.MODE_NORMAL
        } else {
            audioManager.mode = AudioManager.MODE_NORMAL
        }
        routingStatus = "تم إيقاف الميكروفون"
    }

    private fun AudioDeviceInfo.isBluetoothAudio(): Boolean = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> true
        else -> false
    }

    private fun AudioDeviceInfo.isBluetoothSco(): Boolean = type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO

    private fun AudioDeviceInfo.displayName(): String =
        productName?.toString()?.takeIf { it.isNotBlank() } ?: "Audio Device $id"

    companion object {
        private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 4301
    }
}
