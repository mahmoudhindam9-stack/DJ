import re
content = open("app/src/main/java/com/example/ui/theme/Theme.kt").read()

new_enums = """    CYBER_CYAN("Cyber Cyan"),
    LIGHT_DJ_BLUE("Light DJ Blue"),
    LIGHT_MIDNIGHT_PURPLE("Light Midnight Purple"),
    LIGHT_GOLD_PREMIUM("Light Gold Premium"),
    LIGHT_NEON_GREEN("Light Neon Green"),
    LIGHT_CRIMSON_RED("Light Crimson Red"),
    LIGHT_CYBER_CYAN("Light Cyber Cyan")"""
content = content.replace('    CYBER_CYAN("Cyber Cyan")', new_enums)

light_schemes = """private val LightDjBlueColorScheme = lightColorScheme(
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

@Composable"""
content = content.replace('@Composable', light_schemes)

dark_theme_logic = """    val darkTheme = when (appTheme) {
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
    }"""
content = re.sub(r'    val darkTheme = when \(appTheme\) \{.*?(?=    val colorScheme = when \{)', dark_theme_logic + '\n', content, flags=re.DOTALL)

color_scheme_logic = """        appTheme == AppThemeOption.DJ_BLUE -> DjBlueColorScheme
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
        else -> LightColorScheme"""
content = re.sub(r'        appTheme == AppThemeOption\.DJ_BLUE.*?else -> LightColorScheme', color_scheme_logic, content, flags=re.DOTALL)

open("app/src/main/java/com/example/ui/theme/Theme.kt", "w").write(content)
