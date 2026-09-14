import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application) // AGP 9 built-in Kotlin: no kotlin("android")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.lgka"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lgka" // same as the shipping Flutter app
        minSdk = 29
        targetSdk = 37
        // The release workflow passes the next free Play version code (fastlane/Fastfile).
        versionCode = providers.gradleProperty("lgka.versionCode").orNull?.toInt() ?: 310
        versionName = providers.gradleProperty("lgka.versionName").orNull ?: "3.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Play upload key (alias "upload", 1Password LGKA+/lgka-upload-keystore), provided by
    // .github/workflows/release.yml. Without ANDROID_KEYSTORE_PATH release builds stay unsigned.
    val keystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storeType = "pkcs12"
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        localeFilters += listOf("de", "en")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // Third-party jars are audited upstream; our own code must be clean.
        checkDependencies = false
        ignoreTestSources = true
    }

    // phones only: ML Kit's bundled OCR library is ~11 MB per ABI and the release ships one universal APK
    defaultConfig.ndk.abiFilters += setOf("arm64-v8a", "armeabi-v7a")

    // the language can be switched in Settings, so every language must be installed
    bundle {
        language { enableSplit = false }
    }

    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES",
            // bouncycastle (via pdfbox-android) post-quantum tables: never used, ~4 MB
            "org/bouncycastle/pqc/**")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":core")) // api.lgka.app client, models, on-disk sync store
    implementation(libs.okhttp)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.activity.compose)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // custom J11/J12 timetable: camera, on-device text recognition (bundled model, no network), PDF text positions
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.pdfbox.android)

    // string resources are complete in every locale (app/src/test)
    testImplementation(libs.junit4)

    // AGP aligns the androidTest classpath with the app's; androidx.test
    // needs newer support libraries than the app would otherwise pull in.
    constraints {
        implementation(libs.androidx.concurrent.futures)
        implementation(libs.errorprone.annotations) // CameraX / ML Kit bring 2.28, espresso needs 2.30
        implementation(libs.androidx.tracing)
    }

    // Screenshot suite (app/src/androidTest, scripts/screenshots.sh)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
