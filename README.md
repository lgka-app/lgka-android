![LGKA+ Banner](https://raw.githubusercontent.com/luka-loehr/LGKA/main/app_store_assets/banners/lgka_banner_1024x500.png)

# LGKA+ – The app for Lessing-Gymnasium Karlsruhe

[![CI](https://github.com/lgka-app/lgka-android/actions/workflows/ci.yml/badge.svg)](https://github.com/lgka-app/lgka-android/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Android-10%20%E2%80%93%2017-green?style=flat)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-green?style=flat)](LICENSE)
[![App Store](https://img.shields.io/badge/App%20Store-%20-black?style=flat&logo=apple&logoColor=white)](https://apps.apple.com/app/lgka/id6747010920)
[![Google Play](https://img.shields.io/badge/Google%20Play-%20-3DDC84?style=flat&logo=google-play&logoColor=white)](https://play.google.com/store/apps/details?id=com.lgka)

This repository contains the **native Android source code (Kotlin 2.4 / Jetpack Compose, Material 3 Expressive)** of LGKA+.

**LGKA+** is a mobile app for substitution plans, timetables, news, absence reporting, and weather data of the Lessing-Gymnasium Karlsruhe.

> iOS version: [lgka-app/lgka-ios](https://github.com/lgka-app/lgka-ios) · Parity harness: [lgka-app/verification](https://github.com/lgka-app/verification) · Predecessor (Flutter): [luka-loehr/LGKA](https://github.com/luka-loehr/LGKA)

---

## Features

- **Substitution plans** (today & tomorrow) with auto-refresh and caching
- **Timetables** (PDF) with class search and cross-PDF class switching
- **News and events**
- **PDF viewer** with lazy page rendering, search, and share
- **Live weather** from [Open-Meteo](https://github.com/open-meteo/open-meteo) — AGSL animated sky, hourly and 3-day forecast
- **Customizable accent colors**, light/dark/auto appearance, edge-to-edge, predictive back
- **Absence reporting** via the official school form

---

## Modules

```
gradle/libs.versions.toml   version catalog (AGP 9.4, Kotlin 2.4.20, Compose BOM 2026.09, Material 3 1.5, Navigation 3)
core/                       the verified data-layer parsers (JVM, no Android): substitution extractor,
                            glyph clustering, schedule page, news, events, weather — 100% golden parity
extractor/                  parity CLI (Apache PDFBox) used by lgka-app/verification
app/                        the app: Compose + Material 3 Expressive, Navigation 3, ViewModel, Coil,
                            PdfBox-Android for on-device PDF text; strings in res/values(-en)
designguidelines/           brand rules, screenshots and a cited reference of the Material 3 / Android guidance we follow
```

Targets Android 17 (API 37), runs on Android 10 (API 29) and up. `:app` is only included when an Android SDK is available (`ANDROID_HOME` or `local.properties`), so the JVM modules build anywhere.

## Build

```bash
./gradlew build                   # JVM modules + tests, app debug + R8 release, lint
./gradlew :app:installDebug       # onto a connected device
```

Needs JDK 17+ (the foojay toolchain resolver provisions it) and the Android SDK with `platforms;android-37.0` and `build-tools;37.0.0`.

## Test

```bash
git clone https://github.com/lgka-app/verification.git ../verification
./gradlew :core:test :extractor:test     # golden parity + robustness (LGKA_VERIFICATION_DIR overrides the lookup)
```

The parity CLI is unchanged:

```bash
./gradlew :extractor:run --args "substitution ../verification/fixtures/substitution /tmp/out-kotlin"
./gradlew :extractor:run --args "classindex ../verification/fixtures/schedule /tmp/out-kotlin"
cargo run --release --manifest-path ../verification/tool/compare-report/Cargo.toml -- /tmp/out-kotlin
```

## Login and credentials

The school website's read-only credentials are entered once by the user, verified with a request to the server and kept in a private preference file that is excluded from backups. They are never part of the source code and are only ever sent to `lessing-gymnasium-karlsruhe.de`.

---

## License

MIT - [View License](LICENSE)

## Support

- [Report bugs](https://github.com/luka-loehr/LGKA/issues)
- [luka@lukaloehr.com](mailto:luka@lukaloehr.com)

Developed by [Luka Löhr](https://github.com/luka-loehr)
