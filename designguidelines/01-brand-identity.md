# 01 · Brand identity

| | |
|---|---|
| **Last updated** | 2026-09-12 |
| **Applies to** | LGKA+ Android, Android 17 / Material 3 Expressive |
| **Owner** | Luka Löhr |
| **Status** | Normative |

---

## 1. The name

The product is **LGKA+** — always with the plus, always uppercase, never spaced or hyphenated.
It is the single source of the app's display name and of the home-screen label:

```xml
<string name="app_title">LGKA+</string>
```
<sub>`app/src/main/res/values/strings.xml` · identical in `values-en/strings.xml` · used as `android:label` in `AndroidManifest.xml`</sub>

The same string is the top app bar title on the home hub, rendered at `FontWeight.ExtraBold`
(`app/src/main/kotlin/com/lgka/Home.kt`). That is the only place in the app where extra-bold appears —
it is the wordmark, not a text style.

- [x] Write `LGKA+` in copy, commits and store listings.
- [ ] Don't write `LGKA Plus`, `lgka+`, `LGKA-App` or `LGKA App` in user-visible text.
- [x] The long form of the school is `Lessing-Gymnasium Karlsruhe`, spelled out with the hyphen.

## 2. App icon

### 2.1 Layers

The launcher icon is a **Material adaptive icon** with all three layers declared:

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
```
<sub>`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` — `ic_launcher_round.xml` is byte-identical</sub>

| Layer | Source | Value |
|---|---|---|
| Background | `@color/ic_launcher_background` | ![#000000](assets/swatches/000000.svg) `#000000` (`res/values/colors.xml`) |
| Foreground | `@mipmap/ic_launcher_foreground` | Raster PNG, five densities `mdpi`→`xxxhdpi` |
| Monochrome | same asset as the foreground | Enables **themed icons** on Android 13+ |

### 2.2 Motif

The foreground shows **two seated lions on column plinths flanking a round arch**, rendered in a
glossy blue with highlight strokes, on the black background layer. The lions and arch are the
Lessing-Gymnasium's building motif; the blue matches the app's default accent family.
Master art lives outside the app module in `branding/icon-foreground-1024.png` and
`branding/icon-stacked-1024.png`.

The monochrome layer is what makes **themed icons** work:

> starting with Android 13 (API level 33), users can theme their adaptive icons. If a user enables themed app icons in their system settings, and the launcher supports this feature, the system uses the coloring of the user's chosen wallpaper and theme to determine the tint color of the app icons for apps that have a `monochrome` layer in their adaptive icon. Starting with Android 16 QPR 2, Android automatically themes app icons for apps that don't provide their own.
> — [Android — Adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive)

> **Note**
> Reusing the foreground drawable as the monochrome layer is explicitly sanctioned:
> *"The foreground and monochrome layers are using the same drawable. However, you can create separate
> drawables for each layer if needed."*
> — [Android — Adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive).
> LGKA+ does exactly this. Android tints the layer to a single themed color, so the internal shading of the
> art is flattened and the silhouette carries the identity — which it does, because the lions and the arch
> are solid masses. A purpose-drawn silhouette remains an option, not a correction.

See [09 · Iconography §7](09-iconography.md#7-the-launcher-icon) for the layer geometry requirements and how
the asset measures against them.

### 2.3 Splash

The launch theme feeds the same foreground into the platform splash screen, on a background that follows the
system appearance:

```xml
<style name="Theme.LGKA.Splash" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">@color/splash_background</item>
    <item name="windowSplashScreenAnimatedIcon">@mipmap/ic_launcher_foreground</item>
    <item name="postSplashScreenTheme">@style/Theme.LGKA</item>
</style>
```
<sub>`app/src/main/res/values/themes.xml`; installed by `installSplashScreen()` in `MainActivity.onCreate`</sub>

`splash_background` is night-qualified — ![#F2F2F7](assets/swatches/F2F2F7.svg) `#F2F2F7` in
`res/values/colors.xml`, ![#000000](assets/swatches/000000.svg) `#000000` in `res/values-night/colors.xml` —
so the splash hands off to the first app frame on the same color in both appearances. The launcher icon's
`ic_launcher_background` stays ![#000000](assets/swatches/000000.svg) `#000000` regardless, because an icon
background layer is a constant rather than a themed surface.

> **Note**
> On the light ground the art's specular highlights lose definition while its mid-blue mass still reads, so the
> light splash shows a slightly flatter version of the same mark. Measured in
> [02 · Color §7.3](02-color.md#73-the-splash-background-follows-the-system-appearance), along with the known
> limitation that an in-app theme override is not yet reflected in the splash.

### 2.4 The icon inside the app

The onboarding welcome step renders the same foreground asset at 180 dp with a spoken label:

```kotlin
Image(painterResource(R.mipmap.ic_launcher_foreground), stringResource(R.string.a11y_app_logo), Modifier.size(180.dp))
```
<sub>`app/src/main/kotlin/com/lgka/Onboarding.kt` — `a11y_app_logo` = "LGKA+ Logo" / "LGKA+ logo"</sub>

## 3. Tone of voice

Derived entirely from `res/values/strings.xml` and `res/values-en/strings.xml`.

| Trait | Evidence | Rule |
|---|---|---|
| **Second person, informal** | "Wähle deine Lieblingsfarbe aus.", "Tippe, um deine Klasse festzulegen" | German uses *du*, never *Sie*. English uses plain "you". |
| **Students are the audience** | "In welcher Klasse bist du?", "Verwende die Zugangsdaten, die du bereits von der Schulwebsite kennst" | Assume a pupil, not a parent or an administrator. |
| **Plain, unhedged** | "Zugangsdaten sind falsch." / "The credentials are incorrect." | State the fact. No apologies, no "Oops". |
| **Warm at milestones only** | "Willkommen!", "Los geht's!" / "Welcome!", "Let's go!" | Exclamation marks appear in onboarding and nowhere else. |
| **Honest about causes** | "Möglicherweise besteht keine Internetverbindung oder es finden gerade Wartungsarbeiten am Lessing-Gymnasium statt." | Failure copy names the plausible cause and what to try. |
| **No jargon, no branding of other parties** | "Die Krankmeldung wird vom Lessing-Gymnasium bereitgestellt und ist unabhängig von der LGKA+ App." | Say plainly when a feature is not ours. |

- [x] Address the reader directly and tell them what to do next.
- [x] Name the responsible party when the app is only a shell around a school service.
- [ ] Don't use emoji in user-visible strings — there are none in either locale, keep it that way.
- [ ] Don't apologise, don't use "Sorry", "Oops" or error codes in user-facing text.

## 4. What LGKA+ is — and is not

**It is** a read-only companion to the school's own systems: substitution plans for today and tomorrow,
class timetables as PDF, school news and events, Karlsruhe weather, and a doorway to the school's
sick-note form.

**It is not:**

| Not a… | Because |
|---|---|
| Social network | There is no user-generated content, no profile, no messaging. |
| School administration client | The sick note is the school's own web form, opened in-app with a disclaimer (`WebScreen.kt`). |
| Data collector | The only permission requested is `android.permission.INTERNET` (`AndroidManifest.xml`). |
| Grade or attendance app | No such surface exists in the code. |
| Offline document editor | PDFs are rendered and shared, never edited (`PdfViewer.kt`). |

Two identity-level consequences follow, and both are enforced in code:

1. **Personalization belongs to the student.** Accent color and theme mode are chosen during onboarding
   and changeable forever after in Settings (`Prefs.kt`, `Settings.kt`). This is the app's one expressive
   lever — see [02 · Color](02-color.md).
2. **The school's brand is not impersonated.** The app never presents school-provided content as its own;
   news articles and the sick note link out, and the weather card credits Open-Meteo
   ("Wetterdaten von Open-Meteo.com", `WeatherScreen.kt`).

> **Note**
> One deliberate piece of whimsy exists: `FireworksOverlay` in `Settings.kt` draws a particle burst
> on 1 January (Europe/Berlin) and is skipped when the system animation scale is 0. It is decorative,
> non-interactive, and the only seasonal element in the app. Do not add more without a reason.

## Related

[02 · Color](02-color.md) · [09 · Iconography](09-iconography.md) · [12 · Writing & localization](12-writing-and-localization.md)

## Sources

| Source | Accessed |
|---|---|
| App source: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `res/values/colors.xml`, `res/values/themes.xml`, `res/values/strings.xml`, `res/values-en/strings.xml`, `AndroidManifest.xml`, `kotlin/com/lgka/{Onboarding,Settings,WeatherScreen,PdfViewer,WebScreen,Prefs}.kt`, `branding/` | 2026-09-12 |
| [Material 3 — Start building with M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive) | 2026-09-12 |
| [Android — Color for mobile design](https://developer.android.com/design/ui/mobile/guides/styles/color) | 2026-09-12 |
| [Android — Adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive) | 2026-09-12 |
