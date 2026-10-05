import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.carlospinan.guardavideo.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 7
        versionName = "0.7.0"
    }

    // Play upload key: android/keystore.properties (not in git). Until it exists, Play bundles are
    // built unsigned (Play rejects debug-signed uploads anyway).
    val uploadProps = rootProject.file("keystore.properties")
    signingConfigs {
        if (uploadProps.exists()) {
            val p = Properties().apply { uploadProps.inputStream().use(::load) }
            create("upload") {
                storeFile = rootProject.file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    // Two distributions from one codebase. The ids are permanent once installed / created in Play.
    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            // Sideload (you and friends): site pages via yt-dlp. Same id as before: keeps the library.
            dimension = "distribution"
            applicationId = "com.carlospinan.guardavideo"
            // yt-dlp ships a Python runtime per ABI: arm64 covers current phones and Apple-silicon emulators.
            ndk { abiFilters += "arm64-v8a" }
            // Local key, so release builds install over debug ones without losing the library.
            signingConfig = signingConfigs.getByName("debug")
            proguardFile("proguard-full.pro")
        }
        create("play") {
            // Google Play: direct links only (no yt-dlp, Python or ffmpeg). All ABIs: Play splits them.
            dimension = "distribution"
            applicationId = "com.carlospinan.guardavideo.play"
            signingConfig = signingConfigs.findByName("upload")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signing comes from the flavor: full = local key, play = upload key.
        }
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(projects.shared)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.ktor.client.okhttp)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.transformer)
    implementation(libs.media3.cast)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    // Full flavor only: yt-dlp + Python + ffmpeg. The Play build does not contain them at all.
    "fullImplementation"(libs.youtubedl.android)
    "fullImplementation"(libs.youtubedl.ffmpeg)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okio.fakefilesystem)
}

// youtubedl-android runs Python/ffmpeg as executables from the extracted native-lib folder, so the
// full flavor needs legacy (extracted) packaging; the Play bundle keeps the modern default.
androidComponents {
    onVariants(selector().withFlavor("distribution" to "full")) { variant ->
        variant.packaging.jniLibs.useLegacyPackaging.set(true)
    }
}
