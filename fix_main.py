content = open("app/src/main/java/com/example/MainActivity.kt").read()
if "\n    }\n}\n// KARAOKE_MIC_PAGE_V5" in content:
    content = content.replace("\n    }\n}\n// KARAOKE_MIC_PAGE_V5", "\n}\n// KARAOKE_MIC_PAGE_V5")
open("app/src/main/java/com/example/MainActivity.kt", "w").write(content)
