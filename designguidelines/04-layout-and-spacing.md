# 04 · Layout & spacing

| | |
|---|---|
| **Last updated** | 2026-09-12 |
| **Applies to** | LGKA+ Android, Android 17 / Material 3 Expressive |
| **Owner** | Luka Löhr |
| **Status** | Normative |

---

## 1. The spacing scale

Every measurement in the app comes from a small set of values. There is no `Dimens` object; the values
live inline, so the table below is the specification.

| Value | Role | Where |
|---|---|---|
| **4 dp** | Hairline gaps, PDF page separation | `Home.kt`, `PdfViewer.kt` |
| **6 dp** | Gap inside a chip row, gap between icon and label in a small button | `News.kt`, `Home.kt` |
| **8 dp** | Tight vertical rhythm inside a card | everywhere |
| **12 dp** | **The list gap** — vertical arrangement between cards | `Home.kt`, `News.kt`, `Onboarding.kt` |
| **14 dp** | Gap between a 44 dp icon tile and its text column; weather card stack gap | `Widgets.kt`, `WeatherScreen.kt` |
| **16 dp** | Card inner padding; sheet horizontal padding; settings tile padding | `News.kt`, `Settings.kt`, `Onboarding.kt` |
| **18 dp** | Horizontal padding inside a `HomeCard` row | `Widgets.kt` |
| **20 dp** | **Screen margin** on the home and news lists | `Home.kt`, `News.kt` |
| **24 dp** | Screen padding on onboarding and auth; centred error card padding | `Onboarding.kt`, `Home.kt` |
| **32 dp** | Full-screen error state padding; onboarding block separation | `News.kt`, `Onboarding.kt` |
| **48 dp** | Minimum touch target; auth field group separation | `Widgets.kt`, `Settings.kt`, `Onboarding.kt` |

### 1.1 Margins

| Surface | Horizontal margin | Source |
|---|---|---|
| Home hub list | 20 dp | `LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp))` in `Home.kt` |
| News list & article | 20 dp | `News.kt` |
| Weather screen | 16 dp | `WeatherScreen.kt` |
| Settings bottom sheet | 16 dp (+ 32 dp bottom) | `Settings.kt` |
| Onboarding & auth | 24 dp | `Onboarding.kt` |
| Krankmeldung info | 16 dp | `WebScreen.kt` |

> **Note**
> The 20 / 16 / 24 split is not an accident but it is not a system either. If you are adding a screen,
> use **20 dp** — it is the margin of the two screens a student sees most.

## 2. Card geometry

One shape constant governs every list card in the app:

```kotlin
/** Card corner radius shared by every list card (DESIGN_GUIDELINES.md §1.3). */
val CardShape = RoundedCornerShape(16.dp)
```
<sub>`app/src/main/kotlin/com/lgka/Widgets.kt`</sub>

| Element | Radius | Source |
|---|---|---|
| Every list / content card | **16 dp** (`CardShape`) | `Widgets.kt` |
| Icon tile behind a card icon | 12 dp | `Widgets.kt` `IconTile`, `Home.kt` `DateTile` |
| Onboarding feature icon tile | 12 dp | `Onboarding.kt` |
| Krankmeldung info icon tile | 14 dp | `WebScreen.kt` |
| Weather glass card & stat tile | 18 dp | `WeatherScreen.kt` |
| Primary CTA buttons (onboarding, auth, Krankmeldung) | 16 dp | `Onboarding.kt`, `WebScreen.kt` |
| Accent swatch | `swatchSize * 0.32` dp — 18 dp at 56 dp, 10 dp at 32 dp | `Onboarding.kt` `AccentRow` |
| Skeleton placeholder bars | 4 dp | `Widgets.kt` |
| News tag pill | 50 % (`RoundedCornerShape(50)`) | `News.kt` |
| Temperature range bar | 3 dp | `WeatherScreen.kt` |

This is a narrower range than the M3 Expressive shape scale offers, and that is a considered choice:
the app's expressive tension comes from the sky shader and the accent, not from shape variety.

> Be intentional when using shapes in product UI. Don't compromise clarity for the sake of visual design.
> — [Material 3 — Shape overview & principles](https://m3.material.io/styles/shape/overview-principles)

### 2.1 Card heights

`HomeCard` sets a **minimum** height, never a fixed one:

```kotlin
Row(
    Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 18.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically, content = content,
)
```
<sub>`app/src/main/kotlin/com/lgka/Widgets.kt`</sub>

| Card | Minimum height |
|---|---|
| `HomeCard` (substitution, schedule, events, empty states) | 76 dp |
| `SkeletonRow` | 76 dp (fixed, it holds no user text) |
| Weather card row | 112 dp |
| Weather stat tile | 64 dp |
| Primary CTA button | 52 dp |
| Settings tile row | 48 dp |

- [x] Use `heightIn(min = …)` on anything containing user text so it grows with the font-size setting.
- [x] Keep the 12 dp gap between cards; it is what makes the containment read.
- [ ] Don't nest a card inside a card. The app never does.

## 3. Containment and grouping

> Contain content for emphasis. Organize content into logical groupings or containers.
> — [Material 3 — Start building with M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)

> Use containment to group related content to guide the user through content and actions. Cards using explicit containment to group content with related actions.
> — [Android — Layout basics](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-basics)

The home hub is a single `LazyColumn` of labelled groups, each introduced by a `SectionHeader`
(`titleMedium`, `FontWeight.Bold`, `Modifier.semantics { heading() }`):

```
[weather card]
Vertretungsplan     ← SectionHeader
  [today] [tomorrow]
Stundenplan         ← SectionHeader
  [class card] (+ "Klasse eingeben" text button)
Bevorstehende Termine ← SectionHeader
  [event] × up to 4
```
<sub>`app/src/main/kotlin/com/lgka/Home.kt`</sub>

The weather card is deliberately **not** under a header: it is the visual opening of the screen,
and the only card that carries imagery.

Settings groups the same way, but with a label above each card rather than a heading
(`labelSmall` / `onSurfaceVariant`: "DARSTELLUNG", "MEHR") and dividers *inside* the card:

> Dividers: dividers can separate regions in cards or indicate areas of a card that can expand. Use full-width dividers for content that can be expanded; use inset dividers, which don't run the full width of a card, to separate related content.
> — [Material 3 — Cards guidelines](https://m3.material.io/components/cards/guidelines)

`Settings.kt` uses `HorizontalDivider(Modifier.padding(start = 16.dp))` between tiles — inset, matching
the icon column, for related content that does not expand. That is the correct variant.

## 4. Alignment

> Provide consistent alignment between similar content and UI elements. Do: Establish consistent spacing between like elements. Don't: Disrupt readability by inconsistently spacing like elements, which can make designs appear haphazard.
> — [Android — Layout basics](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-basics)

Every home card follows one row grammar:

```
[ 44 dp tile ] 14 dp [ title / subtitle column, weight 1f ] [ trailing: chevron 14 dp | spinner 18 dp | retry button ]
```

The tile is always 44 dp, the gap always 14 dp, the chevron always `Icons.AutoMirrored.Filled.ArrowForwardIos`
at 14 dp tinted `onSurfaceVariant`. The `AutoMirrored` variant matters: `android:supportsRtl="true"` is set
in the manifest, and M3 asks for it —

> Design for bidirectionality to support both left-to-right (LTR) and right-to-left (RTL) languages.
> — [Material 3 — Layout overview](https://m3.material.io/foundations/layout/understanding-layout/overview)

- [x] Use `Icons.AutoMirrored.*` for any directional glyph (back, forward, open-in-new, logout).
- [x] Keep the 44 / 14 / weight(1f) grammar for a new home card.

## 5. System bars and edge-to-edge

`MainActivity.onCreate` calls `enableEdgeToEdge()` before `setContent`, and both XML themes make the bars
transparent with `enforceNavigationBarContrast=false` (see [02 · Color §8](02-color.md#8-system-bar-colors)).

> Keep the system status and navigation bars transparent or translucent and draw content behind these bars to go edge-to-edge.
> — [Android — System bars](https://developer.android.com/design/ui/mobile/guides/foundations/system-bars)

> Transparent gesture navigation bars are always recommended. Do: Keep the gesture navigation bar transparent. Don't: Add a background to the gesture navigation bar.
> — [Android — System bars](https://developer.android.com/design/ui/mobile/guides/foundations/system-bars)

How insets are consumed, per surface:

| Surface | Mechanism | Source |
|---|---|---|
| Home, News, PDF viewer, Krankmeldung info, in-app browser | `Scaffold` + `Modifier.padding(padding)` | `Home.kt`, `News.kt`, `PdfViewer.kt`, `WebScreen.kt` |
| Weather screen (full-bleed sky) | `Modifier.safeDrawingPadding()` on the scrolling column **and** on the floating top bar | `WeatherScreen.kt` |
| Onboarding & auth (no app bar) | `Modifier.safeDrawingPadding()` on the root column | `Onboarding.kt` |
| PDF viewer dialog | `DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)` then its own `Scaffold` | `PdfViewer.kt` |

The manifest sets `android:windowSoftInputMode="adjustResize"`, which is what lets the login fields stay
visible above the keyboard:

> Focus user inputs. If the keyboard is present, move the input up into a focused state or consider attaching the text input to the keyboard. […] Don't hide inputs.
> — [Android — Layout basics](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-basics)

- [x] Every new full-screen surface must consume insets — `Scaffold` padding or `safeDrawingPadding()`.
- [ ] Don't place a touch target under the gesture-navigation inset.
- [ ] Don't set a solid `statusBarColor` or `navigationBarColor`; the themes deliberately do not.

## 6. Large screens — a known gap

The screenshot suite captures a **tablet** form factor (`pixel_tablet`, `scripts/screenshots.sh`), and the
app runs correctly there, but there is **no breakpoint-aware layout**. `WindowSizeClass` is never used;
`BoxWithConstraints` appears once, in `PdfViewer.kt`, and only to compute a bitmap render width. There is no
`NavigationRail` and no list-detail pane.

The guidance the app does not yet meet:

> Adapt layouts to compact, medium, expanded, large, and extra-large breakpoints (previously window size classes).
> — [Material 3 — Layout overview](https://m3.material.io/foundations/layout/understanding-layout/overview)

> In mobile layouts, components such as lists or cards are stretched to fit the full width of the screen; when designing for large screens with an expanded breakpoint, use multiple columns to display content. Avoid extending UI elements across the screen when possible.
> — [Material 3 — Cards guidelines](https://m3.material.io/components/cards/guidelines)

> NavigationRail is used for small-to-medium size tablets or phones in landscape mode. It provides ergonomics to users and improves the user experience for those devices.
> — [Compose — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)

**Current behaviour on a tablet:** full-width cards at a 20 dp margin, so a card can be ~1000 dp wide with a
44 dp icon at one end and a 14 dp chevron at the other. It is usable; it is not good.

**Recommended fix, when it is taken on:** constrain the content column to a maximum width (roughly 600 dp)
and centre it, before considering multiple columns. That is one modifier, it requires no navigation change,
and it fixes the worst of the stretch. Record the decision here when it lands.

> **Note**
> The app is portrait-agnostic — `android:configChanges` includes `orientation|screenSize|screenLayout`,
> and state lives in a `ViewModel` and `SharedPreferences`, so rotation never loses data. Rotation was
> verified by construction, not by a captured screenshot.

## Related

[02 · Color](02-color.md) · [05 · Material 3 Expressive](05-material-expressive.md) · [06 · Components](06-components.md) · [13 · Screens](13-screens.md)

## Sources

| Source | Accessed |
|---|---|
| [Android — Layout basics](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-basics) | 2026-09-12 |
| [Android — System bars](https://developer.android.com/design/ui/mobile/guides/foundations/system-bars) | 2026-09-12 |
| [Material 3 — Layout overview](https://m3.material.io/foundations/layout/understanding-layout/overview) | 2026-09-12 |
| [Material 3 — Cards guidelines](https://m3.material.io/components/cards/guidelines) | 2026-09-12 |
| [Material 3 — Shape overview & principles](https://m3.material.io/styles/shape/overview-principles) | 2026-09-12 |
| [Material 3 — Start building with M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive) | 2026-09-12 |
| [Compose — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) | 2026-09-12 |
| App source: `kotlin/com/lgka/{Widgets,Home,News,WeatherScreen,Onboarding,Settings,PdfViewer,WebScreen,MainActivity}.kt`, `AndroidManifest.xml`, `scripts/screenshots.sh` | 2026-09-12 |
