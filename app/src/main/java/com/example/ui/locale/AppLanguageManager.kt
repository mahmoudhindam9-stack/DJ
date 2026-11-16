package com.example.ui.locale

import android.content.Context
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class LanguageMode(val code: String) {
    AUTO("auto"),
    ARABIC("ar"),
    ENGLISH("en");

    companion object {
        fun fromCode(code: String?): LanguageMode {
            return when (code?.lowercase()?.trim()) {
                "ar", "arabic" -> ARABIC
                "en", "english" -> ENGLISH
                else -> AUTO
            }
        }
    }
}

object AppLanguageManager {
    private const val PREFS_NAME = "app_language_prefs"
    private const val KEY_LANGUAGE_MODE = "language_mode"

    private val _languageMode = MutableStateFlow(LanguageMode.AUTO)
    val languageMode: StateFlow<LanguageMode> = _languageMode.asStateFlow()

    private val _isArabicFlow = MutableStateFlow(detectSystemArabic())
    val isArabicFlow: StateFlow<Boolean> = _isArabicFlow.asStateFlow()

    val isArabic: Boolean
        get() = _isArabicFlow.value

    fun detectSystemArabic(locale: Locale = Locale.getDefault()): Boolean {
        return locale.language.lowercase().startsWith("ar")
    }

    fun resolveIsArabic(mode: LanguageMode, systemLocale: Locale = Locale.getDefault()): Boolean {
        return when (mode) {
            LanguageMode.ARABIC -> true
            LanguageMode.ENGLISH -> false
            LanguageMode.AUTO -> detectSystemArabic(systemLocale)
        }
    }

    fun currentLayoutDirection(isAr: Boolean = _isArabicFlow.value): LayoutDirection {
        return if (isAr) LayoutDirection.Rtl else LayoutDirection.Ltr
    }

    fun init(context: Context, systemLocale: Locale = Locale.getDefault()) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedCode = prefs.getString(KEY_LANGUAGE_MODE, LanguageMode.AUTO.code)
        val mode = LanguageMode.fromCode(savedCode)
        _languageMode.value = mode
        _isArabicFlow.value = resolveIsArabic(mode, systemLocale)
    }

    fun setLanguageMode(context: Context, mode: LanguageMode, systemLocale: Locale = Locale.getDefault()) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE_MODE, mode.code).apply()
        _languageMode.value = mode
        _isArabicFlow.value = resolveIsArabic(mode, systemLocale)
    }
}
