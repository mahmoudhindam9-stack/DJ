import re

# Clean MainActivity.kt
file_ma = "app/src/main/java/com/example/MainActivity.kt"
content_ma = open(file_ma, "r").read()
# The TutorialOverlay block spans multiple lines
content_ma = re.sub(r'TutorialOverlay\(\s*onNavigate\s*=\s*\{.*?\n\s*\}\s*\)', '', content_ma, flags=re.DOTALL)
content_ma = content_ma.replace("TutorialManager.init(this, version)", "")
open(file_ma, "w").write(content_ma)

# Clean DJMixerScreen.kt
file_dj = "app/src/main/java/com/example/DJMixerScreen.kt"
content_dj = open(file_dj, "r").read()
content_dj = re.sub(r'\.run\s*\{\s*if\s*\([^)]+\)\s*tutorialTarget\(TutorialStep\.DJ_LOAD\)\s*else\s*this\s*\}', '', content_dj)
open(file_dj, "w").write(content_dj)

# Clean MainPlayerExperience.kt
file_mp = "app/src/main/java/com/example/MainPlayerExperience.kt"
content_mp = open(file_mp, "r").read()
content_mp = re.sub(r'val tutorialStep by TutorialManager\.currentStep\.collectAsState\(\)', '', content_mp)
content_mp = re.sub(r'LaunchedEffect\(tutorialStep\)\s*\{[^\}]+\}', '', content_mp, flags=re.DOTALL)
open(file_mp, "w").write(content_mp)

