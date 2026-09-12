# 09 · Iconography

| | |
|---|---|
| **Last updated** | 2026-09-12 |
| **Applies to** | LGKA+ Android, Android 17 / Material 3 Expressive |
| **Owner** | Luka Löhr |
| **Status** | Normative |

---

## 1. One icon source

Every in-app icon comes from **Material Symbols** via `androidx.compose.material:material-icons-extended`
(`1.7.8`, `gradle/libs.versions.toml`). The app ships **no custom vector drawables** —
`app/src/main/res/drawable/` is empty. The only raster art is the launcher foreground.

- [x] Pick an existing symbol from `Icons.Outlined` / `Icons.Filled` / `Icons.AutoMirrored`.
- [ ] Don't add an SVG or a vector drawable without a written reason.

## 2. Outlined vs filled

The split is consistent and load-bearing:

| Set | Meaning | Examples |
|---|---|---|
| `Icons.Outlined` | **Nouns** — a subject or a destination | `CalendarToday`, `Schedule`, `TableChart`, `School`, `Event`, `Newspaper`, `MedicalServices`, `Settings`, `Cloud`, `CloudOff`, `Person`, `Lock`, `Visibility`, `BugReport`, `PrivacyTip`, `Info`, `WarningAmber`, `SupportAgent`, `WifiOff` |
| `Icons.Filled` | **Verbs and states** — something happening or chosen | `Refresh`, `Check`, `Search`, `Share`, `Close`, `KeyboardArrowUp`, `KeyboardArrowDown`, `DarkMode`, `LightMode`, `BrightnessAuto` |
| `Icons.AutoMirrored` | **Direction** — flips under RTL | `ArrowBack`, `ArrowForwardIos`, `OpenInNew`, `Logout` |

> **Warning**
> Every directional glyph must come from `Icons.AutoMirrored`. The manifest declares
> `android:supportsRtl="true"`, so a non-mirrored arrow would point the wrong way in an RTL locale.
> The app currently ships `de` and `en` only, but the rule costs nothing and prevents a silent regression.

## 3. Sizes

| Size | Used for |
|---|---|
| **14 dp** | Trailing chevron; `OpenInNew` affordance; meta-row glyphs (author, date, views) |
| **16 dp** | Segmented button icons |
| **18 dp** | Icon inside an `OutlinedButton`; inline spinner |
| **20 dp** | Icon inside a 44 dp tile; settings tile leading icon; `RetryButton` glyph; CTA button icon |
| **30 dp** | Weather condition icon on the home weather card |
| **40 dp** | `CloudOff` in the substitution error card |
| **48 dp** | Onboarding feature tile (the tile; icon inside is default size) |
| **52 dp** | Krankmeldung info tile |
| **56 dp** | `WifiOff` in the WebView error state |
| **180 dp** | App logo on the onboarding welcome step |

Default `Icon` size (24 dp) is used where no `Modifier.size` is given — for example the top app bar actions,
which `IconButton` already pads to a 48 dp target.

## 4. The icon tile

The recurring container that gives a card its subject at a glance:

```kotlin
/** 44dp tinted icon square used across the home cards (decorative). */
@Composable
fun IconTile(icon: ImageVector, alpha: Float = 0.12f) {
    Box(
        Modifier.size(44.dp).background(
            MaterialTheme.colorScheme.primary.copy(alpha = alpha), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}
```
<sub>`app/src/main/kotlin/com/lgka/Widgets.kt`</sub>

| Variant | Size | Radius | Fill | Where |
|---|---|---|---|---|
| `IconTile` | 44 dp | 12 dp | `primary` @ 12 % (6 % disabled) | Home cards |
| `DateTile` | 44 dp | 12 dp | `primary` @ 12 % | Event cards — day numeral + 10 sp month instead of an icon |
| Onboarding feature tile | 48 dp | 12 dp | `primary` @ 10 % | Feature list |
| Krankmeldung info tile | 52 dp | 14 dp | `primary` @ 15 % | Info cards |

The icon inside is always passed `contentDescription = null`: the tile is decorative and the card text
already says what the card is.

> Use the hideFromAccessibility API to mark purely decorative elements so that accessibility services can ignore them. If a UI element has a contentDescription parameter but is purely decorative (such as an Icon that is part of another UI element), pass null to avoid redundant labeling.
> — [Android — Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps)

## 5. Icons that carry meaning

An icon that is the *only* label for an action must have a `contentDescription` from a string resource:

| Icon | `contentDescription` | Where |
|---|---|---|
| `Newspaper` | `R.string.news` | Home app bar |
| `MedicalServices` | `R.string.krankmeldung` | Home app bar |
| `Settings` | `R.string.settings` | Home app bar |
| `ArrowBack` | `R.string.a11y_back` | Every detail app bar |
| `Close` | `R.string.a11y_close` | PDF viewer, in-app browser |
| `Search` | `R.string.a11y_search` | PDF viewer |
| `Share` | `R.string.a11y_share` | PDF viewer |
| `KeyboardArrowUp` / `Down` | `R.string.a11y_previous_match` / `a11y_next_match` | PDF search |
| `Refresh` (in `RetryButton`) | `R.string.a11y_retry` | Card retry |
| `OpenInNew` | `R.string.open_in_browser` | News detail app bar |

> Use descriptions to convey the purpose and result of the interaction, not the visual details. […] Avoid redundancies in descriptions. For example, if selecting a button causes a "submit" action to occur in your app, make the button's description "Submit", not "Submit button".
> — [Android — Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps)

The app follows this: the description is "Zurück", not "Zurück-Button".

## 6. Weather iconography — WMO mapping

Weather codes are mapped to symbols in one place, so the home card, the hourly strip and the 3-day list
never disagree:

```kotlin
object WmoIcons {
    fun icon(code: Int, isDay: Boolean): ImageVector = when (code) {
        0, 1 -> if (isDay) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay
        2 -> Icons.Outlined.WbCloudy
        3 -> Icons.Outlined.Cloud
        45, 48 -> Icons.Outlined.Dehaze
        in 51..67, in 80..82 -> Icons.Outlined.WaterDrop
        in 71..77, 85, 86 -> Icons.Outlined.AcUnit
        95, 96, 99 -> Icons.Outlined.Thunderstorm
        else -> Icons.Outlined.Cloud
    }
}
```
<sub>`app/src/main/kotlin/com/lgka/WeatherScreen.kt`</sub>

| WMO codes | Symbol | Condition |
|---|---|---|
| 0, 1 | `WbSunny` / `NightsStay` | Clear / mostly clear, day or night |
| 2 | `WbCloudy` | Partly cloudy |
| 3 | `Cloud` | Overcast |
| 45, 48 | `Dehaze` | Fog, freezing fog |
| 51–67, 80–82 | `WaterDrop` | Drizzle, rain, showers |
| 71–77, 85, 86 | `AcUnit` | Snow |
| 95, 96, 99 | `Thunderstorm` | Thunderstorm |
| anything else | `Cloud` | Unknown |

Only 0 and 1 are day/night aware, because only the sun has a night counterpart. Each icon is paired with a
localized text description from `wmoRes(code)` (`Theme.kt`), so the icon is never the sole carrier —
matching the Android rule that color and imagery must not be the only indicator.

The daily forecast rows deliberately pass `isDay = true` for every day: a daily summary has no time of day.

## 7. The launcher icon

The motif and the layer declarations are in [01 · Brand identity §2](01-brand-identity.md#2-app-icon).
This section is the geometry check.

> An adaptive icon, or `AdaptiveIconDrawable`, can display differently depending on individual device capabilities and user theming. Adaptive icons are primarily used by the launcher on the home screen, but they can also be used in shortcuts, the Settings app, sharing dialogs, and the overview screen. Adaptive icons are used across all Android form factors.
> — [Android — Adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive)

### 7.1 The design requirements, measured

> - You must provide two layers for the color version of the icon: one for the foreground, and one for the background. The layers can be either vectors or bitmaps, though vectors are preferred.
> - If you want to support user theming of app icons, provide a single layer for the monochrome version of the icon.
> - Size all layers to 108x108 dp.
> - Use icons with clean edges. The layers must not have masks or background shadows around the outline of the icon.
> - Use a logo that's at least 48x48 dp. It must not exceed 66x66 dp, because the inner 66x66 dp of the icon appears within the masked viewport.
>
> — [Android — Adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive)

| Requirement | LGKA+ | Verdict |
|---|---|---|
| Two color layers | `background` = `@color/ic_launcher_background`, `foreground` = `@mipmap/ic_launcher_foreground` | ✅ |
| Monochrome layer for theming | Present, pointing at the foreground drawable | ✅ |
| Layers sized 108×108 dp | `mdpi` 108², `hdpi` 162², `xhdpi` 216², `xxhdpi` 324², `xxxhdpi` 432² — exactly 108 dp at every density | ✅ |
| Clean edges, no mask or outline shadow on the layer | The foreground is a transparent-background PNG; the rounded-square and shadow seen in `branding/icon-stacked-1024.png` are a **preview composite**, not the shipped layer | ✅ |
| Logo between 48 and 66 dp, inside the 66×66 dp safe zone | The motif is inset well within the central region, clear of the 18 dp reserved margin on each side | ✅ |
| Vectors preferred over bitmaps | **Bitmaps** — five density-specific PNGs | ⚠️ Deviation |

> **Note — the one deviation**
> The doc prefers vectors: *"The layers can be either vectors or bitmaps, though vectors are preferred."*
> LGKA+ ships bitmaps. The art is a shaded, glossy illustration with gradients and highlight strokes, which is
> not what a `VectorDrawable` is good at, and the five PNGs already cover every density at the correct 108 dp.
> The cost is APK size and a lost opportunity for crisper scaling. It is a considered trade, not an oversight.

### 7.2 What the monochrome layer buys

> In the following scenarios, the home screen doesn't display the themed app icon, and instead displays the adaptive or standard app icon:
> - If the user doesn't enable themed app icons.
> - If your app doesn't provide a monochromatic app icon and the user's device runs on an earlier version of Android than Android 16 QPR 2.
> - If the launcher doesn't support themed app icons.
>
> — [Android — Adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive)

Because LGKA+ declares a monochrome layer, it opts out of the third case entirely and controls its own themed
appearance rather than letting Android 16 QPR 2 generate one. Reusing the foreground drawable for that layer
is explicitly allowed — see the note in [01 · Brand identity §2.2](01-brand-identity.md#22-motif).

### 7.3 `roundIcon`

The manifest sets both attributes, and `mipmap-anydpi-v26/ic_launcher_round.xml` is byte-identical to
`ic_launcher.xml`:

> An optional attribute, `android:roundIcon`, is used by launchers that represent apps with circular icons, and may be useful if your app's icon includes a circular background as a core part of its design. […] The following code snippet illustrates both of these attributes, but most apps only specify `android:icon`.
> — [Android — Adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive)

LGKA+'s icon has no circular background element, so the round variant buys nothing. Declaring an identical
duplicate is harmless but redundant; dropping `android:roundIcon` and `ic_launcher_round.xml` would match the
"most apps only specify `android:icon`" guidance.

## Related

[01 · Brand identity](01-brand-identity.md) · [02 · Color](02-color.md) · [06 · Components](06-components.md) · [10 · Accessibility](10-accessibility.md)

## Sources

| Source | Accessed |
|---|---|
| [Android — Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps) | 2026-09-12 |
| [Compose — Accessibility API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) | 2026-09-12 |
| [Android — Color for mobile design](https://developer.android.com/design/ui/mobile/guides/styles/color) | 2026-09-12 |
| [Android — Adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive) | 2026-09-12 |
| App source: `kotlin/com/lgka/{Widgets,Home,News,Settings,Onboarding,WeatherScreen,PdfViewer,WebScreen,Theme}.kt`, `res/mipmap-anydpi-v26/{ic_launcher,ic_launcher_round}.xml`, `res/mipmap-*/ic_launcher_foreground.png`, `res/values/strings.xml`, `AndroidManifest.xml`, `gradle/libs.versions.toml` | 2026-09-12 |
