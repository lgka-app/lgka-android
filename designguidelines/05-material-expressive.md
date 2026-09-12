# 05 · Material 3 Expressive

| | |
|---|---|
| **Last updated** | 2026-09-12 |
| **Applies to** | LGKA+ Android, Android 17 / Material 3 Expressive |
| **Owner** | Luka Löhr |
| **Status** | Normative |

---

## 1. What "Expressive" means here

> M3 Expressive is an expansion of Material Design 3, including research-backed updates to theming, components, motion, typography, and more […]. M3 Expressive complements the Android 16 visual style and system UI.
> — [Compose — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)

> And to be clear — M3 Expressive isn't a new version of the system. We're not deprecating M3, and this isn't "M4."
> — [Material 3 — Start building with M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)

LGKA+ opts in at the theme level. Everything else follows from that single call.

## 2. The opt-in

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LgkaTheme(prefs: Prefs, content: @Composable () -> Unit) {
    …
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
```
<sub>`app/src/main/kotlin/com/lgka/Theme.kt`</sub>

| Fact | Value |
|---|---|
| Composable | `MaterialExpressiveTheme` (not `MaterialTheme`) |
| Motion scheme | `MotionScheme.expressive()` |
| Color scheme | Custom static light/dark, accent-driven — see [02 · Color](02-color.md) |
| Typography | Defaults — see [03 · Typography](03-typography.md) |
| Shapes | Defaults; components that need a specific radius pass `shape = CardShape` explicitly |
| Library | `androidx.compose.material3:material3:1.5.0-alpha28`, Compose BOM `2026.09.00` (`gradle/libs.versions.toml`) |

> **Warning**
> `MaterialExpressiveTheme`, `MotionScheme` and `LoadingIndicator` are all behind
> `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`, and the project builds with
> `allWarningsAsErrors = true` plus `lint { warningsAsErrors = true; abortOnError = true }`
> (`app/build.gradle.kts`). Add the opt-in at the narrowest scope that compiles — function level,
> not file level — exactly as `Theme.kt` and `Widgets.kt` do.

## 3. Motion scheme

> Expressive is Material's opinionated motion scheme, and should be used for most situations, particularly hero moments and key interactions. The expressive motion scheme overshoots the final values to add bounce.
> — [Material 3 — Motion overview](https://m3.material.io/styles/motion/overview)

> On Jetpack Compose, 21 Material components use the motion physics system by default.
> — [Material 3 — Motion overview](https://m3.material.io/styles/motion/overview)

Because `motionScheme` is set once at the theme, every Material component in the app —
`ModalBottomSheet`, `SegmentedButton`, `PullToRefreshBox`, `Card`, `Button`, `AlertDialog`,
`LoadingIndicator` — springs rather than eases, with no per-call-site code. The app is at **Level 1** of
the customization ladder, which is where it should stay:

> Level 1: Use a default motion scheme. The expressive and standard schemes should be sufficient for all motion needs. On Jetpack Compose, components use these schemes by default.
> — [Material 3 — Motion overview](https://m3.material.io/styles/motion/overview)

Details in [08 · Motion & feedback](08-motion-and-feedback.md).

## 4. The expressive `LoadingIndicator`

`LoadingIndicator` is one of the components introduced by the Expressive update
("Loading indicator (new)" in the component list on the M3 Expressive blog post). LGKA+ wraps it once and
uses the wrapper everywhere:

```kotlin
/** Material 3 Expressive loading indicator. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Loading(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.loading)
    LoadingIndicator(modifier = modifier.semantics { contentDescription = label })
}
```
<sub>`app/src/main/kotlin/com/lgka/Widgets.kt`</sub>

| Progress surface | Component | Why |
|---|---|---|
| Full-screen waits (news list, article, PDF open, weather, WebView) | `Loading()` → `LoadingIndicator` | The expressive default |
| Inline in a card row (timetable PDF being fetched) | `CircularProgressIndicator(18.dp, strokeWidth = 2.dp)` | Must fit a 44 dp row without dominating it |
| Inside the login button | `CircularProgressIndicator(22.dp, strokeWidth = 2.5.dp)` | Replaces the button label in place |
| Placeholder while list data loads | `SkeletonRow()` | Preserves layout, no spinner flash |

Both spinner call sites carry their own `contentDescription` (`R.string.loading_schedule`,
and the button keeps its accessible name) — see [10 · Accessibility](10-accessibility.md).

## 5. The expressive tactics, scored

The blog post lists seven tactics. Honest assessment of LGKA+ against each:

| # | Tactic | LGKA+ |
|---|---|---|
| 1 | **Use a variety of shapes** | ⚠️ Minimal. One 16 dp card radius, 12 dp icon tiles, 18 dp weather glass. Deliberate restraint, but the shape library and morphing are unused. |
| 2 | **Apply rich and nuanced colors** | ✅ Five student-chosen accents, tinted containers at 6–24 %, fixed neutral surfaces for structural calm. [02 · Color](02-color.md) |
| 3 | **Guide attention with typography** | ⚠️ Achieved through `FontWeight` rather than the new emphasized styles. [03 · Typography](03-typography.md) |
| 4 | **Contain content for emphasis** | ✅ The whole home hub is labelled card groups. [04 · Layout](04-layout-and-spacing.md#3-containment-and-grouping) |
| 5 | **Add fluid and natural motion** | ✅ `MotionScheme.expressive()` app-wide, plus a frame-driven sky shader. [08 · Motion](08-motion-and-feedback.md) |
| 6 | **Leverage component flexibility** | ❌ No breakpoint adaptation. [04 · Layout §6](04-layout-and-spacing.md#6-large-screens--a-known-gap) |
| 7 | **Combine tactics for hero moments** | ✅ Exactly two: the animated sky, and the 92 sp weather temperature. |

> Hero moments are brief, delightful, surprising, and unexpected. Stick to one or two hero moments in your product; too many moments can be overwhelming or distracting.
> — [Material 3 — Start building with M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)

Two hero moments. That is the budget, and it is spent.

## 6. Expressive components — adopted and not

| Component from the Expressive list | In LGKA+ |
|---|---|
| App bars | ✅ `TopAppBar` on home, news, PDF viewer, browser |
| Common buttons | ✅ `Button`, `OutlinedButton`, `TextButton`, `IconButton` |
| Loading indicator (new) | ✅ via `Loading()` |
| Progress indicators | ✅ `CircularProgressIndicator` for inline cases |
| Button groups (new) | ❌ |
| Extended FAB · FAB menu (new) · FABs | ❌ — the app has no FAB; its primary actions are cards and app-bar icons |
| Icon buttons | ✅ |
| Navigation bar · Navigation rail | ❌ — navigation is a back stack, not a tab bar. See [07 · Navigation](07-navigation.md) |
| Sliders | ❌ |
| Split button (new) | ❌ |
| Toolbars (new) | ❌ |

> **Note**
> The absence of a FAB and a navigation bar is structural, not an oversight. LGKA+ is a hub-and-detail
> app with one root: every destination is reached from the home hub, and nothing needs a persistent
> tab bar or a single dominant create action.

## 7. System-level Expressive behaviour

Two things come for free and must not be re-implemented:

> Ripple now uses a subtle sparkle to illuminate surfaces when pressed. Compose Material Ripple uses a platform RippleDrawable under the hood on Android, so sparkle ripple is available on Android 12 and above for all Material components.
> — [Compose — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)

> Overscroll now uses a stretch effect at the edge of scrolling containers. Stretch overscroll is on by default in scrolling container composables — for example, LazyColumn, LazyRow, and LazyVerticalGrid — in Compose Foundation 1.1.0 and above, regardless of API level.
> — [Compose — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)

Both apply to the app's `LazyColumn`s and every `Card(onClick = …)`. LGKA+ adds no custom `Indication`.

- [x] Take component defaults. Override only what the table in §4 lists.
- [x] Keep `MotionScheme.expressive()` as the single motion decision.
- [ ] Don't introduce a second theme composable or a nested `MaterialTheme`.
- [ ] Don't build a custom ripple or overscroll effect.

## Related

[02 · Color](02-color.md) · [03 · Typography](03-typography.md) · [06 · Components](06-components.md) · [08 · Motion & feedback](08-motion-and-feedback.md)

## Sources

| Source | Accessed |
|---|---|
| [Material 3 — Start building with M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive) | 2026-09-12 |
| [Material 3 — Motion overview](https://m3.material.io/styles/motion/overview) | 2026-09-12 |
| [Material 3 — Shape overview & principles](https://m3.material.io/styles/shape/overview-principles) | 2026-09-12 |
| [Compose — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) | 2026-09-12 |
| App source: `kotlin/com/lgka/{Theme,Widgets,Home,Onboarding}.kt`, `app/build.gradle.kts`, `gradle/libs.versions.toml` | 2026-09-12 |
