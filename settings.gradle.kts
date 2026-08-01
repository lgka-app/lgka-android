pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "lgka-android"
include(":core", ":extractor")
// :app needs the Android SDK + AGP; keep the JVM modules (parity CLI)
// buildable on machines without either.
if (System.getenv("ANDROID_HOME") != null || file("local.properties").exists()) {
    include(":app")
}
