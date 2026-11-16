import os
import re

dir_path = "app/src/main/java/com/example"

for root, dirs, files in os.walk(dir_path):
    if "tutorial" in root.split(os.sep):
        continue
    for file in files:
        if file.endswith(".kt"):
            filepath = os.path.join(root, file)
            with open(filepath, "r") as f:
                content = f.read()

            # Remove .tutorialTarget(...) and Modifier.tutorialTarget(...)
            content = re.sub(r'\.tutorialTarget\(TutorialStep\.[A-Z_]+\)', '', content)
            content = re.sub(r'modifier\s*=\s*Modifier\.tutorialTarget\(TutorialStep\.[A-Z_]+\)', '', content)
            content = re.sub(r'Modifier\.run\s*\{\s*if\s*\([^\)]+\)\s*tutorialTarget\(TutorialStep\.[A-Z_]+\)\s*else\s*this\s*\}\s*,?', 'Modifier,', content)
            
            # Additional cleanup for dangling commas or double commas in some modifiers 
            content = re.sub(r',\s*,', ',', content)
            content = re.sub(r'\(\s*,', '(', content)
            
            # Remove tutorial imports
            content = re.sub(r'import com\.example\.tutorial\..*\n', '', content)
            
            # Specific cleanups for MainActivity.kt
            if file == "MainActivity.kt":
                # Remove TutorialManager.init(...)
                content = re.sub(r'TutorialManager\.init\([^)]+\)', '', content)
                # Remove TutorialOverlay block
                content = re.sub(r'TutorialOverlay\(\s*onNavigate\s*=\s*\{[^}]+\}\s*\)', '', content)

            with open(filepath, "w") as f:
                f.write(content)

