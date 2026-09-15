import re
content = open("app/src/main/java/com/example/ui/theme/Theme.kt").read()

content = content.replace("fun MyApplicationTheme(", "@Composable\nfun MyApplicationTheme(")
content = content.replace("content:  () -> Unit", "content: @Composable () -> Unit")

open("app/src/main/java/com/example/ui/theme/Theme.kt", "w").write(content)
