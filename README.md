![LGKA+ for Android](docs/assets/banner.png)

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
- **Live weather** — the school's rooftop station when it is healthy, [Open-Meteo](https://open-meteo.com) forecast and fallback — AGSL animated sky, hourly and 3-day forecast
- **Customizable accent colors**, light/dark/auto appearance, edge-to-edge, predictive back
- **Absence reporting** via the official school form

---

## Data layer

Everything the app shows comes from **[api.lgka.app](https://github.com/lgka-app/api)** — a Cloudflare Worker that
mirrors and parses the school's substitution PDFs, timetables, news, calendar and weather once, server-side.
The phone does no scraping and no PDF text extraction any more.

```
launch / resume / every minute on screen
  GET /v1/sync?substitutions=<hash>&schedules=<hash>&news=<hash>&events=<hash>&weather=<hash>&embed=pdf
  → per resource: fresh (nothing sent) · updated (payload + inline PDFs) · unavailable
```

- `core/` (JVM, no Android): `LgkaApi` (OkHttp), the wire models (kotlinx.serialization), `SyncStore`
  (on-disk resources + mirrored PDFs, sync merge), view helpers (hourly window, class → page, schedule lookup).
- `app/`: `HomeViewModel` renders the on-disk copy instantly, then applies one sync. Credentials (the school's
  shared login, typed at onboarding and verified via `/v1/auth/check`) are AES-GCM encrypted with an Android
  Keystore key; a `401` on any request signs the user out. The WebView never sees the credentials.
- Timetable class indices are real 1-based PDF pages (`pagerIndex()` maps them for the pager).

## Modules

```
gradle/libs.versions.toml   version catalog (AGP 9.4, Kotlin 2.4.20, Compose BOM 2026.09, Material 3 1.5, Navigation 3, OkHttp 5)
core/                       api.lgka.app client, models, on-disk sync store, view helpers — JVM unit tests with recorded API responses
app/                        the app: Compose + Material 3 Expressive, Navigation 3, ViewModel, Coil, PdfRenderer; strings in res/values(-en)
designguidelines/           brand rules, screenshots and a cited reference of the Material 3 / Android guidance we follow
```

Targets Android 17 (API 37), runs on Android 10 (API 29) and up. `:app` is only included when an Android SDK is available (`ANDROID_HOME` or `local.properties`), so the JVM module builds anywhere.

## Build

```bash
./gradlew build                   # core tests, app debug + R8 release, lint
./gradlew :app:installDebug       # onto a connected device
```

Needs JDK 17+ (the foojay toolchain resolver provisions it) and the Android SDK with `platforms;android-37.0` and `build-tools;37.0.0`.

## Test

```bash
./gradlew :core:test              # models against recorded API responses, sync merge, 401/403 handling and the auth-check confirmation (MockWebServer), hourly window, page mapping
```

No emulator is needed for CI. Refresh the recorded responses with
`curl -u user:pass -A 'LGKA+/3.0' https://api.lgka.app/v1/<resource> > core/src/test/resources/api/<resource>.json`
(strip `pdf.base64` in `sync_embed.json`).

## Screenshots

The store screenshots are produced by an instrumented Compose test (`app/src/androidTest/.../ScreenshotTest.kt`) on an emulator — no manual navigation:

```bash
LGKA_LOGIN=user:pass scripts/screenshots.sh               # de/en × phone/tablet × dark/light
LGKA_LOGIN=user:pass scripts/screenshots.sh dark phone    # one theme / form factor
```

Output: `app_store_assets/screenshots/<locale>/android/<phone|tablet>/<dark|light>/NN_name.png` at native resolution (Pixel 9: 1080×2424, Pixel Tablet: 2560×1600). The suite also contains a real login-flow regression test.

## Login and credentials

The school website's read-only credentials are entered once by the user, verified with a request to the server and kept in a private preference file that is excluded from backups. They are never part of the source code and are only ever sent to `lessing-gymnasium-karlsruhe.de`.

---

## License

MIT - [View License](LICENSE)

## Support

- [Report bugs](https://github.com/luka-loehr/LGKA/issues)
- [luka@lukaloehr.com](mailto:luka@lukaloehr.com)

Developed by [Luka Löhr](https://github.com/luka-loehr)
