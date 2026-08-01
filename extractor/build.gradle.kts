plugins {
    kotlin("jvm") version "2.1.20"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // On Android this becomes com.tom-roush:pdfbox-android (same API,
    // different package prefix) — keep PDFBox usage isolated in PdfWords.kt.
    implementation("org.apache.pdfbox:pdfbox:3.0.4")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jsoup:jsoup:1.18.3")
}

application {
    mainClass.set("lgka.MainKt")
}

kotlin {
    jvmToolchain(21)
}
