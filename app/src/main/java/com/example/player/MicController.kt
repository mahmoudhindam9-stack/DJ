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
import androidx.compose.runtime.*
import kotlinx.coroutines.*

enum class MicFilter(val displayName: String) {
    NORMAL("عادي (طبيعي)"),
    STUDIO_REVERB("صدى استوديو (كاريوكي)"),
    CHIPMUNK("صوت كرتون (سنجاب)"),
    MONSTER("صوت عميق (وحش)"),
    ROBOT("صوت إلكتروني (روبوت)")
}

class MicController(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null

    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    var isMicEnabled by mutableStateOf(false)
        private set

    var micVolume by mutableStateOf(1.2f)
    var echoLevel by mutableStateOf(0.3f) // 0 to 1
    var currentFilter by mutableStateOf(MicFilter.STUDIO_REVERB)

    var inputDevices by mutableStateOf<List<AudioDeviceInfo>>(emptyList())
        private set
    var outputDevices by mutableStateOf<List<AudioDeviceInfo>>(emptyList())
        private set

    var selectedInputDevice by mutableStateOf<AudioDeviceInfo?>(null)
    var selectedOutputDevice by mutableStateOf<AudioDeviceInfo?>(null)

    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ) * 2

    init {
        refreshDevices()
    }

    fun refreshDevices() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
            outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
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
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && selectedInputDevice != null) {
                audioRecord?.preferredDevice = selectedInputDevice
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && selectedOutputDevice != null) {
                audioTrack?.preferredDevice = selectedOutputDevice
            }

            // Enable Acoustic Echo Cancellation and Noise Suppression to prevent feedback/howling
            val sessionId = audioRecord?.audioSessionId ?: 0
            if (sessionId != 0) {
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(sessionId)
                    echoCanceler?.enabled = true
                }
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(sessionId)
                    noiseSuppressor?.enabled = true
                }
            }

            audioRecord?.startRecording()
            audioTrack?.play()
            isMicEnabled = true

            recordingJob = coroutineScope.launch(Dispatchers.IO) {
                val buffer = ShortArray(bufferSize / 2)
                val echoBuffer = ShortArray(sampleRate) // 1 second echo buffer
                var echoIdx = 0

                while (isActive && isMicEnabled) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val activeFilter = currentFilter
                        val effEcho = if (activeFilter == MicFilter.STUDIO_REVERB) echoLevel.coerceAtLeast(0.4f) else echoLevel

                        for (i in 0 until read) {
                            var input = buffer[i].toFloat()

                            // Apply Mic Filters
                            when (activeFilter) {
                                MicFilter.CHIPMUNK -> {
                                    // Simple pitch shift simulation by multiplying index steps or scaling
                                    input *= (if (i % 2 == 0) 1.2f else 0.8f)
                                }
                                MicFilter.MONSTER -> {
                                    input *= 0.7f
                                }
                                MicFilter.ROBOT -> {
                                    val mod = if ((i / 20) % 2 == 0) 1f else 0.5f
                                    input *= mod
                                }
                                else -> {}
                            }

                            // Echo / Reverb delay calculation
                            val delaySamples = (sampleRate * 0.35).toInt()
                            val delayedIdx = (echoIdx - delaySamples + echoBuffer.size) % echoBuffer.size
                            val delayedSample = echoBuffer[delayedIdx]

                            val output = (input + (delayedSample * effEcho)) * micVolume
                            val clamped = output.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

                            buffer[i] = clamped
                            echoBuffer[echoIdx] = clamped
                            echoIdx = (echoIdx + 1) % echoBuffer.size
                        }
                        audioTrack?.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isMicEnabled = false
        }
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
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioTrack = null
    }
}
