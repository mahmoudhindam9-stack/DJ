package com.example.tutorial

import android.content.Context
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

enum class TutorialStep(val title: String, val message: String, val tabRoute: String) {
    NONE("", "", ""),
    MAIN_MENU_BUTTON("Main Menu", "Tap the three-dot menu to access\nMusic Import, Updates, and more.", "player"),
    MENU_SCAN("Scan Device Music", "Bring all your MP3 files from your device.", "player"),
    MENU_ADD("Add Audio Files", "Add one exact song from your device.", "player"),
    MENU_IMPORT("Import Music", "Choose exactly what you want to play individually.", "player"),
    MENU_UPDATE("Check for Updates", "Always check for updates to get the latest features and fixes.", "player"),
    DJ_LOAD("Load Track", "Bring a song to the deck and start your party.", "dj"),
    DJ_FX("DJ FX", "Shape and manipulate your track with live DJ effects.", "dj"),
    DJ_SAMPLER("FX Sampler", "Trigger sound effects instantly during your mix.", "dj"),
    EQ_PRESETS("Presets", "Quickly switch between ready-made sound profiles.", "eq"),
    EQ_FREQUENCIES("Frequencies", "Shape individual frequency bands to fine-tune your sound.", "eq"),
    EQ_BASS_TREBLE("Bass & Treble Boost", "Increase low or high frequencies independently.", "eq"),
    EQ_PREAMP("Preamp Gain", "Control the overall signal level before output.", "eq"),
    MIC_CONTROLS("Microphone", "Toggle the mic, adjust volume, echo, and reverb.", "mic"),
    RADIO_STATIONS("Radio Stations", "Browse and select available stations.", "radio"),
    RADIO_PLAY("Play", "Start the selected station.", "radio"),
    RADIO_FAVORITES("Favorites", "Save your preferred stations.", "radio"),
    ONLINE_SEARCH("Search", "Find online music.", "online"),
    ONLINE_SELECT("Select", "Choose songs individually.", "online"),
    ONLINE_QUEUE("Add to Queue", "Send selected songs to the existing playback queue.", "online"),
    ONLINE_DOWNLOAD("Download", "Download eligible online songs using the existing download system.", "online"),
    
    // Update steps
    UPDATE_NEW_THEMES("New Themes", "Choose from 8 visual themes instantly.", "player")
}

object TutorialManager {
    private val PREFS = "tutorial_prefs"
    private val KEY_COMPLETED = "tutorial_completed"
    private val KEY_LAST_VERSION = "last_seen_version"

    private val _currentStep = MutableStateFlow(TutorialStep.NONE)
    val currentStep: StateFlow<TutorialStep> = _currentStep.asStateFlow()

    private var activeTour = emptyList<TutorialStep>()
    private var currentIndex = -1
    private var isUpdate = false

    val targets = mutableMapOf<TutorialStep, Rect>()

    fun init(context: Context, currentVersion: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val completed = prefs.getBoolean(KEY_COMPLETED, false)
        val lastVersion = prefs.getString(KEY_LAST_VERSION, "") ?: ""

        if (!completed) {
            startFirstLaunchTour()
        } else if (lastVersion != currentVersion && lastVersion.isNotEmpty()) {
            startUpdateTour()
        }
    }

    private fun startFirstLaunchTour() {
        activeTour = listOf(
            TutorialStep.MAIN_MENU_BUTTON,
            TutorialStep.MENU_SCAN,
            TutorialStep.MENU_ADD,
            TutorialStep.MENU_IMPORT,
            TutorialStep.MENU_UPDATE,
            TutorialStep.DJ_LOAD,
            TutorialStep.DJ_FX,
            TutorialStep.DJ_SAMPLER,
            TutorialStep.EQ_PRESETS,
            TutorialStep.EQ_FREQUENCIES,
            TutorialStep.EQ_BASS_TREBLE,
            TutorialStep.EQ_PREAMP,
            TutorialStep.MIC_CONTROLS,
            TutorialStep.RADIO_STATIONS,
            TutorialStep.RADIO_PLAY,
            TutorialStep.RADIO_FAVORITES,
            TutorialStep.ONLINE_SEARCH,
            TutorialStep.ONLINE_SELECT,
            TutorialStep.ONLINE_QUEUE,
            TutorialStep.ONLINE_DOWNLOAD
        )
        currentIndex = 0
        isUpdate = false
        _currentStep.value = activeTour[currentIndex]
    }

    private fun startUpdateTour() {
        activeTour = listOf(
            TutorialStep.UPDATE_NEW_THEMES
        )
        currentIndex = 0
        isUpdate = true
        _currentStep.value = activeTour[currentIndex]
    }

    fun next(context: Context, currentVersion: String) {
        if (currentIndex < activeTour.size - 1) {
            currentIndex++
            _currentStep.value = activeTour[currentIndex]
        } else {
            end(context, currentVersion)
        }
    }

    fun end(context: Context, currentVersion: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_COMPLETED, true)
            .putString(KEY_LAST_VERSION, currentVersion)
            .apply()
        
        activeTour = emptyList()
        currentIndex = -1
        _currentStep.value = TutorialStep.NONE
    }

    fun registerTarget(step: TutorialStep, rect: Rect) {
        targets[step] = rect
    }

    fun getTargetRect(step: TutorialStep): Rect? = targets[step]
    
    fun isUpdateTour() = isUpdate
}

fun Modifier.tutorialTarget(step: TutorialStep): Modifier = composed {
    this.onGloballyPositioned { coordinates ->
        TutorialManager.registerTarget(step, coordinates.boundsInRoot())
    }
}
