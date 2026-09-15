import re
file_mp = "app/src/main/java/com/example/MainPlayerExperience.kt"
content_mp = open(file_mp, "r").read()
content_mp = re.sub(r'else if \(tutorialStep == TutorialStep\.NONE\)\s*\{\s*showLibraryMenu = false\s*\}\s*\}', '', content_mp)
open(file_mp, "w").write(content_mp)
