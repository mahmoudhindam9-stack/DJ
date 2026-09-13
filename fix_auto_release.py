import re

with open('.github/workflows/auto-release.yml', 'r') as f:
    content = f.read()

# Replace assembleDebug with assembleRelease
content = content.replace('Build Debug APK', 'Build Release APK')
content = content.replace('run: gradle :app:assembleDebug', 'run: gradle :app:assembleRelease')

# Replace apk paths
content = content.replace('app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/release/app-release.apk')

with open('.github/workflows/auto-release.yml', 'w') as f:
    f.write(content)

print("Updated auto-release.yml")
