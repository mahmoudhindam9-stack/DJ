import re
with open('app/build.gradle.kts', 'r') as f:
    content = f.read()

# Remove the early check exception
content = re.sub(r'gradle\.taskGraph\.whenReady \{.*?\}\n', '', content, flags=re.DOTALL)

# Modify buildTypes
old_release_block = """    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val relConfig = signingConfigs.getByName("release")
      signingConfig = relConfig
    }"""

new_release_block = """    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      if (file(keystorePath).exists()) {
        signingConfig = signingConfigs.getByName("release")
      }
    }"""

content = content.replace(old_release_block, new_release_block)

with open('app/build.gradle.kts', 'w') as f:
    f.write(content)
