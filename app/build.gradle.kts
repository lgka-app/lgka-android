// NOTE: version pins chosen for Gradle 9 / late-2026 toolchains; if the
// first WiFi build complains, bump agp/compose-bom here only.
plugins {
    id("com.android.application") version "8.13.0"
    kotlin("android") version "2.1.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
}

android {
    namespace = "com.lgka"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lgka" // same as the shipping Flutter app
        minSdk = 29
        targetSdk = 36
        versionCode = 300
        versionName = "3.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
