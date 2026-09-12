plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    compilerOptions { allWarningsAsErrors.set(true) }
}

dependencies {
    implementation(libs.gson)
    implementation(libs.jsoup)
    // jsoup 1.23 annotates with JSpecify; Kotlin needs the annotations on the classpath.
    implementation(libs.jspecify)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Golden parity tests need the lgka-app/verification checkout; CI clones
    // it next to the repo, `LGKA_VERIFICATION_DIR` overrides the lookup.
    environment("LGKA_VERIFICATION_DIR", System.getenv("LGKA_VERIFICATION_DIR") ?: "")
}
