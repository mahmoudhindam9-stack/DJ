import os
content = open("app/src/main/java/com/example/ui/theme/Theme.kt").read()

content = content.replace("@Composable\n@Composable\nfun MyApplicationTheme", "@Composable\nfun MyApplicationTheme")
open("app/src/main/java/com/example/ui/theme/Theme.kt", "w").write(content)
