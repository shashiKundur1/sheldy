import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Optional Gemini key for the companion chat, read from local.properties so it never reaches
// version control. Copy local.properties.example to local.properties and fill it in, or skip it
// entirely and paste a key in Settings on the phone. Everything except the chat works without one.
val geminiKey: String = runCatching {
    Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }.getProperty("gemini.key", "")
}.getOrDefault("")

android {
    namespace = "com.shashi.shield"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shashi.shield"
        minSdk = 30          // WindowInsets.Type.systemBars() for safe-area handling
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // MediaPipe ships native libs for four ABIs. This phone is arm64 and always will be, and
        // carrying the other three would triple the APK for code that can never run on it.
        ndk { abiFilters.add("arm64-v8a") }
        buildConfigField("String", "GEMINI_KEY", "\"$geminiKey\"")
    }

    buildFeatures { buildConfig = true }

    // The sentence encoder is memory-mapped at load. A compressed asset cannot be mapped, so
    // without this the model has to be unpacked to disk first and TextEmbedder just fails.
    androidResources { noCompress.add("tflite") }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// ponytail: still no UI dependencies, views are built in code, no AppCompat, no Compose.
//
// The one exception is on-device NLP. Deciding whether "there is a UPI transaction that needs
// doing" is a real reason is a semantic question, and a keyword list cannot answer it: it cannot
// tell that sentence apart from "I want to transact with instagram". MediaPipe's text embedder
// turns both into vectors and the distance between them is the answer. It runs entirely on the
// phone, which matters more here than anywhere else in the app, the lock has to open at 3am on
// aeroplane mode, so its judgement cannot depend on a network call.
dependencies {
    implementation("com.google.mediapipe:tasks-text:0.10.35")
}
