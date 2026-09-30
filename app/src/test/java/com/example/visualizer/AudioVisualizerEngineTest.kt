package com.example.visualizer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioVisualizerEngineTest {

    @Test
    fun testEmptyAndInvalidAnalysisData() {
        val engine = AudioVisualizerEngine()
        val initial = engine.state.value

        assertEquals(0f, initial.bass, 0.0001f)
        assertEquals(0f, initial.mid, 0.0001f)
        assertEquals(0f, initial.treble, 0.0001f)
        assertEquals(0f, initial.energy, 0.0001f)
        assertEquals(0f, initial.peak, 0.0001f)
        assertEquals(0f, initial.beat, 0.0001f)
        assertEquals(32, initial.bands.size)
        assertEquals(64, initial.wave.size)
        assertFalse(initial.isPlaying)

        // Feed an empty buffer
        val emptyBuf = ByteBuffer.allocateDirect(0)
        engine.feedPcm(emptyBuf, 2, 44100)
        assertEquals(0f, engine.state.value.energy, 0.0001f)

        engine.release()
    }

    @Test
    fun testAudioProcessorConfiguration() {
        val engine = AudioVisualizerEngine()
        val processor = engine.audioProcessor

        // Valid 16-bit PCM format
        val validFormat = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        val result = processor.configure(validFormat)
        assertEquals(validFormat, result)
        assertTrue(processor.isActive)

        // Invalid encoding (e.g. invalid bit depth)
        val invalidFormat = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_INVALID)
        val invalidResult = processor.configure(invalidFormat)
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, invalidResult)
        assertFalse(processor.isActive)

        engine.release()
    }

    @Test
    fun testBandAggregationAndBassReaction() {
        val engine = AudioVisualizerEngine()
        engine.isPlaying = true

        val processor = engine.audioProcessor
        val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)

        // Generate 1024 samples of a low-frequency bass tone (100 Hz sine wave)
        val sampleCount = 1024
        val byteBuffer = ByteBuffer.allocateDirect(sampleCount * 2 * 2).order(ByteOrder.nativeOrder())
        val freq = 100.0 // 100 Hz bass
        for (i in 0 until sampleCount) {
            val angle = 2.0 * Math.PI * freq * i / 44100.0
            val sample = (sin(angle) * 28000.0).toInt().toShort()
            byteBuffer.putShort(sample) // Left
            byteBuffer.putShort(sample) // Right
        }
        byteBuffer.flip()

        processor.queueInput(byteBuffer)
        engine.processFrame()

        val state = engine.state.value
        assertEquals(32, state.bands.size)
        assertEquals(64, state.wave.size)

        // Bass and energy should be actively detected
        assertTrue("Energy should be detected (> 0.05)", state.energy > 0.05f)
        assertTrue("Peak should be detected (> 0.2)", state.peak > 0.2f)
        assertTrue("Bass should be detected (> 0.1)", state.bass > 0.1f)

        engine.release()
    }

    @Test
    fun testSmoothingAndDecayToZero() {
        val engine = AudioVisualizerEngine()
        engine.isPlaying = true

        val processor = engine.audioProcessor
        val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)

        // Feed a loud audio burst
        val sampleCount = 512
        val byteBuffer = ByteBuffer.allocateDirect(sampleCount * 2 * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until sampleCount) {
            val s = 25000.toShort()
            byteBuffer.putShort(s)
            byteBuffer.putShort(s)
        }
        byteBuffer.flip()
        processor.queueInput(byteBuffer)
        repeat(5) { engine.processFrame() }

        val peakEnergy = engine.state.value.energy
        assertTrue("Energy must be elevated after audio input ($peakEnergy)", peakEnergy > 0.01f)

        // Stop playback - engine should smoothly decay
        engine.isPlaying = false
        engine.decayToZero()

        val decayedEnergy = engine.state.value.energy
        assertTrue("Decayed energy ($decayedEnergy) should be less than peak energy ($peakEnergy)", decayedEnergy < peakEnergy)

        // Repeat decay steps to verify complete decay
        repeat(30) { engine.decayToZero() }
        assertEquals(0f, engine.state.value.energy, 0.0001f)
        assertEquals(0f, engine.state.value.bass, 0.0001f)
        assertEquals(0f, engine.state.value.beat, 0.0001f)

        engine.release()
    }

    @Test
    fun testLifecycleRelease() {
        val engine = AudioVisualizerEngine()
        engine.isPlaying = true

        engine.release()
        val state = engine.state.value

        assertEquals(0f, state.bass, 0.0001f)
        assertEquals(0f, state.energy, 0.0001f)
        assertEquals(32, state.bands.size)
    }
}
