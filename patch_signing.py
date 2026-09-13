import re
with open('app/build.gradle.kts', 'r') as f:
    content = f.read()

signing_block = """  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val ksFile = file(keystorePath)
      
      storeFile = ksFile
      if (ksFile.exists()) {
        storePassword = System.getenv("STORE_PASSWORD") ?: ""
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD") ?: ""
      } else {
        // Will fail on assembleRelease if keystore is missing, but configures fine.
      }
    }
  }"""

content = re.sub(r'  signingConfigs \{[\s\S]*?\}\n  \}\n', signing_block + '\n', content)

with open('app/build.gradle.kts', 'w') as f:
    f.write(content)
