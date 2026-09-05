import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.net.HttpURLConnection
import java.net.URL

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.musicplayer.abcde"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val ksFile = file(keystorePath)
      if (ksFile.exists() && !System.getenv("STORE_PASSWORD").isNullOrEmpty()) {
        storeFile = ksFile
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      } else {
        val debugKsFile = file("${rootDir}/debug.keystore")
        if (debugKsFile.exists()) {
          storeFile = debugKsFile
          storePassword = "android"
          keyAlias = "androiddebugkey"
          keyPassword = "android"
        }
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val relConfig = signingConfigs.getByName("release")
      if (relConfig.storeFile != null) {
        signingConfig = relConfig
      } else {
        signingConfig = signingConfigs.getByName("debug")
      }
    }
    debug {
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

data class FactoryFxDownload(val relativePath: String, val url: String)

val factoryFxDownloads = listOf(
  FactoryFxDownload("factory_fx/dj/bell1.wav", "https://raw.githubusercontent.com/code4fukui/sound-cc0/main/bell1.wav"),
  FactoryFxDownload("factory_fx/dj/cracker1.wav", "https://raw.githubusercontent.com/code4fukui/sound-cc0/main/cracker1.wav"),
  FactoryFxDownload("factory_fx/dj/cracker1v-stereo.wav", "https://raw.githubusercontent.com/code4fukui/sound-cc0/main/cracker1v-stereo.wav"),
  FactoryFxDownload("factory_fx/dj/cracker1v.wav", "https://raw.githubusercontent.com/code4fukui/sound-cc0/main/cracker1v.wav"),
  FactoryFxDownload("factory_fx/dj/cracker2.wav", "https://raw.githubusercontent.com/code4fukui/sound-cc0/main/cracker2.wav"),
  FactoryFxDownload("factory_fx/dj/metal1.wav", "https://raw.githubusercontent.com/code4fukui/sound-cc0/main/metal1.wav"),
  FactoryFxDownload("factory_fx/dj/steel1.wav", "https://raw.githubusercontent.com/code4fukui/sound-cc0/main/steel1.wav"),
  FactoryFxDownload("factory_fx/dj/switch1.wav", "https://raw.githubusercontent.com/code4fukui/sound-cc0/main/switch1.wav"),
  FactoryFxDownload("factory_fx/dj/wahaha.wav", "https://raw.githubusercontent.com/code4fukui/sound-cc0/main/wahaha.wav"),
  FactoryFxDownload("factory_fx/kenney/laserLarge_000.ogg", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/laserLarge_000.ogg"),
  FactoryFxDownload("factory_fx/kenney/explosionCrunch_001.ogg", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/explosionCrunch_001.ogg"),
  FactoryFxDownload("factory_fx/kenney/explosionCrunch_002.ogg", "https://raw.githubusercontent.com/euuuuuuan/voidclad-public/main/assets/sfx/kenney/explosionCrunch_002.ogg"),
  FactoryFxDownload("factory_fx/kenney/explosionCrunch_003.ogg", "https://raw.githubusercontent.com/euuuuuuan/voidclad-public/main/assets/sfx/kenney/explosionCrunch_003.ogg"),
  FactoryFxDownload("factory_fx/kenney/forceField_000.ogg", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/forceField_000.ogg"),
  FactoryFxDownload("factory_fx/kenney/forceField_001.ogg", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/forceField_001.ogg"),
  FactoryFxDownload("factory_fx/kenney/computerNoise_000.ogg", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/computerNoise_000.ogg"),

  FactoryFxDownload("factory_fx/drums/hard-kick-01.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/kicks/hard-kick-01.wav"),
  FactoryFxDownload("factory_fx/drums/hard-kick-02.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/kicks/hard-kick-02.wav"),
  FactoryFxDownload("factory_fx/drums/hard-kick-03.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/kicks/hard-kick-03.wav"),
  FactoryFxDownload("factory_fx/drums/808-bass-dist.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/808s/808-bass-dist.wav"),
  FactoryFxDownload("factory_fx/drums/808-bass-sub.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/808s/808-bass-sub.wav"),
  FactoryFxDownload("factory_fx/drums/hard-snare-01.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/snares/hard-snare-01.wav"),
  FactoryFxDownload("factory_fx/drums/hard-snare-02.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/snares/hard-snare-02.wav"),
  FactoryFxDownload("factory_fx/drums/hard-snare-03.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/snares/hard-snare-03.wav"),
  FactoryFxDownload("factory_fx/drums/clap-01.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/claps/clap-01.wav"),
  FactoryFxDownload("factory_fx/drums/cl.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/claps/cl.wav"),
  FactoryFxDownload("factory_fx/drums/hi-hat-closed-01.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/hi-hats/hi-hat-closed-01.wav"),
  FactoryFxDownload("factory_fx/drums/ch.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/hi-hats/ch.wav"),
  FactoryFxDownload("factory_fx/drums/open-hat-01.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/open-hats/open-hat-01.wav"),
  FactoryFxDownload("factory_fx/drums/perc-cowbell.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/percs/perc-cowbell.wav"),
  FactoryFxDownload("factory_fx/drums/perc-rimshot.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/percs/perc-rimshot.wav"),
  FactoryFxDownload("factory_fx/drums/fx-cymbal.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/01-hard-trap/fx/fx-cymbal.wav"),

  FactoryFxDownload("factory_fx/electronic/bounce-kick-01.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/kicks/bounce-kick-01.wav"),
  FactoryFxDownload("factory_fx/electronic/bounce-kick-02.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/kicks/bounce-kick-02.wav"),
  FactoryFxDownload("factory_fx/electronic/bounce-kick-03.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/kicks/bounce-kick-03.wav"),
  FactoryFxDownload("factory_fx/electronic/808-bass-long.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/808s/808-bass-long.wav"),
  FactoryFxDownload("factory_fx/electronic/808-bass-punch.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/808s/808-bass-punch.wav"),
  FactoryFxDownload("factory_fx/electronic/bounce-snare-01.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/snares/bounce-snare-01.wav"),
  FactoryFxDownload("factory_fx/electronic/bounce-snare-02.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/snares/bounce-snare-02.wav"),
  FactoryFxDownload("factory_fx/electronic/bounce-snare-03.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/snares/bounce-snare-03.wav"),
  FactoryFxDownload("factory_fx/electronic/clap-01.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/claps/clap-01.wav"),
  FactoryFxDownload("factory_fx/electronic/hi-hat-closed-01.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/hi-hats/hi-hat-closed-01.wav"),
  FactoryFxDownload("factory_fx/electronic/open-hat-01.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/open-hats/open-hat-01.wav"),
  FactoryFxDownload("factory_fx/electronic/perc-high-tom.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/percs/perc-high-tom.wav"),
  FactoryFxDownload("factory_fx/electronic/perc-low-tom.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/percs/perc-low-tom.wav"),
  FactoryFxDownload("factory_fx/electronic/808-round-long.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/808s/808-round-long.wav"),
  FactoryFxDownload("factory_fx/electronic/fx-cymbal.wav", "https://raw.githubusercontent.com/Boochi44/free-drum-samples/main/drum-samples/02-bounce/fx/fx-cymbal.wav"),
  FactoryFxDownload("factory_fx/electronic/laserSmall_000.ogg", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/laserSmall_000.ogg"),

  FactoryFxDownload("factory_fx/party/doorOpen_000.ogg", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/doorOpen_000.ogg"),
  FactoryFxDownload("factory_fx/party/doorOpen_001.ogg", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/doorOpen_001.ogg"),
  FactoryFxDownload("factory_fx/party/doorClose_000.ogg", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/doorClose_000.ogg"),
  FactoryFxDownload("factory_fx/party/doorClose_001.ogg", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/doorClose_001.ogg"),
  FactoryFxDownload("factory_fx/party/laserSmall_001.ogg", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/laserSmall_001.ogg"),
  FactoryFxDownload("factory_fx/party/laserSmall_002.ogg", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/laserSmall_002.ogg"),
  FactoryFxDownload("factory_fx/party/impactMetal_000.ogg", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/impactMetal_000.ogg"),
  FactoryFxDownload("factory_fx/party/impactMetal_001.ogg", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/impactMetal_001.ogg"),
  FactoryFxDownload("factory_fx/party/impactMetal_002.ogg", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/impactMetal_002.ogg"),
  FactoryFxDownload("factory_fx/party/explosionCrunch_004.ogg", "https://raw.githubusercontent.com/euuuuuuan/voidclad-public/main/assets/sfx/kenney/explosionCrunch_004.ogg"),
  FactoryFxDownload("factory_fx/party/lowFrequency_explosion_000.ogg", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/lowFrequency_explosion_000.ogg"),
  FactoryFxDownload("factory_fx/party/engineCircular_000.ogg", "https://raw.githubusercontent.com/danvanderboom/Aetherium/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds/engineCircular_000.ogg"),
  FactoryFxDownload("factory_fx/party/impactMetal_light_003.ogg", "https://raw.githubusercontent.com/euuuuuuan/voidclad-public/main/assets/sfx/kenney/impactMetal_light_003.ogg"),
  FactoryFxDownload("factory_fx/party/impactMetal_medium_002.ogg", "https://raw.githubusercontent.com/euuuuuuan/voidclad-public/main/assets/sfx/kenney/impactMetal_medium_002.ogg"),
  FactoryFxDownload("factory_fx/party/steel1.wav", "https://raw.githubusercontent.com/code4fukui/sound-cc0/main/steel1.wav"),
  FactoryFxDownload("factory_fx/party/wahaha.wav", "https://raw.githubusercontent.com/code4fukui/sound-cc0/main/wahaha.wav")
)

check(factoryFxDownloads.size == 64) { "Expected exactly 64 factory FX assets, got ${factoryFxDownloads.size}" }

val factoryFxAssetsDir = layout.buildDirectory.dir("generated/factoryFxAssets/main")

val prepareFactoryFxAssets = tasks.register("prepareFactoryFxAssets") {
  outputs.dir(factoryFxAssetsDir)
  doLast {
    val root = factoryFxAssetsDir.get().asFile
    factoryFxDownloads.forEach { item ->
      val target = root.resolve(item.relativePath)
      if (!target.exists() || target.length() < 128L) {
        target.parentFile.mkdirs()
        val connection = (URL(item.url).openConnection() as HttpURLConnection).apply {
          connectTimeout = 30_000
          readTimeout = 120_000
          instanceFollowRedirects = true
          requestMethod = "GET"
          setRequestProperty("User-Agent", "DJ-FactoryFX-AssetBuilder/1.0")
        }
        connection.connect()
        try {
          if (connection.responseCode !in 200..299) {
            error("Factory FX download failed (${connection.responseCode}): ${item.url}")
          }
          connection.inputStream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
          if (target.length() < 128L) error("Factory FX file is unexpectedly small: ${item.relativePath}")
        } finally {
          connection.disconnect()
        }
      }
    }
    println("Prepared ${factoryFxDownloads.size} real factory FX assets for offline APK playback")
  }
}

android.sourceSets.getByName("main").assets.srcDir(factoryFxAssetsDir)
tasks.named("preBuild").configure { dependsOn(prepareFactoryFxAssets) }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  implementation("androidx.media3:media3-exoplayer:1.4.1")
  implementation("androidx.media3:media3-ui:1.4.1")
  implementation("androidx.media:media:1.7.0")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
