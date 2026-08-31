// KARAOKE_DSP_V2
package com.example.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.sin


enum class MicFilter(val displayName: String) {
    NORMAL("Clean"),
    STUDIO_REVERB("Studio Reverb"),
    CHIPMUNK("Chipmunk"),
    MONSTER("Monster"),
    ROBOT("Robot")
}

enum class BeatFxDivision(val displayName: String, val beats: Float) {
    HALF("1/2 Beat", 0.5f),
    QUARTER("1/4 Beat", 0.25f),
    THREE_QUARTER("3/4 Beat", 0.75f),
    ONE("1 Beat", 1f)
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
    var beatFxEnabled by mutableStateOf(false)
    var reverbLevel by mutableStateOf(0.28f)
    var flangerMix by mutableStateOf(0.35f)
    var filterMix by mutableStateOf(0.55f)
    var bpm by mutableStateOf(120f)
    var beatFxDivision by mutableStateOf(BeatFxDivision.QUARTER)

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
                routingStatus = "اسمح للتطبيق بالوصول إلى Bluetooth ثم اضغط تحديث"
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                    .filter { it.isSource }
                    .sortedWith(compareBy({ !it.isBluetoothAudio() }, { it.productName?.toString() ?: "" }))

                outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .filter { it.isSink }
                    .sortedWith(compareBy({ !it.isBluetoothAudio() }, { it.productName?.toString() ?: "" }))

                routingStatus = if (inputDevices.isEmpty() && outputDevices.isEmpty()) {
                    "لم يجد Android أجهزة صوت متصلة"
                } else {
                    "تم تحديث أجهزة الصوت"
                }
            }
        } catch (t: Throwable) {
            routingStatus = "تعذر قراءة أجهزة الصوت: ${t.message ?: "خطأ غير معروف"}"
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
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                if (selectedOutputDevice?.id == device.id) {
                    audioManager.setCommunicationDevice(device)
                }
            }
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
                val input = selectedInputDevice
                if (input?.isBluetoothSco() == true && selectedOutputDevice?.id == input.id) {
                    audioManager.setCommunicationDevice(input)
                } else if (selectedOutputDevice == null && audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) {
                    audioManager.clearCommunicationDevice()
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
        routingStatus = "الإدخال: $inputName  •  الإخراج: $outputName"
    }

    private fun stopMic() {
        isMicEnabled = false
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try { audioManager.clearCommunicationDevice() } catch (_: Throwable) { }
        }
        audioManager.mode = AudioManager.MODE_NORMAL
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
