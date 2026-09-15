import re

content = open("app/src/main/java/com/example/ui/theme/Theme.kt").read()

new_enum = """enum class AppThemeOption(val displayName: String) {
    SYSTEM("System Default"),
    DARK("Dark Mode"),
    LIGHT("Light Mode"),
    DJ_BLUE("DJ Blue"),
    MIDNIGHT_PURPLE("Midnight Purple"),
    GOLD_PREMIUM("Gold Premium"),
    NEON_GREEN("Neon Green"),
    CRIMSON_RED("Crimson Red"),
    CYBER_CYAN("Cyber Cyan")
}"""

content = re.sub(r'enum class AppThemeOption.*?\}', new_enum, content, flags=re.DOTALL)

new_schemes = """private val LightColorScheme = lightColorScheme(
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
)"""

content = re.sub(r'private val LightColorScheme = lightColorScheme\(.*?error = DjError\n\)', new_schemes, content, flags=re.DOTALL)

theme_selection = """    val colorScheme = when {
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
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }"""
content = re.sub(r'    val colorScheme = when \{.*?else -> LightColorScheme\n    \}', theme_selection, content, flags=re.DOTALL)

open("app/src/main/java/com/example/ui/theme/Theme.kt", "w").write(content)
