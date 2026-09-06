from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
PLAYER = ROOT / "app/src/main/java/com/example/player"
EQ_CONTROLLER = PLAYER / "EqualizerController.kt"
AUDIO_CONTROLLER = PLAYER / "AudioPlayerController.kt"
DJ_DECK = PLAYER / "DJDeckController.kt"
MIC = PLAYER / "MicController.kt"
STUDIO = ROOT / "app/src/main/java/com/example/studio/MusicStudioController.kt"

# 1) Add the shared state + processors.
(PLAYER / "GlobalEqualizerState.kt").write_text(r'''
package com.example.player

/**
 * Authoritative EQ state shared by every audio output path in the app.
 * DJ decks, the normal player, online music, microphone monitor, sampler and
 * studio previews all read the same snapshot.
 */
object GlobalEqualizerState {
    private const val BAND_COUNT = 10

    @Volatile var enabled: Boolean = false
        private set
    @Volatile var levelsDb: FloatArray = FloatArray(BAND_COUNT)
        private set
    @Volatile var preampDb: Float = 0f
        private set
    @Volatile var version: Long = 0L
        private set

    @Synchronized
    fun update(levels: FloatArray, enabled: Boolean, preampDb: Float) {
        val next = FloatArray(BAND_COUNT)
        for (i in 0 until minOf(BAND_COUNT, levels.size)) {
            next[i] = levels[i].coerceIn(-12f, 12f)
        }
        levelsDb = next
        this.enabled = enabled
        this.preampDb = if (enabled) preampDb.coerceIn(0f, 12f) else 0f
        version++
    }
}
'''.strip() + "\n", encoding="utf-8")

(PLAYER / "GlobalEqualizerAudioProcessor.kt").write_text(r'''
package com.example.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * App-wide 10-band PCM EQ for every Media3/ExoPlayer audio path outside the
 * DJ decks. It follows the same band frequencies and safe gain range as the DJ EQ.
 */
class GlobalEqualizerAudioProcessor : AudioProcessor {
    companion object {
        private val FREQUENCIES = floatArrayOf(
            60f, 170f, 310f, 600f, 1000f,
            3000f, 6000f, 12000f, 14000f, 16000f
        )
        private const val Q = 1f
        private const val TRANSITION_FRAMES = 256
        private const val LIMITER_THRESHOLD = 0.82f
    }

    private fun createBank() = Array(2) { Array(10) { BiquadFilter() } }
    private var active = createBank()
    private var target = createBank()
    private var inputFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false
    private var sampleRate = 44100
    private var channelCount = 2
    private var appliedVersion = -1L
    private var transitionActive = false
    private var transitionPosition = TRANSITION_FRAMES

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (buffer.capacity() < size) {
            buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN)
        } else {
            buffer.clear()
        }
        outputBuffer = buffer
        return buffer
    }

    private fun configureBank(bank: Array<Array<BiquadFilter>>, levels: FloatArray) {
        for (ch in 0 until channelCount) {
            for (i in 0 until 10) {
                bank[ch][i].setPeakingEQ(
                    FREQUENCIES[i],
                    levels.getOrElse(i) { 0f },
                    Q,
                    sampleRate.toFloat()
                )
                bank[ch][i].resetState()
            }
        }
    }

    private fun syncGlobalState() {
        val version = GlobalEqualizerState.version
        if (version == appliedVersion) return
        configureBank(target, GlobalEqualizerState.levelsDb)
        transitionPosition = 0
        transitionActive = true
        appliedVersion = version
    }

    private fun applyEq(sample: Float, ch: Int, amount: Float): Float {
        var current = sample
        var next = sample
        for (i in 0 until 10) {
            current = active[ch][i].process(current)
            next = target[ch][i].process(next)
        }
        return current * (1f - amount) + next * amount
    }

    private fun preampGain(): Float =
        Math.pow(10.0, GlobalEqualizerState.preampDb.toDouble() / 20.0).toFloat()

    private fun softLimit(sample: Float): Float {
        val magnitude = abs(sample)
        if (magnitude <= LIMITER_THRESHOLD) return sample
        val excess = (magnitude - LIMITER_THRESHOLD) / (1f - LIMITER_THRESHOLD)
        val compressed = LIMITER_THRESHOLD + (1f - LIMITER_THRESHOLD) * tanh(excess)
        return if (sample < 0f) -compressed else compressed
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.sampleRate <= 0 ||
            inputAudioFormat.channelCount !in 1..2
        ) {
            inputFormat = AudioProcessor.AudioFormat.NOT_SET
            return AudioProcessor.AudioFormat.NOT_SET
        }
        inputFormat = inputAudioFormat
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        for (bank in arrayOf(active, target)) {
            for (ch in 0 until channelCount) {
                for (i in 0 until 10) {
                    bank[ch][i].setPeakingEQ(FREQUENCIES[i], 0f, Q, sampleRate.toFloat())
                    bank[ch][i].resetState()
                }
            }
        }
        appliedVersion = -1L
        transitionActive = false
        transitionPosition = TRANSITION_FRAMES
        return inputAudioFormat
    }

    override fun isActive(): Boolean = inputFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive()) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val bytes = inputBuffer.remaining()
        if (bytes <= 0) return

        if (!GlobalEqualizerState.enabled) {
            val output = replaceOutputBuffer(bytes)
            output.put(inputBuffer)
            output.flip()
            return
        }

        syncGlobalState()

        val output = replaceOutputBuffer(bytes)
        val frames = bytes / (2 * channelCount)
        val gain = preampGain()

        for (frame in 0 until frames) {
            val amount = if (transitionActive) {
                ((transitionPosition + 1).toFloat() / TRANSITION_FRAMES.toFloat()).coerceIn(0f, 1f)
            } else 1f

            for (ch in 0 until channelCount) {
                if (!inputBuffer.hasRemaining()) break
                var sample = inputBuffer.short.toFloat() / 32768f
                sample = applyEq(sample, ch, amount)
                sample = softLimit(sample * gain).coerceIn(-1f, 1f)
                output.putShort((sample * 32767f).roundToInt().toShort())
            }

            if (transitionActive) {
                transitionPosition++
                if (transitionPosition >= TRANSITION_FRAMES) {
                    val old = active
                    active = target
                    target = old
                    transitionActive = false
                    transitionPosition = TRANSITION_FRAMES
                }
            }
        }
        output.flip()
    }

    override fun queueEndOfStream() { inputEnded = true }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        for (bank in arrayOf(active, target)) {
            for (ch in 0 until channelCount) {
                for (i in 0 until 10) bank[ch][i].resetState()
            }
        }
        appliedVersion = -1L
        transitionActive = false
        transitionPosition = TRANSITION_FRAMES
    }

    override fun reset() {
        flush()
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
        sampleRate = 44100
        channelCount = 2
    }
}
'''.strip() + "\n", encoding="utf-8")

(PLAYER / "GlobalEqualizerPcmProcessor.kt").write_text(r'''
package com.example.player

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tanh

/** Same app-wide EQ for direct AudioTrack PCM paths that do not pass through Media3. */
class GlobalEqualizerPcmProcessor {
    companion object {
        private val FREQUENCIES = floatArrayOf(
            60f, 170f, 310f, 600f, 1000f,
            3000f, 6000f, 12000f, 14000f, 16000f
        )
        private const val Q = 1f
        private const val TRANSITION_FRAMES = 256
        private const val LIMITER_THRESHOLD = 0.82f
    }

    private fun createBank() = Array(2) { Array(10) { BiquadFilter() } }
    private var active = createBank()
    private var target = createBank()
    private var appliedVersion = -1L
    private var transitionActive = false
    private var transitionPosition = TRANSITION_FRAMES
    private var configuredSampleRate = 44100
    private var configuredChannels = 2

    private fun configureBank(bank: Array<Array<BiquadFilter>>, levels: FloatArray) {
        for (ch in 0 until configuredChannels) {
            for (i in 0 until 10) {
                bank[ch][i].setPeakingEQ(
                    FREQUENCIES[i],
                    levels.getOrElse(i) { 0f },
                    Q,
                    configuredSampleRate.toFloat()
                )
                bank[ch][i].resetState()
            }
        }
    }

    private fun sync(sampleRate: Int, channels: Int) {
        configuredSampleRate = sampleRate.coerceAtLeast(1)
        configuredChannels = channels.coerceIn(1, 2)
        val version = GlobalEqualizerState.version
        if (version == appliedVersion) return
        configureBank(target, GlobalEqualizerState.levelsDb)
        transitionPosition = 0
        transitionActive = true
        appliedVersion = version
    }

    private fun applyEq(sample: Float, channel: Int, amount: Float): Float {
        var current = sample
        var next = sample
        for (i in 0 until 10) {
            current = active[channel][i].process(current)
            next = target[channel][i].process(next)
        }
        return current * (1f - amount) + next * amount
    }

    private fun softLimit(sample: Float): Float {
        val magnitude = abs(sample)
        if (magnitude <= LIMITER_THRESHOLD) return sample
        val excess = (magnitude - LIMITER_THRESHOLD) / (1f - LIMITER_THRESHOLD)
        val compressed = LIMITER_THRESHOLD + (1f - LIMITER_THRESHOLD) * tanh(excess)
        return if (sample < 0f) -compressed else compressed
    }

    fun process(buffer: ShortArray, length: Int, sampleRate: Int, channels: Int) {
        if (!GlobalEqualizerState.enabled || length <= 0) return
        sync(sampleRate, channels)

        val gain = Math.pow(10.0, GlobalEqualizerState.preampDb.toDouble() / 20.0).toFloat()
        val frames = length / configuredChannels
        for (frame in 0 until frames) {
            val amount = if (transitionActive) {
                ((transitionPosition + 1).toFloat() / TRANSITION_FRAMES.toFloat()).coerceIn(0f, 1f)
            } else 1f
            for (ch in 0 until configuredChannels) {
                val index = frame * configuredChannels + ch
                var sample = buffer[index].toFloat() / 32768f
                sample = applyEq(sample, ch, amount)
                sample = softLimit(sample * gain).coerceIn(-1f, 1f)
                buffer[index] = (sample * 32767f).roundToInt().toShort()
            }
            if (transitionActive) {
                transitionPosition++
                if (transitionPosition >= TRANSITION_FRAMES) {
                    val old = active
                    active = target
                    target = old
                    transitionActive = false
                    transitionPosition = TRANSITION_FRAMES
                }
            }
        }
    }

    fun reset() {
        appliedVersion = -1L
        transitionActive = false
        transitionPosition = TRANSITION_FRAMES
        for (bank in arrayOf(active, target)) {
            for (ch in 0 until configuredChannels) {
                for (i in 0 until 10) bank[ch][i].resetState()
            }
        }
    }
}
'''.strip() + "\n", encoding="utf-8")

# 2) Make the visible EQ controller publish one authoritative global snapshot.
text = EQ_CONTROLLER.read_text(encoding="utf-8")
if "GlobalEqualizerState.update(levels, enabled, sharedPreamp)" not in text:
    old = "        DeckFxAudioProcessor.setGlobalPreampDb(sharedPreamp)\n"
    new = old + "        GlobalEqualizerState.update(levels, enabled, sharedPreamp)\n"
    if text.count(old) != 1:
        raise SystemExit("Expected EqualizerController broadcast preamp line exactly once")
    text = text.replace(old, new, 1)

if "GlobalEqualizerState.update(\n                bands.map" not in text:
    load_marker = """            if (isEnabled) DeckFxAudioProcessor.setGlobalPreampDb(preampDb) else DeckFxAudioProcessor.setGlobalPreampDb(0f)
            syncQuickFromBands()
"""
    load_replacement = """            if (isEnabled) DeckFxAudioProcessor.setGlobalPreampDb(preampDb) else DeckFxAudioProcessor.setGlobalPreampDb(0f)
            GlobalEqualizerState.update(
                bands.map { it.currentLevelDb.toFloat() }.toFloatArray(),
                isEnabled,
                if (isEnabled) preampDb else 0f
            )
            syncQuickFromBands()
"""
    if text.count(load_marker) != 1:
        raise SystemExit("EqualizerController load-state marker not found exactly once")
    text = text.replace(load_marker, load_replacement, 1)
EQ_CONTROLLER.write_text(text, encoding="utf-8")

# 3) Put the global EQ into the normal ExoPlayer used by Player/Online Music/etc.
text = AUDIO_CONTROLLER.read_text(encoding="utf-8")
if "GlobalEqualizerAudioProcessor()" not in text:
    if "import androidx.media3.exoplayer.DefaultRenderersFactory" not in text:
        marker = "import androidx.media3.exoplayer.ExoPlayer\n"
        if text.count(marker) != 1:
            raise SystemExit("AudioPlayerController ExoPlayer import marker not found exactly once")
        text = text.replace(
            marker,
            marker +
            "import androidx.media3.exoplayer.DefaultRenderersFactory\n"
            "import androidx.media3.exoplayer.audio.AudioSink\n"
            "import androidx.media3.exoplayer.audio.DefaultAudioSink\n",
            1
        )
    old = "class AudioPlayerController(private val context: Context) {\n    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()\n"
    new = """@OptIn(UnstableApi::class)
class AudioPlayerController(private val context: Context) {
    private val renderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): AudioSink {
            return DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(GlobalEqualizerAudioProcessor()))
                .build()
        }
    }

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context, renderersFactory).build()
"""
    if old not in text:
        raise SystemExit("AudioPlayerController player construction block not found")
    text = text.replace(old, new, 1)
    AUDIO_CONTROLLER.write_text(text, encoding="utf-8")

# 4) Direct sampler AudioTrack: apply the same EQ to generated one-shots.
text = DJ_DECK.read_text(encoding="utf-8")
if "private val globalEq = GlobalEqualizerPcmProcessor()" not in text:
    marker = "class DJSoundPlayer(private val context: Context) {\n"
    if text.count(marker) != 1:
        raise SystemExit("DJSoundPlayer class marker not found exactly once")
    text = text.replace(marker, marker + "    private val globalEq = GlobalEqualizerPcmProcessor()\n", 1)
    old = "                audioTrack.write(buffer,0,buffer.size); audioTrack.play();"
    new = "                globalEq.process(buffer, buffer.size, sampleRate, 1); audioTrack.write(buffer,0,buffer.size); audioTrack.play();"
    if text.count(old) != 1:
        raise SystemExit("DJSoundPlayer write marker not found exactly once")
    text = text.replace(old, new, 1)
    DJ_DECK.write_text(text, encoding="utf-8")

# 5) Microphone monitor: apply the EQ after mic FX and before the output AudioTrack.
text = MIC.read_text(encoding="utf-8")
if "private val globalEq = GlobalEqualizerPcmProcessor()" not in text:
    marker = "class MicController(private val context: Context) {\n"
    if text.count(marker) != 1:
        raise SystemExit("MicController class marker not found exactly once")
    text = text.replace(marker, marker + "    private val globalEq = GlobalEqualizerPcmProcessor()\n", 1)
    old = "                    audioTrack?.write(buffer, 0, read)\n"
    new = "                    globalEq.process(buffer, read, sampleRate, 1)\n                    audioTrack?.write(buffer, 0, read)\n"
    if text.count(old) != 1:
        raise SystemExit("Mic AudioTrack write marker not found exactly once")
    text = text.replace(old, new, 1)
    MIC.write_text(text, encoding="utf-8")

# 6) Studio preview + sequencer playback: same global EQ on both direct PCM paths.
text = STUDIO.read_text(encoding="utf-8")
if "private val previewEq = com.example.player.GlobalEqualizerPcmProcessor()" not in text:
    marker = "class MusicStudioController(private val context: Context) {\n"
    if text.count(marker) != 1:
        raise SystemExit("MusicStudioController class marker not found exactly once")
    text = text.replace(marker, marker + "    private val previewEq = com.example.player.GlobalEqualizerPcmProcessor()\n    private val playbackEq = com.example.player.GlobalEqualizerPcmProcessor()\n", 1)
    old = "                    previewTrack?.write(pcm, 0, pcm.size)\n"
    new = "                    previewEq.process(pcm, pcm.size, sampleRate, 2)\n                    previewTrack?.write(pcm, 0, pcm.size)\n"
    if text.count(old) != 1:
        raise SystemExit("Studio preview write marker not found exactly once")
    text = text.replace(old, new, 1)
    playback_marker = "                    track.write(buffer, 0, buffer.size)\n"
    playback_replacement = "                    playbackEq.process(buffer, buffer.size, sampleRate, 2)\n                    track.write(buffer, 0, buffer.size)\n"
    if text.count(playback_marker) != 1:
        raise SystemExit("Studio playback write marker not found exactly once")
    text = text.replace(playback_marker, playback_replacement, 1)
    STUDIO.write_text(text, encoding="utf-8")

print("Global app-wide EQ routing applied.")
