package com.example.player

import android.annotation.SuppressLint
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

enum class MicFilter(val displayName: String) {
    NORMAL("عادي (طبيعي)"),
    STUDIO_REVERB("صدى استوديو (كاريوكي)"),
    CHIPMUNK("صوت كرتون (سنجاب)"),
    MONSTER("صوت عميق (وحش)"),
    ROBOT("صوت إلكتروني (روبوت)")
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

    var inputDevices by mutableStateOf<List<AudioDeviceInfo>>(emptyList())
        private set
    var outputDevices by mutableStateOf<List<AudioDeviceInfo>>(emptyList())
        private set

    var selectedInputDevice by mutableStateOf<AudioDeviceInfo?>(null)
        private set
    var selectedOutputDevice by mutableStateOf<AudioDeviceInfo?>(null)
        private set

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
        refreshDevices()
    }

    /**
     * Refresh the devices Android currently exposes to apps.
     * The app can route to already-connected devices; pairing/connecting the
     * Bluetooth peripherals is still controlled by the Android Bluetooth UI.
     */
    @SuppressLint("MissingPermission")
    fun refreshDevices() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                inputDevices = audioManager
                    .getDevices(AudioManager.GET_DEVICES_INPUTS)
                    .filter { it.isSource }
                    .sortedWith(compareBy({ !it.isBluetoothAudio }, { it.productName?.toString() ?: "" }))

                outputDevices = audioManager
                    .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .filter { it.isSink }
                    .sortedWith(compareBy({ !it.isBluetoothAudio }, { it.productName?.toString() ?: "" }))
            }
        } catch (t: Throwable) {
            routingStatus = "تعذر قراءة أجهزة الصوت: ${t.message ?: "خطأ غير معروف"}"
        }
    }

    fun setInputDevice(device: AudioDeviceInfo?) {
        selectedInputDevice = device
        routingStatus = if (device == null) {
            "مصدر الإدخال: تلقائي"
        } else {
            "مصدر الإدخال: ${device.displayName()}"
        }

        if (isMicEnabled) {
            applyInputRouting()
        }
    }

    fun setOutputDevice(device: AudioDeviceInfo?) {
        selectedOutputDevice = device
        routingStatus = if (device == null) {
            "مخرج الصوت: تلقائي"
        } else {
            "مخرج الصوت: ${device.displayName()}"
        }

        if (isMicEnabled) {
            applyOutputRouting()
        }
    }

    @SuppressLint("MissingPermission")
    fun toggleMic(enabled: Boolean, coroutineScope: CoroutineScope) {
        if (enabled) {
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

            // Bluetooth HFP microphones are communication devices. Using
            // VOICE_COMMUNICATION allows Android to expose the headset mic.
            val audioSource = if (useBluetoothHfp) {
                MediaRecorder.AudioSource.VOICE_COMMUNICATION
            } else {
                MediaRecorder.AudioSource.MIC
            }

            audioRecord = AudioRecord(
                audioSource,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioRecord?.setPreferredDevice(inputDevice)
            }

            // Do not globally force the output to the microphone headset.
            // A separate AudioTrack can use selectedOutputDevice when the OS
            // exposes independent input/output routes.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useBluetoothHfp) {
                // Communication mode is needed for classic Bluetooth HFP input.
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

                // Only request the HFP device as the communication endpoint when
                // the selected output is the same device. This avoids overriding
                // a separately selected Bluetooth speaker whenever the platform
                // permits independent routes.
                if (selectedOutputDevice?.id == inputDevice?.id) {
                    audioManager.setCommunicationDevice(inputDevice)
                }
            }

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioTrack?.setPreferredDevice(selectedOutputDevice)
            }

            val sessionId = audioRecord?.audioSessionId ?: 0
            if (sessionId != 0) {
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                        enabled = true
                    }
                }
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                        enabled = true
                    }
                }
            }

            audioRecord?.startRecording()
            if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("AudioRecord failed to start")
            }

            audioTrack?.play()
            isMicEnabled = true
            updateRoutingStatus()

            recordingJob = coroutineScope.launch(Dispatchers.IO) {
                val buffer = ShortArray(bufferSize / 2)
                val echoBuffer = ShortArray(sampleRate)
                var echoIdx = 0

                while (isActive && isMicEnabled) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read <= 0) continue

                    val activeFilter = currentFilter
                    val effEcho = if (activeFilter == MicFilter.STUDIO_REVERB) {
                        echoLevel.coerceAtLeast(0.4f)
                    } else {
                        echoLevel
                    }

                    for (i in 0 until read) {
                        var input = buffer[i].toFloat()

                        when (activeFilter) {
                            MicFilter.CHIPMUNK -> input *= if (i % 2 == 0) 1.2f else 0.8f
                            MicFilter.MONSTER -> input *= 0.7f
                            MicFilter.ROBOT -> input *= if ((i / 20) % 2 == 0) 1f else 0.5f
                            else -> Unit
                        }

                        val delaySamples = (sampleRate * 0.35).toInt()
                        val delayedIdx = (echoIdx - delaySamples + echoBuffer.size) % echoBuffer.size
                        val delayedSample = echoBuffer[delayedIdx]

                        val output = (input + delayedSample * effEcho) * micVolume
                        val clamped = output
                            .coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
                            .toInt()
                            .toShort()

                        buffer[i] = clamped
                        echoBuffer[echoIdx] = clamped
                        echoIdx = (echoIdx + 1) % echoBuffer.size
                    }

                    audioTrack?.write(buffer, 0, read)
                }
            }
        } catch (t: Throwable) {
            routingStatus = "فشل تشغيل الميكروفون: ${t.message ?: "خطأ غير معروف"}"
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
            val track = audioTrack ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val accepted = track.setPreferredDevice(selectedOutputDevice)
                if (!accepted && selectedOutputDevice != null) {
                    routingStatus = "تعذر توجيه الصوت إلى ${selectedOutputDevice?.displayName()}"
                    return
                }
            }

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

    @SuppressLint("MissingPermission")
    private fun updateRoutingStatus() {
        if (!isMicEnabled) return

        val actualInput = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioRecord?.routedDevice
        } else null
        val actualOutput = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioTrack?.routedDevice
        } else null

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
            noiseSuppressor?.enabled = false
            noiseSuppressor?.release()
            noiseSuppressor = null
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        audioTrack = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.clearCommunicationDevice()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
        audioManager.mode = AudioManager.MODE_NORMAL
        routingStatus = "تم إيقاف الميكروفون"
    }

    private fun AudioDeviceInfo.isBluetoothAudio(): Boolean = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> true
        else -> false
    }

    private fun AudioDeviceInfo.isBluetoothSco(): Boolean =
        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO

    private fun AudioDeviceInfo.displayName(): String =
        productName?.toString()?.takeIf { it.isNotBlank() } ?: "Audio Device $id"
}
