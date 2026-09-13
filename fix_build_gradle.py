import re
with open('app/build.gradle.kts', 'r') as f:
    content = f.read()

# Make sure we remove our custom `tasks.whenTaskAdded` from previous turn if it's still there
content = re.sub(r'tasks\.whenTaskAdded \{.*?\}\n', '', content, flags=re.DOTALL)

# Inject our taskGraph.whenReady early check
early_check = """
gradle.taskGraph.whenReady {
    if (hasTask(":app:assembleRelease") || hasTask(":app:bundleRelease") || hasTask(":app:packageRelease")) {
        val ksPath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
        if (!file(ksPath).exists()) {
            throw GradleException("Release keystore not found at $ksPath. Cannot build Release APK.")
        }
    }
}
"""

content = content + early_check

with open('app/build.gradle.kts', 'w') as f:
    f.write(content)
