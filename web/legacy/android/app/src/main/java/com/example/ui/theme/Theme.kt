package com.example.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppThemeOption(val displayName: String) {
    SYSTEM("System Default"),
    DARK("Dark Mode"),
    LIGHT("Light Mode"),
    DJ_BLUE("DJ Blue"),
    MIDNIGHT_PURPLE("Midnight Purple"),
    GOLD_PREMIUM("Gold Premium"),
    NEON_GREEN("Neon Green"),
    CRIMSON_RED("Crimson Red"),
    CYBER_CYAN("Cyber Cyan"),
    LIGHT_DJ_BLUE("Light DJ Blue"),
    LIGHT_MIDNIGHT_PURPLE("Light Midnight Purple"),
    LIGHT_GOLD_PREMIUM("Light Gold Premium"),
    LIGHT_NEON_GREEN("Light Neon Green"),
    LIGHT_CRIMSON_RED("Light Crimson Red"),
    LIGHT_CYBER_CYAN("Light Cyber Cyan")
}

object ThemeManager {
    private const val PREFS = "app_theme_prefs"
    private const val KEY_THEME = "selected_theme"
    
    private val _currentTheme = MutableStateFlow(AppThemeOption.SYSTEM)
    val currentTheme: StateFlow<AppThemeOption> = _currentTheme

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_THEME, AppThemeOption.SYSTEM.name) ?: AppThemeOption.SYSTEM.name
        _currentTheme.value = runCatching { AppThemeOption.valueOf(saved) }.getOrDefault(AppThemeOption.SYSTEM)
    }

    fun setTheme(context: Context, theme: AppThemeOption) {
        _currentTheme.value = theme
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme.name)
            .apply()
    }
}

private val DarkColorScheme = darkColorScheme(
    primary = DjPrimaryDark,
    secondary = DjSecondaryDark,
    tertiary = Color(0xFF00E676), // Neon Green for FX/Active states
    background = DjBackgroundDark,
    surface = DjSurfaceDark,
    surfaceVariant = DjSurfaceVariantDark,
    outline = DjOutlineDark,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFFA0AAB5),
    error = DjError
)

private val LightColorScheme = lightColorScheme(
    primary = DjPrimaryLight,
    secondary = DjSecondaryLight,
    tertiary = Color(0xFF00C853),
    background = DjBackgroundLight,
    surface = DjSurfaceLight,
    surfaceVariant = DjSurfaceVariantLight,
    outline = DjOutlineLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF222222),
    onSurfaceVariant = Color(0xFF555555),
    error = DjError
)

private val DjBlueColorScheme = darkColorScheme(
    primary = Color(0xFF2962FF),
    secondary = Color(0xFF00B0FF),
    tertiary = Color(0xFF00E5FF),
    background = Color(0xFF0D1117),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF21262D),
    outline = Color(0xFF30363D),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color(0xFFC9D1D9),
    onSurfaceVariant = Color(0xFF8B949E),
    error = DjError
)

private val MidnightPurpleColorScheme = darkColorScheme(
    primary = Color(0xFFAA00FF),
    secondary = Color(0xFFD500F9),
    tertiary = Color(0xFFFF00FF),
    background = Color(0xFF090014),
    surface = Color(0xFF130024),
    surfaceVariant = Color(0xFF260042),
    outline = Color(0xFF3B0061),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color(0xFFE5CCFF),
    onSurfaceVariant = Color(0xFFC499FF),
    error = DjError
)

private val GoldPremiumColorScheme = darkColorScheme(
    primary = Color(0xFFFFD700),
    secondary = Color(0xFFFFC400),
    tertiary = Color(0xFFFFEA00),
    background = Color(0xFF141100),
    surface = Color(0xFF241D00),
    surfaceVariant = Color(0xFF3B3000),
    outline = Color(0xFF5C4D00),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFFFFDF5),
    onSurface = Color(0xFFFFEFA6),
    onSurfaceVariant = Color(0xFFE5D073),
    error = DjError
)

private val NeonGreenColorScheme = darkColorScheme(
    primary = Color(0xFF00FF00),
    secondary = Color(0xFF00E676),
    tertiary = Color(0xFF69F0AE),
    background = Color(0xFF001405),
    surface = Color(0xFF00240A),
    surfaceVariant = Color(0xFF004013),
    outline = Color(0xFF00611E),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color(0xFFB3FFC2),
    onSurfaceVariant = Color(0xFF80FF99),
    error = DjError
)

private val CrimsonRedColorScheme = darkColorScheme(
    primary = Color(0xFFFF0000),
    secondary = Color(0xFFFF1744),
    tertiary = Color(0xFFFF5252),
    background = Color(0xFF1A0000),
    surface = Color(0xFF2E0000),
    surfaceVariant = Color(0xFF4D0000),
    outline = Color(0xFF750000),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color(0xFFFFCCCC),
    onSurfaceVariant = Color(0xFFFF9999),
    error = DjError
)

private val CyberCyanColorScheme = darkColorScheme(
    primary = Color(0xFF00FFFF),
    secondary = Color(0xFF00E5FF),
    tertiary = Color(0xFF18FFFF),
    background = Color(0xFF00161A),
    surface = Color(0xFF00272E),
    surfaceVariant = Color(0xFF00434D),
    outline = Color(0xFF006775),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color(0xFFCCFFFF),
    onSurfaceVariant = Color(0xFF99FFFF),
    error = DjError
)

private val LightDjBlueColorScheme = lightColorScheme(
    primary = Color(0xFF2962FF),
    secondary = Color(0xFF00B0FF),
    tertiary = Color(0xFF00E5FF),
    background = Color(0xFFF0F4F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE1E8F0),
    outline = Color(0xFFB0BEC5),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF222222),
    onSurfaceVariant = Color(0xFF555555),
    error = DjError
)

private val LightMidnightPurpleColorScheme = lightColorScheme(
    primary = Color(0xFFAA00FF),
    secondary = Color(0xFFD500F9),
    tertiary = Color(0xFFFF00FF),
    background = Color(0xFFF8F0FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0E1F5),
    outline = Color(0xFFD1B3E0),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF222222),
    onSurfaceVariant = Color(0xFF555555),
    error = DjError
)

private val LightGoldPremiumColorScheme = lightColorScheme(
    primary = Color(0xFFD4AF37),
    secondary = Color(0xFFFFC400),
    tertiary = Color(0xFFFFEA00),
    background = Color(0xFFFCFAF2),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF7F1D7),
    outline = Color(0xFFD6C881),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF222222),
    onSurfaceVariant = Color(0xFF555555),
    error = DjError
)

private val LightNeonGreenColorScheme = lightColorScheme(
    primary = Color(0xFF00C853),
    secondary = Color(0xFF00E676),
    tertiary = Color(0xFF69F0AE),
    background = Color(0xFFF0FAF2),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE1F5E6),
    outline = Color(0xFF9CCC65),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF222222),
    onSurfaceVariant = Color(0xFF555555),
    error = DjError
)

private val LightCrimsonRedColorScheme = lightColorScheme(
    primary = Color(0xFFD50000),
    secondary = Color(0xFFFF1744),
    tertiary = Color(0xFFFF5252),
    background = Color(0xFFFAF0F0),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF5E1E1),
    outline = Color(0xFFE57373),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF222222),
    onSurfaceVariant = Color(0xFF555555),
    error = DjError
)

private val LightCyberCyanColorScheme = lightColorScheme(
    primary = Color(0xFF00B8D4),
    secondary = Color(0xFF00E5FF),
    tertiary = Color(0xFF18FFFF),
    background = Color(0xFFF0FAFA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE1F5F5),
    outline = Color(0xFF4DD0E1),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF222222),
    onSurfaceVariant = Color(0xFF555555),
    error = DjError
)



@Composable
fun MyApplicationTheme(
    appTheme: AppThemeOption = AppThemeOption.SYSTEM,
    // We disable dynamic color to maintain the strict DJ aesthetic (neon/dark)
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val darkTheme = when (appTheme) {
        AppThemeOption.SYSTEM -> isSystemDark
        AppThemeOption.DARK,
        AppThemeOption.DJ_BLUE,
        AppThemeOption.MIDNIGHT_PURPLE,
        AppThemeOption.GOLD_PREMIUM,
        AppThemeOption.NEON_GREEN,
        AppThemeOption.CRIMSON_RED,
        AppThemeOption.CYBER_CYAN -> true
        AppThemeOption.LIGHT,
        AppThemeOption.LIGHT_DJ_BLUE,
        AppThemeOption.LIGHT_MIDNIGHT_PURPLE,
        AppThemeOption.LIGHT_GOLD_PREMIUM,
        AppThemeOption.LIGHT_NEON_GREEN,
        AppThemeOption.LIGHT_CRIMSON_RED,
        AppThemeOption.LIGHT_CYBER_CYAN -> false
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        appTheme == AppThemeOption.DJ_BLUE -> DjBlueColorScheme
        appTheme == AppThemeOption.MIDNIGHT_PURPLE -> MidnightPurpleColorScheme
        appTheme == AppThemeOption.GOLD_PREMIUM -> GoldPremiumColorScheme
        appTheme == AppThemeOption.NEON_GREEN -> NeonGreenColorScheme
        appTheme == AppThemeOption.CRIMSON_RED -> CrimsonRedColorScheme
        appTheme == AppThemeOption.CYBER_CYAN -> CyberCyanColorScheme
        appTheme == AppThemeOption.LIGHT_DJ_BLUE -> LightDjBlueColorScheme
        appTheme == AppThemeOption.LIGHT_MIDNIGHT_PURPLE -> LightMidnightPurpleColorScheme
        appTheme == AppThemeOption.LIGHT_GOLD_PREMIUM -> LightGoldPremiumColorScheme
        appTheme == AppThemeOption.LIGHT_NEON_GREEN -> LightNeonGreenColorScheme
        appTheme == AppThemeOption.LIGHT_CRIMSON_RED -> LightCrimsonRedColorScheme
        appTheme == AppThemeOption.LIGHT_CYBER_CYAN -> LightCyberCyanColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
