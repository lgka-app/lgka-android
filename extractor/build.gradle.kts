plugins {
    kotlin("jvm") version "2.1.20"
    application
}

dependencies {
    api(project(":core"))
    // JVM-only; the Android app swaps in com.tom-roush:pdfbox-android
    implementation("org.apache.pdfbox:pdfbox:3.0.4")
    implementation("com.google.code.gson:gson:2.11.0")
}

application { mainClass.set("lgka.MainKt") }
kotlin { jvmToolchain(17) }
