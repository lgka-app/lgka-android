plugins {
    kotlin("jvm") version "2.1.20"
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jsoup:jsoup:1.18.3")
}

kotlin { jvmToolchain(17) }
