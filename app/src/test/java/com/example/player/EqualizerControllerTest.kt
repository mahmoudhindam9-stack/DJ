package com.example.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EqualizerControllerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("quick_eq", Context.MODE_PRIVATE).edit().clear().commit()
        EqualizerController.resetInstanceForTests()
    }

    @After
    fun tearDown() {
        EqualizerController.resetInstanceForTests()
    }

    @Test
    fun `fresh install enables equalizer by default and persists toggle state without zeroing preamp`() {
        val initial = EqualizerController.obtain(context)
        assertTrue("EQ should default to enabled on fresh install", initial.isEnabled)

        initial.updatePreampDb(6.5f)
        initial.updateBandLevel(0, 7)
        assertEquals(6.5f, initial.preampDb, 0.01f)
        assertEquals(7, initial.bands[0].currentLevelDb)

        // Toggle EQ off — UI preamp setting must be preserved while DSP sees disabled
        initial.toggleEnable()
        assertFalse(initial.isEnabled)
        assertEquals(6.5f, initial.preampDb, 0.01f)

        // Simulate cold restart by clearing in-memory singleton and re-obtaining from SharedPreferences
        EqualizerController.resetInstanceForTests()
        val restored = EqualizerController.obtain(context)
        assertFalse("Persisted disabled EQ state must survive restart", restored.isEnabled)
        assertEquals(6.5f, restored.preampDb, 0.01f)
        assertEquals(7, restored.bands[0].currentLevelDb)

        // Re-enable and restart again
        restored.toggleEnable()
        assertTrue(restored.isEnabled)
        EqualizerController.resetInstanceForTests()
        val restoredEnabled = EqualizerController.obtain(context)
        assertTrue(restoredEnabled.isEnabled)
        assertEquals(6.5f, restoredEnabled.preampDb, 0.01f)
    }

    @Test
    fun `custom preset save load update delete persists across restarts and protects built-in presets`() {
        val eq = EqualizerController.obtain(context)

        // Built-in protection
        assertTrue(eq.saveCustomPreset("Rock").isFailure)
        assertTrue(eq.saveCustomPreset("   ").isFailure)
        assertTrue(eq.updateCustomPreset("Jazz").isFailure)
        assertFalse(eq.deleteCustomPreset("Flat"))

        // Configure custom curve and save
        eq.updateBandLevel(0, 8)
        eq.updateBandLevel(4, -3)
        eq.updatePreampDb(4.0f)
        eq.updateBassBoost(0.75f)
        eq.updateTrebleBoost(0.25f)

        val saveRes = eq.saveCustomPreset("Car Bass")
        assertTrue(saveRes.isSuccess)
        assertEquals("Car Bass", eq.selectedPreset)
        assertEquals(1, eq.customPresets.size)

        // Switch away to Flat and then reload custom preset
        eq.applyPreset("Flat")
        assertEquals("Flat", eq.selectedPreset)
        assertEquals(0, eq.bands[0].currentLevelDb)

        eq.applyPreset("Car Bass")
        assertEquals("Car Bass", eq.selectedPreset)
        assertEquals(8, eq.bands[0].currentLevelDb)
        assertEquals(-3, eq.bands[4].currentLevelDb)
        assertEquals(4.0f, eq.preampDb, 0.01f)
        assertEquals(0.75f, eq.bassBoostLevel, 0.01f)
        assertEquals(0.25f, eq.trebleBoostLevel, 0.01f)

        // Modify sliders while editing Car Bass and update it
        eq.updateBandLevel(0, 10)
        assertEquals("Custom", eq.selectedPreset)
        assertEquals("Car Bass", eq.editingCustomPresetName)
        assertTrue(eq.updateCustomPreset("Car Bass").isSuccess)

        // Simulate app restart and verify persistence
        EqualizerController.resetInstanceForTests()
        val reloaded = EqualizerController.obtain(context)
        assertEquals(1, reloaded.customPresets.size)
        assertEquals("Car Bass", reloaded.selectedPreset)
        assertEquals(10, reloaded.bands[0].currentLevelDb)
        assertEquals(-3, reloaded.bands[4].currentLevelDb)
        assertEquals(4.0f, reloaded.preampDb, 0.01f)

        // Delete custom preset and verify cleanup persists
        assertTrue(reloaded.deleteCustomPreset("Car Bass"))
        assertTrue(reloaded.customPresets.isEmpty())
        assertEquals("Custom", reloaded.selectedPreset)

        EqualizerController.resetInstanceForTests()
        val afterDelete = EqualizerController.obtain(context)
        assertTrue(afterDelete.customPresets.isEmpty())
    }
}
