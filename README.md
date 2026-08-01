![LGKA+ Banner](https://raw.githubusercontent.com/luka-loehr/LGKA/main/app_store_assets/banners/lgka_banner_1024x500.png)

# LGKA+ – The app for Lessing-Gymnasium Karlsruhe

[![Kotlin](https://img.shields.io/badge/Kotlin-Latest-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-Android-green?style=flat)](https://github.com/luka-loehr/LGKA/releases)
[![License](https://img.shields.io/badge/License-MIT-green?style=flat)](LICENSE)
[![App Store](https://img.shields.io/badge/App%20Store-%20-black?style=flat&logo=apple&logoColor=white)](https://apps.apple.com/app/lgka/id6747010920)
[![Google Play](https://img.shields.io/badge/Google%20Play-%20-3DDC84?style=flat&logo=google-play&logoColor=white)](https://play.google.com/store/apps/details?id=com.lgka)

This repository contains the **native Android source code (Kotlin / Jetpack Compose)** of LGKA+.

**LGKA+** is a mobile app for substitution plans, timetables, news, absence reporting, and weather data of the Lessing-Gymnasium Karlsruhe. Built with Jetpack Compose, Material Design 3.

> iOS version: [lgka-app/lgka-ios](https://github.com/lgka-app/lgka-ios) · Predecessor (Flutter): [luka-loehr/LGKA](https://github.com/luka-loehr/LGKA)

---

## Features

- **Substitution plans** (today & tomorrow) with auto-refresh and caching
- **Timetables** (PDF) with class search
- **News and events**
- **PDF viewer** with zoom, share, and class lookup
- **Live weather** from [Open-Meteo](https://github.com/open-meteo/open-meteo) — current conditions and 3-day forecast
- **Customizable accent colors**
- **Absence reporting** via official school form

---

## License

MIT - [View License](LICENSE)

---

## Support

- [Report bugs](https://github.com/luka-loehr/LGKA/issues)  
- [luka@lukaloehr.com](mailto:luka@lukaloehr.com)  

---

Developed by [Luka Löhr](https://github.com/luka-loehr)

---

## Extractor (first native module)

`extractor/` contains the substitution-plan extractor and the schedule class-to-page index: a Kotlin port of the
geometric Untis-table parser, using Apache PDFBox glyph positions (on-device
it swaps to the API-compatible [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android)).
It is verified at **100% parity** against the golden dataset in
[lgka-app/verification](https://github.com/lgka-app/verification).

Runner modes: `substitution` and `classindex` (PDFs), `schedulehtml`
(Stundenplan page scrape), `news`, `events`, `weather` — the app's complete
data layer, all verified against the goldens.

```bash
# run over the verification fixtures
git clone https://github.com/lgka-app/verification.git ../verification
cd extractor
gradle run --args "substitution ../../verification/fixtures/substitution /tmp/out-kotlin"
gradle run --args "classindex ../../verification/fixtures/schedule /tmp/out-kotlin"
# compare against goldens (Rust; run once, get report.html)
cargo run --release --manifest-path ../../verification/tool/compare-report/Cargo.toml -- /tmp/out-kotlin
```


---

## Native app modules

- `:core` — the verified data-layer parsers (shared; JVM + Android)
- `:extractor` — parity CLI (Apache PDFBox, JVM); goldens gate in
  [lgka-app/verification](https://github.com/lgka-app/verification)
- `:app` — the native app (Jetpack Compose Material 3, AGSL weather shader,
  PdfBox-Android for on-device PDF geometry)

Build (needs Android SDK + first-run dependency downloads — do this on WiFi):

```bash
# with ANDROID_HOME set and cmdline-tools installed:
gradle :app:assembleRelease
gradle :extractor:run --args "..."   # parity CLI unchanged
```
