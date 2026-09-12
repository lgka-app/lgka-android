# 10 · Accessibility

| | |
|---|---|
| **Last updated** | 2026-09-12 |
| **Applies to** | LGKA+ Android, Android 17 / Material 3 Expressive |
| **Owner** | Luka Löhr |
| **Status** | Normative — accessibility is a build requirement, not a polish pass |

---

## 1. Position

> Accessibility by default is a core design value for Material.
> — [Material 3 — Accessible design overview](https://m3.material.io/foundations/accessible-design/overview)

> Requirements as a starting point. The minimum requirements established by WCAG support specific human needs. However, these requirements can produce creative solutions with broad benefits.
> — [Material 3 — Accessible design overview](https://m3.material.io/foundations/accessible-design/overview)

LGKA+ is used daily by a whole school. It takes the Compose defaults seriously and adds semantics wherever
the defaults cannot know the intent.

> Material, Compose UI, and Foundation APIs implement and offer many accessible practices by default. They contain built-in semantics that follow their specific role and function. This means most accessibility support is provided with little or no additional work.
> — [Compose — Accessibility API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)

## 2. Touch targets

> For touch interfaces, we recommend that each interactive UI element have a focusable area, or touch target size, of at least 48dpx48dp. Larger is even better.
> — [Android — Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps)

> Any on-screen element that someone can click, touch, or interact with must be large enough for reliable interaction. When sizing these elements, make sure to set the minimum size to 48dp to correctly follow the Material Design accessibility guidelines.
> — [Compose — Accessibility API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)

| Target | Size | How |
|---|---|---|
| Home card | ≥ 76 dp tall, full width | `HomeCard` `heightIn(min = 76.dp)` |
| Weather card | ≥ 112 dp tall | `heightIn(min = 112.dp)` |
| App bar actions, PDF search buttons, `RetryButton` | 48 dp | `IconButton` default padding |
| Settings tile | ≥ 48 dp | `heightIn(min = 48.dp)` + `clickable(role = Role.Button)` |
| Accent swatch | 48 dp minimum, even when the swatch is 32 dp | `Modifier.size(maxOf(swatchSize, 48).dp)` |
| Primary CTA | ≥ 52 dp | `heightIn(min = 52.dp)` |
| Weather attribution link | ≥ 48 dp | `heightIn(min = 48.dp)` + 14 dp vertical padding |
| Debug sky-preview long-press | 48 dp | `Modifier.size(48.dp)` on the invisible box |

The accent swatch is the pattern to copy: the **visual** stays 32 dp, the **target** grows to 48 dp.

## 3. Content descriptions

> For each UI element in your app, include a description that describes the element's purpose.
> — [Android — Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps)

> Note that you do not need to provide a contentDescription for Text composables. Android accessibility services (like TalkBack) automatically announce the text itself.
> — [Android — Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps)

The app has **twelve dedicated `a11y_*` string resources**, all localized in both `values` and `values-en`:

| Resource | German | English |
|---|---|---|
| `a11y_app_logo` | LGKA+ Logo | LGKA+ logo |
| `a11y_back` | Zurück | Back |
| `a11y_close` | Schließen | Close |
| `a11y_day` | `%1$s: %2$s, Höchstwert %3$d Grad, Tiefstwert %4$d Grad` | `%1$s: %2$s, high %3$d degrees, low %4$d degrees` |
| `a11y_hour` | `%1$s Uhr, %2$d Grad, %3$s` | `%1$s, %2$d degrees, %3$s` |
| `a11y_match_position` | Treffer %1$d von %2$d | Match %1$d of %2$d |
| `a11y_next_match` / `a11y_previous_match` | Nächster / Vorheriger Treffer | Next / Previous match |
| `a11y_open_in_browser` | Im Browser öffnen | Open in browser |
| `a11y_page` | Seite %1$d | Page %1$d |
| `a11y_retry` | Erneut laden | Reload |
| `a11y_search` | Im PDF suchen | Search in PDF |
| `a11y_selected` | ausgewählt | selected |
| `a11y_share` | PDF teilen | Share PDF |
| `a11y_sky_preview` | Himmel-Vorschau | Sky preview |
| `a11y_weather_card` | `Wetter in Karlsruhe: %1$s, %2$d Grad. Tippen für Details.` | `Weather in Karlsruhe: %1$s, %2$d degrees. Tap for details.` |

Note what these do: they turn a **grid of numbers into a sentence**. A hourly column that reads visually as
"14 / ☔ / 60 % / 19°" is announced as one string, not four fragments.

- [x] Every new `a11y_*` string goes into both locale files in the same commit.
- [x] Decorative icons get `contentDescription = null`.
- [ ] Don't write "Button" or "Icon" into a description. The `Role` already says that.

## 4. Merging and roles

> Because interactable components might consist of multiple elements, clickable and toggleable merge their children's semantics by default, so that the component is treated as one logical entity.
> — [Compose — Accessibility API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)

The app uses three merging patterns:

**1. A whole card as one button** — weather card:

```kotlin
Card(
    onClick = onOpen,
    shape = CardShape,
    modifier = Modifier.fillMaxWidth().testTag("home.weather").semantics { contentDescription = a11y; role = Role.Button },
)
```
<sub>`app/src/main/kotlin/com/lgka/Home.kt` — with the comment *"Card(onClick) makes the entire card the touch target, not just the text."*</sub>

**2. Non-interactive groups merged into one announcement** — event cards, feature cards, Krankmeldung info
cards, hourly columns, daily rows, weather hero:

```kotlin
HomeCard(modifier = Modifier.semantics(mergeDescendants = true) {
    contentDescription = "$subtitle: ${event.title}"
})
```
<sub>`app/src/main/kotlin/com/lgka/Home.kt`</sub>

**3. Explicit roles where a container is the control** — `Role.Button` on settings tiles and the weather
card, `Role.RadioButton` on accent swatches.

> Use the Role semantics property (like Role.Button or Role.Switch) to expose a UI element's type. This way, screen readers can announce the element correctly.
> — [Android — Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps)

## 5. Headings

> To improve the navigation experience, some accessibility services allow for easier navigation directly between sections or headings. To enable this, indicate that your component is a heading by defining its semantics property
> — [Compose — Semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics)

`heading()` is set on: `SectionHeader` (`Widgets.kt`), the news article title and "Weitere Neuigkeiten"
(`News.kt`), the settings sheet title (`Settings.kt`), all onboarding and auth headlines (`Onboarding.kt`),
the weather screen title and the "STÜNDLICH" / "3 TAGE" labels (`WeatherScreen.kt`).

That gives TalkBack a real outline of the home hub: *Vertretungsplan → Stundenplan → Bevorstehende Termine*.

## 6. Unique descriptions in lists

> Each description should be unique. […] In particular, each item within a list such as LazyColumn should have a different description, each reflecting the content that's unique to a given item
> — [Android — Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps)

| List | Per-item description |
|---|---|
| Events | `"$subtitle: ${event.title}"` — date and title |
| Hourly forecast | `a11y_hour` — time, temperature, condition |
| 3-day forecast | `a11y_day` — day name, condition, high, low |
| PDF pages | `a11y_page` — "Seite 3" |
| News list | The card's own text (title, description, meta) is announced; no override needed |

> **Warning — the one place this rule is stretched**
> `SkeletonRow()` sets `contentDescription = R.string.loading` on every instance, so four stacked skeletons
> announce "Lädt…" four times. It is a transient state and the alternative (announcing nothing) is worse,
> but a single `liveRegion` announcement on the list would be better. Recorded as a known limitation.
> The mechanism is documented under
> [Compose — Semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics):
> *"Alert-like components can be marked with the liveRegion semantics property. This allows accessibility
> services to automatically notify the user of changes to this component, or its children"*.

## 7. `testTag` is not accessibility

The app uses `Modifier.testTag(...)` **only** for the instrumented screenshot suite, never as a substitute
for a label:

| Tag | Purpose |
|---|---|
| `home.weather`, `home.news`, `home.sick`, `home.settings` | Entry points the suite taps |
| `home.plan.today`, `home.plan.tomorrow` | Substitution cards; the suite reads their `Disabled` semantics to pick a tappable one |
| `plan.page` | Proves a PDF page actually rasterized before capture |
| `news.row`, `news.detail` | News list item and loaded article body |
| `auth.username`, `auth.password`, `auth.login`, `onboarding.continue` | Login-flow regression test |

Every tagged element **also** has a real accessible name. `home.weather` carries `a11y_weather_card`;
`onboarding.continue` carries its visible label.

- [x] Add a `testTag` when the screenshot suite needs a handle.
- [ ] Never let a `testTag` be an element's only semantic identity.

## 8. Color and contrast

> If the text is smaller than 18sp, or if the text is bold and smaller than 14sp, use foreground and background colors that result in a color contrast ratio of at least 4.5:1. For all other text, set the color contrast ratio to at least 3:1.
> — [Android — Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps)

What the scheme delivers (full tables in [02 · Color](02-color.md)):

| Pair | Ratio | Verdict |
|---|---:|---|
| `onSurface` `#FFFFFF` on dark card `#1E1E1E` | 16.67 | ✅ |
| `onSurfaceVariant` `#B8B8BE` on dark card `#1E1E1E` | 8.44 | ✅ |
| `onSurface` `#1A1A1A` on light card `#FFFFFF` | 17.4 | ✅ |
| `onSurfaceVariant` `#5C5C63` on light card `#FFFFFF` | 6.63 | ✅ |
| `onPrimary` on each accent | ≥ 4.5 by construction | ✅ — computed in `Accent.onColor` |
| **Accent text on a light card** | 2.91 (Mint) – 4.74 (Blue) | ⚠️ Below 4.5 for four of five accents |

The last row is the app's one open contrast issue. It is confined to small decorative labels that never
carry unique information, and it is written up in [02 · Color §2.1](02-color.md#21-how-onprimary-is-chosen).

Color is never the only signal:

| Signal | Redundant cue |
|---|---|
| Selected accent swatch | 3 dp border **+** check icon **+** ", ausgewählt" |
| Login success (green) | Check icon replaces the label |
| Login failure (error red) | Error message text above the button |
| Disabled substitution card | 45 % title alpha **+** `enabled = false` **+** no chevron |
| Weather condition | Icon **+** localized text from `wmoRes(code)` |

> Never make color the only affordance or indicator for an available action. Utilize a component button, change of font weight, or even an icon to help inform your user that they can interact with the element.
> — [Android — Color for mobile design](https://developer.android.com/design/ui/mobile/guides/styles/color)

## 9. Text scaling and layout resilience

All type is in `sp`; all containers use `heightIn(min = …)`. `Home.kt` carries the comment
*"The row defines the card height (never clips at large font sizes)"* on the weather card. See
[03 · Typography §5](03-typography.md#5-scaling-and-the-92-sp-hero).

## 10. Review checklist

Before merging a UI change:

- [ ] Every interactive element reaches 48 dp, or its visual is wrapped in a 48 dp target.
- [ ] Every icon-only control has a `contentDescription` from a string resource, in **both** locales.
- [ ] Every decorative icon passes `contentDescription = null`.
- [ ] Multi-part rows are merged with `semantics(mergeDescendants = true)` and given one sentence.
- [ ] Section titles carry `heading()`.
- [ ] List items have unique descriptions.
- [ ] Text and non-text contrast checked against the light **and** dark scheme, for all five accents.
- [ ] No information conveyed by color alone.
- [ ] Layout survives the largest system font size without clipping.
- [ ] Any `testTag` added is accompanied by a real accessible name.

> Test your code to make sure the content description is delivered as expected. Android Lint, Compose testing, and manual and automated test tools can flag common issues and expose problems in your implementation.
> — [Android — Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps)

The project runs Android Lint with `warningsAsErrors = true` and `abortOnError = true`
(`app/build.gradle.kts`), so a missing content description on a lint-detectable element fails the build.

## Related

[02 · Color](02-color.md) · [03 · Typography](03-typography.md) · [06 · Components](06-components.md) · [09 · Iconography](09-iconography.md)

## Sources

| Source | Accessed |
|---|---|
| [Material 3 — Accessible design overview](https://m3.material.io/foundations/accessible-design/overview) | 2026-09-12 |
| [Android — Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps) | 2026-09-12 |
| [Compose — Accessibility API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) | 2026-09-12 |
| [Compose — Semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics) | 2026-09-12 |
| [Android — Color for mobile design](https://developer.android.com/design/ui/mobile/guides/styles/color) | 2026-09-12 |
| [Compose — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) | 2026-09-12 |
| App source: `kotlin/com/lgka/{Widgets,Home,News,Settings,Onboarding,WeatherScreen,PdfViewer,WebScreen,Theme,Prefs}.kt`, `res/values/strings.xml`, `res/values-en/strings.xml`, `app/build.gradle.kts`, `app/src/androidTest/kotlin/com/lgka/ScreenshotTest.kt` | 2026-09-12 |
