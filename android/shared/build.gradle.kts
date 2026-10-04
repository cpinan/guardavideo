plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    android {
        namespace = "com.carlospinan.guardavideo.shared"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        // Host (JVM) tests are opt-in with AGP's KMP library plugin: without this, commonTest
        // compiles nothing and silently never runs (lesson from VeterinariosApp).
        withHostTest {}
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.okio)
            api(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.ktor.client.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.okio.fakefilesystem)
        }
    }
}

room {
    // Exported schemas are checked in: they are what a future migration test compares against.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Room's processor runs once per target that compiles the database.
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach { add(it, libs.room.compiler) }
}
