import re
with open('app/build.gradle.kts', 'r') as f:
    content = f.read()

# Replace the signing config block
signing_block = """  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val ksFile = file(keystorePath)
      
      if (ksFile.exists()) {
        storeFile = ksFile
        storePassword = System.getenv("STORE_PASSWORD") ?: ""
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD") ?: ""
      }
    }
  }"""

content = re.sub(r'  signingConfigs \{[\s\S]*?\}\n  \}\n', signing_block + '\n', content)

with open('app/build.gradle.kts', 'w') as f:
    f.write(content)
