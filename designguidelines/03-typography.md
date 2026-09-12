# 03 · Typography

| | |
|---|---|
| **Last updated** | 2026-09-12 |
| **Applies to** | LGKA+ Android, Android 17 / Material 3 Expressive |
| **Owner** | Luka Löhr |
| **Status** | Normative |

---

## 1. The type system in one sentence

LGKA+ uses the **stock Material 3 type scale, unmodified**, and expresses hierarchy through
*style choice* and *weight override* rather than through a custom `Typography` object.

`MaterialExpressiveTheme` is called in `Theme.kt` with `colorScheme` and `motionScheme` only —
no `typography` argument — so every style comes from the Compose Material 3 defaults:

```kotlin
MaterialExpressiveTheme(
    colorScheme = scheme,
    motionScheme = MotionScheme.expressive(),
    content = content,
)
```
<sub>`app/src/main/kotlin/com/lgka/Theme.kt` — there is no `Typography(...)` anywhere in `app/src/main/kotlin`</sub>

Consequence: the font is the platform default, and the sizes and line heights are the published M3 values.
Compose resolves an unset `fontFamily` from its built-in set:

> `Text` has a `fontFamily` parameter to allow setting the font used in the composable. By default, serif, sans-serif, monospace and cursive font families are included
> — [Compose — Work with fonts](https://developer.android.com/develop/ui/compose/text/fonts)

The M3 type scale names **Roboto** as the font for every token:

| Token | Default | Token | Default |
|---|---|---|---|
| `displayLarge` | Roboto 57/64 | `titleMedium` | Roboto Medium 16/24 |
| `displayMedium` | Roboto 45/52 | `titleSmall` | Roboto Medium 14/20 |
| `displaySmall` | Roboto 36/44 | `bodyLarge` | Roboto 16/24 |
| `headlineLarge` | Roboto 32/40 | `bodyMedium` | Roboto 14/20 |
| `headlineMedium` | Roboto 28/36 | `bodySmall` | Roboto 12/16 |
| `headlineSmall` | Roboto 24/32 | `labelLarge` | Roboto Medium 14/20 |
| `titleLarge` | Roboto Medium 22/28 | `labelMedium` | Roboto Medium 12/16 |
| | | `labelSmall` | Roboto Medium 11/16 |

<sub>Values as published in [Compose — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)</sub>

> Your product will likely not need all 15 default styles from the Material Design type scale.
> — [Compose — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)

LGKA+ uses **nine** of them. The rest are unused and should stay unused.

## 2. Where each style is used

| Style | Weight override | Used for | Source |
|---|---|---|---|
| `displayLarge` (at 92 sp) | `Thin` | The hero temperature on the weather screen | `WeatherScreen.kt` `Hero` |
| `displaySmall` | `Medium` | Temperature on the home weather card | `Home.kt` `WeatherRow` |
| `displaySmall` | `Bold` | "Willkommen!" onboarding headline | `Onboarding.kt` `WelcomeStep` |
| `headlineMedium` | `Bold` | Onboarding step headlines, "Anmeldung erforderlich" | `Onboarding.kt` |
| `headlineSmall` | `Bold` | News article title, "Einstellungen" sheet title | `News.kt`, `Settings.kt` |
| `titleLarge` | `Bold` | "Weitere Neuigkeiten" section in the article | `News.kt` |
| `titleLarge` (default) | `Medium` | City name on the weather hero | `WeatherScreen.kt` |
| `titleMedium` | `Bold` | `SectionHeader` — "Vertretungsplan", "Stundenplan", "Bevorstehende Termine" | `Widgets.kt` |
| `titleMedium` | `Medium` | Weather stat tile values | `WeatherScreen.kt` `StatTile` |
| `bodyLarge` | — | News article body text | `News.kt` |
| `bodyMedium` | — | Feature descriptions, auth subtitle, Krankmeldung info text, news card description | `Onboarding.kt`, `News.kt`, `WebScreen.kt` |
| `bodySmall` | — | Card subtitles, meta rows (author · date · views), copyright line | `Home.kt`, `News.kt`, `Settings.kt` |
| `labelLarge` | `SemiBold` / `Medium` | Weather card city, condition, high/low; PDF match counter | `Home.kt`, `PdfViewer.kt` |
| `labelMedium` | `SemiBold` | "STÜNDLICH" / "3 TAGE" headers, "Mehr erfahren", weather attribution | `WeatherScreen.kt`, `News.kt` |
| `labelSmall` | `SemiBold` | Stat-tile labels, section labels "DARSTELLUNG" / "MEHR", news tags | `WeatherScreen.kt`, `Settings.kt`, `News.kt` |
| *(no style)* | `ExtraBold` | The `LGKA+` wordmark in the home top app bar | `Home.kt` |

Body text that carries no explicit `style` inherits `bodyLarge`, which is the Compose `Text` default.
That is the case for card titles (`FontWeight.SemiBold` only), list labels and dialog text.

## 3. Weight is the hierarchy lever

M3 sanctions this:

> Using different font weights for text. […] you can provide custom weights to our type scale for providing different emphasis.
> — [Compose — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)

> Guide attention with typography. Use emphasized text styles to draw attention to important UI elements, like headlines and actions. […] Heavier weights, larger sizes, color, and spacing can direct attention and make key information more engaging.
> — [Material 3 — Start building with M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)

The app's ladder, from heaviest to lightest:

| Weight | Reserved for |
|---|---|
| `ExtraBold` | The wordmark only — one place in the app |
| `Bold` | Screen and section headlines; the numeral in a date tile |
| `SemiBold` | Card titles, button labels, all-caps micro-labels, values that must be found fast |
| `Medium` | Secondary emphasis inside the weather surfaces |
| *(default `Normal`)* | Body copy, descriptions, meta rows |
| `Thin` | The 92 sp weather hero temperature — a single hero moment |

- [x] Reach for a heavier weight before reaching for a larger style.
- [x] Keep `ExtraBold` unique to the wordmark.
- [ ] Don't introduce a new font family. The app ships no font assets and no `fontFamily` override.
- [ ] Don't use `Thin` outside the weather hero — at any smaller size it fails legibility on the sky.

## 4. All-caps micro-labels

Three labels are uppercase, and they are uppercase in **two different ways** — know which you are editing:

| Label | How | Source |
|---|---|---|
| "STÜNDLICH" / "HOURLY", "3 TAGE" / "3 DAYS" | Uppercase **in the string resource** | `strings.xml`, `values-en/strings.xml` |
| "DARSTELLUNG" / "APPEARANCE", "MEHR" / "MORE" | Uppercase **in the string resource** | `strings.xml`, `values-en/strings.xml` |
| Weather stat-tile labels ("Luftfeuchte", "Wind", …) | `label.uppercase()` **at render time** | `WeatherScreen.kt` `StatTile` |

> **Warning**
> `label.uppercase()` without a locale argument uses the default locale. For German and English this is
> harmless, but it is a latent bug for any locale with special casing rules. If a third locale is ever
> added to `res/xml/locales_config.xml`, move the casing into the string resources like the others.

## 5. Scaling and the 92 sp hero

The weather hero overrides the size on a scale style rather than inventing a new one:

```kotlin
Text("${w.temp.toInt()}°", color = Color.White,
     style = MaterialTheme.typography.displayLarge.copy(fontSize = 92.sp),
     fontWeight = FontWeight.Thin)
```
<sub>`app/src/main/kotlin/com/lgka/WeatherScreen.kt`</sub>

This is the app's clearest **hero moment**, which the Expressive guidance reserves for one or two places:

> Stick to one or two hero moments in your product; too many moments can be overwhelming or distracting.
> — [Material 3 — Start building with M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)

Sizes are declared in `sp` throughout, so they follow the user's font-size setting. Layouts are written to
survive that: cards use `heightIn(min = …)` rather than fixed heights, and the weather card carries the
comment *"The row defines the card height (never clips at large font sizes)"* in `Home.kt`.

- [x] Size text in `sp`, size boxes in `dp`.
- [x] Use `heightIn(min = …)`, never `height(…)`, on anything containing text.
- [ ] Don't cap `maxLines` on anything a student must read in full. Exceptions in the code are deliberate:
      news card descriptions (`maxLines = 2`), event titles (`maxLines = 2`), PDF viewer title and
      download-link labels (`maxLines = 1`).

## 6. What is not done

| M3 feature | Status in LGKA+ |
|---|---|
| Emphasized type styles (the 15 added in the May 2025 Expressive update) | **Not used.** The app predates them in its type decisions and simulates emphasis with `FontWeight`. |
| Variable fonts (Roboto Flex, Google Sans Flex) | **Not used.** No font assets ship with the app; `app/src/main/res/font/` does not exist. |
| Custom `fontFamily` via `res/font` | **Not used.** |
| Downloadable fonts | **Not used.** |
| Custom `Typography` object | **Not used.** See §1. |

> Material's type scale includes fifteen baseline type styles, the same as before, and fifteen new emphasized type styles. The emphasized type styles add more expression to highlighted moments.
> — [Material 3 — Typography overview](https://m3.material.io/styles/typography/overview)

Adopting emphasized styles would be the single highest-value typography change available, and would let
most of the `FontWeight` overrides in §2 disappear. It is not a bug that they are absent; it is a backlog item.

### 6.1 What a custom font would cost

If a brand face is ever considered, these are the facts that decide it:

> A variable font is a font format that allows one font file to contain different styles. With variable fonts, you can modify axes (or parameters) to generate your preferred style. These axes can be standard, such as weight, width, slant, and italic, or custom, which differ across variable fonts.
> — [Compose — Work with fonts](https://developer.android.com/develop/ui/compose/text/fonts)

> Using variable fonts instead of regular font files allows you to only have one font file instead of multiple.
> — [Compose — Work with fonts](https://developer.android.com/develop/ui/compose/text/fonts)

> Warning: Variable fonts are only supported on Android O and above.
> — [Compose — Work with fonts](https://developer.android.com/develop/ui/compose/text/fonts)

The `minSdk` of 29 clears the Android O floor comfortably, so a single variable `.ttf` in `res/font` would
cover all six weights the app uses today, at one asset instead of six. Two constraints make this a decision
rather than a formality:

- The M3 `Typography` class has no shortcut for it —
  *"Unlike the M2 `Typography` class, the M3 `Typography` class doesn't currently include a `defaultFontFamily` parameter. You'll need to use the `fontFamily` parameter in each of the individual `TextStyles` instead."*
  — [Compose — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3).
  Every one of the nine styles in §2 would have to be redeclared.
- Downloadable fonts are not a shortcut either: they do not support variable fonts, and
  *"Google Fonts takes several months to make new fonts available on Android."*
  — [Compose — Work with fonts](https://developer.android.com/develop/ui/compose/text/fonts)

- [x] If a custom face is adopted, bundle a variable `.ttf` in `res/font` rather than several static weights.
- [ ] Don't set `fontFamily` at individual call sites. It belongs in one `Typography` object passed to the theme.

## Related

[02 · Color](02-color.md) · [05 · Material 3 Expressive](05-material-expressive.md) · [12 · Writing & localization](12-writing-and-localization.md)

## Sources

| Source | Accessed |
|---|---|
| [Material 3 — Typography overview](https://m3.material.io/styles/typography/overview) | 2026-09-12 |
| [Material 3 — Start building with M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive) | 2026-09-12 |
| [Compose — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) | 2026-09-12 |
| [Compose — Work with fonts](https://developer.android.com/develop/ui/compose/text/fonts) | 2026-09-12 |
| App source: `kotlin/com/lgka/{Theme,Home,Widgets,WeatherScreen,News,Onboarding,Settings,PdfViewer,WebScreen}.kt`, `res/values/strings.xml`, `res/values-en/strings.xml`, `app/build.gradle.kts` | 2026-09-12 |
