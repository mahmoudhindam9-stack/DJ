import re

with open('app/build.gradle.kts', 'r') as f:
    content = f.read()

fallback = '''      if (ksFile.exists() && !System.getenv("STORE_PASSWORD").isNullOrEmpty()) {
        storeFile = ksFile
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      } else {
        val debugKs = file("${rootDir}/debug.keystore")
        if (debugKs.exists()) {
          storeFile = debugKs
          storePassword = "android"
          keyAlias = "androiddebugkey"
          keyPassword = "android"
        }
      }'''

content = re.sub(r'      if \(ksFile\.exists\(\) && !System\.getenv\("STORE_PASSWORD"\)\.isNullOrEmpty\(\)\) \{\s*storeFile = ksFile\s*storePassword = System\.getenv\("STORE_PASSWORD"\)\s*keyAlias = System\.getenv\("KEY_ALIAS"\) \?: "upload"\s*keyPassword = System\.getenv\("KEY_PASSWORD"\)\s*\}', fallback, content)

with open('app/build.gradle.kts', 'w') as f:
    f.write(content)

print("Updated build.gradle.kts")
