import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pokemmocompanion.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.pokemmocompanion.app"
        minSdk = 29
        targetSdk = 37
        versionCode = 70
        versionName = "0.70"
        // The AYN Thor is arm64; skip other ABIs to keep ML Kit's native libs small.
        ndk { abiFilters += "arm64-v8a" }
    }

    // Release signing: keystore.properties (project root, not in git) with storeFile, storePassword, keyAlias,
    // keyPassword. Without it, release builds are left unsigned.
    val keystoreProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f -> Properties().apply { f.inputStream().use(::load) } }
    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Same key for debug builds, so test builds and published releases install over each other.
        debug {
            if (keystoreProps != null) signingConfig = signingConfigs.getByName("release")
        }
        release {
            if (keystoreProps != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.activity.compose)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  debugImplementation(libs.androidx.compose.ui.tooling)

  // On-device OCR with the bundled model (works offline)
  implementation(libs.mlkit.text.recognition)

  // Game data (PokeMMO Hub snapshot in assets/pokemmo)
  implementation(libs.kotlinx.serialization.json)

  testImplementation(libs.junit)
}
