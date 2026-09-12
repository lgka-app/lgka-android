plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
    compilerOptions { allWarningsAsErrors.set(true) }
}

dependencies {
    api(project(":core"))
    // JVM-only; the Android app swaps in com.tom-roush:pdfbox-android
    implementation(libs.pdfbox)
    implementation(libs.gson)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application { mainClass.set("lgka.MainKt") }

tasks.test {
    useJUnitPlatform()
    environment("LGKA_VERIFICATION_DIR", System.getenv("LGKA_VERIFICATION_DIR") ?: "")
}
