import re

content = open("app/src/main/java/com/example/ui/theme/Theme.kt").read()

dark_theme_sel = """    val darkTheme = when (appTheme) {
        AppThemeOption.SYSTEM -> isSystemDark
        AppThemeOption.DARK -> true
        AppThemeOption.LIGHT -> false
        else -> true
    }"""
content = re.sub(r'    val darkTheme = when \(appTheme\) \{.*?AppThemeOption\.LIGHT -> false\n    \}', dark_theme_sel, content, flags=re.DOTALL)

open("app/src/main/java/com/example/ui/theme/Theme.kt", "w").write(content)
