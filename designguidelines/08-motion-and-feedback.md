# 08 · Motion & feedback

| | |
|---|---|
| **Last updated** | 2026-09-12 |
| **Applies to** | LGKA+ Android, Android 17 / Material 3 Expressive |
| **Owner** | Luka Löhr |
| **Status** | Normative |

---

## 0. Why motion at all

> Animations are essential in a modern mobile app in order to realize a smooth and understandable user experience.
> — [Compose — Animation](https://developer.android.com/develop/ui/compose/animation/introduction)

Two words in that sentence set the budget for this app: *smooth* and *understandable*. Motion in LGKA+ exists
to make a state change legible, or to make the weather feel like weather. Nothing animates for its own sake.

## 1. One motion decision

```kotlin
MaterialExpressiveTheme(
    colorScheme = scheme,
    motionScheme = MotionScheme.expressive(),
    content = content,
)
```
<sub>`app/src/main/kotlin/com/lgka/Theme.kt`</sub>

That single line is the app's motion system. Every Material component below it springs.

> Motion schemes use springs. A spring is a combination of three attributes which control all motion behavior: stiffness, damping, and initial velocity.
> — [Material 3 — Motion overview](https://m3.material.io/styles/motion/overview)

> Expressive is Material's opinionated motion scheme, and should be used for most situations, particularly hero moments and key interactions. The expressive motion scheme overshoots the final values to add bounce.
> — [Material 3 — Motion overview](https://m3.material.io/styles/motion/overview)

> Spatial spring tokens are used for animations that move something on screen, for example the x and y position, rotation, size, rounded corners. This spring overshoots the final value and bounces into place. Effects spring tokens are used to animate properties such as color and opacity animations, where there shouldn't be any overshoot.
> — [Material 3 — Motion overview](https://m3.material.io/styles/motion/overview)

LGKA+ never names a spring token directly. It takes the scheme and lets components apply the right one.
That is Level 1 of the M3 customization ladder and it is where the app should stay — see
[05 · Material 3 Expressive §3](05-material-expressive.md#3-motion-scheme).

- [x] Change motion by changing the scheme, not by hand-tuning `animateFloatAsState` at a call site.
- [ ] Don't override `LocalMotionScheme` for a single component without writing down why.

## 2. Where motion appears

| Motion | Driver | Source |
|---|---|---|
| Bottom sheet enter / exit | `ModalBottomSheet` + expressive scheme | `Settings.kt` |
| Destination transitions, predictive back | `NavDisplay` + `enableOnBackInvokedCallback` | `MainActivity.kt`, `AndroidManifest.xml` |
| Pull-to-refresh | `PullToRefreshBox` | `Home.kt`, `News.kt` |
| Card press ripple | Platform sparkle ripple via `Card(onClick)` | `Widgets.kt` |
| Overscroll stretch | Compose Foundation default on every `LazyColumn` | `Home.kt`, `News.kt`, `PdfViewer.kt` |
| Segmented button selection | `SegmentedButton` | `Onboarding.kt` |
| Loading indicator | `LoadingIndicator` (Expressive component) | `Widgets.kt` |
| Scroll to a PDF search match | `listState.animateScrollToItem(…)` | `PdfViewer.kt` |
| Animated sky | `withFrameNanos` loop feeding an AGSL `RuntimeShader` | `WeatherScreen.kt` |
| Rain / snow particles | `withFrameNanos` loop over a `Canvas` | `WeatherScreen.kt` |
| New Year fireworks | `withFrameNanos` loop over a `Canvas`, 1 Jan Europe/Berlin only | `Settings.kt` |

The three frame-driven animations are custom because no component covers them: they are continuous
ambient motion, not a transition between two states. Compose classifies that category explicitly:

> Animate properties indefinitely — Use `InfiniteTransition` to animate properties continuously.
> — [Compose — Animation](https://developer.android.com/develop/ui/compose/animation/introduction)

> **Note — a known implementation deviation**
> LGKA+ drives all three with a raw `withFrameNanos` loop feeding a `Float` elapsed-time value, not with
> `rememberInfiniteTransition`. The loop is the right shape for a shader uniform (it needs monotonic wall-clock
> seconds, not a value oscillating between two bounds), and it is what makes the explicit
> `ANIMATOR_DURATION_SCALE` check in §6 possible. But it is hand-rolled where an API exists, and it means the
> animations are invisible to Compose's animation testing surface
> (*"Test animations — Learn how to write tests for your animations."*
> — [Compose — Animation](https://developer.android.com/develop/ui/compose/animation/introduction)).
> Keep the loops; know that they are the exception.

The rest of the app uses no `animate*AsState`, no `AnimatedVisibility`, no `AnimatedContent` and no
`animateContentSize()`. Every state change is either an instantaneous recomposition or a component's own
spring. That is a feature: there is exactly one place to change how the app feels.

## 3. Loading states

The rule: **match the placeholder to what you already know.**

| You know… | Show | Component |
|---|---|---|
| …the shape of the result (a list of cards) | A skeleton in that shape | `SkeletonRow()` |
| …nothing yet | A single centred indicator | `Loading()` |
| …that one row is busy | An inline 18 dp spinner in that row | `CircularProgressIndicator` |
| …that a button is busy | Replace the label with a 22 dp spinner | `CircularProgressIndicator` |

```kotlin
/** Placeholder row while data loads; announced once to TalkBack. */
@Composable
fun SkeletonRow() {
    val label = stringResource(R.string.loading)
    Card(shape = CardShape, modifier = Modifier.semantics { contentDescription = label }) {
        Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 18.dp), …
```
<sub>`app/src/main/kotlin/com/lgka/Widgets.kt`</sub>

Skeleton counts mirror the real content: 2 for the substitution pair, 4 for events, 1 for weather and the
schedule card. The result is that content **replaces** the placeholder in place; the list never reflows.

### 3.1 Cached data means no spinner at all

```kotlin
fun bootstrap() {
    if (bootstrapped) return
    bootstrapped = true
    viewModelScope.launch {
        loadAll(FetchMode.CacheAny)
        loadAll(FetchMode.CacheFirst)
        …
    }
}
```
<sub>`app/src/main/kotlin/com/lgka/HomeViewModel.kt`</sub>

The first pass paints whatever is cached, however stale; the second revalidates. A returning student sees
content immediately and a silent update a moment later. This is the app's most important perceived-performance
decision and it is invisible by design.

> Users want to be able to interact with your app or game as quickly as possible. […] as a general principle you should minimize the time between launch and first interaction.
> — [Android — What great technical quality looks like](https://developer.android.com/quality/technical)

> Most apps should run at 60 fps without any dropped or delayed frames. Poor rendering performance can cause users to perceive stuttering, also known as jank.
> — [Android — What great technical quality looks like](https://developer.android.com/quality/technical)

The PDF viewer is written against that second point: only visible pages are rasterized, at most six bitmaps
live in memory, and the non-thread-safe `PdfRenderer` is serialized behind a mutex (`PdfViewer.kt`).

## 4. Haptics

Feedback is haptic as well as visual at four points, each with a semantically chosen type:

| Action | `HapticFeedbackType` | Source |
|---|---|---|
| Top app bar action tap (news, sick note, settings) | `ContextClick` | `Home.kt` |
| Pull-to-refresh triggered | `LongPress` | `Home.kt` |
| Accent swatch selected | `SegmentTick` | `Onboarding.kt` |
| Login succeeded | `Confirm` | `Onboarding.kt` |
| Login failed | `Reject` | `Onboarding.kt` |

```kotlin
haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
```
<sub>`app/src/main/kotlin/com/lgka/Home.kt`</sub>

> User interface components give feedback to the device user by the way they respond to user interactions. Every component has its own way of responding to interactions, which helps the user know what their interactions are doing.
> — [Compose — Handling user interactions](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions)

- [x] Haptics accompany visual feedback; they never replace it.
- [ ] Don't add haptics to routine card taps. The ripple is the feedback there.

## 5. Interaction feedback

The app relies on Compose's built-in interaction handling rather than custom `Indication`:

> Button relies on Modifier.clickable to figure out whether the user clicked the button. If you're adding a typical button to your app, you can define the button's onClick code, and Modifier.clickable runs that code when appropriate.
> — [Compose — Handling user interactions](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions)

Disabled state is expressed twice, so it is never ripple-only: a substitution card with no plan gets
`enabled = false` **and** its icon tile alpha drops from 12 % to 6 % **and** its title drops to 45 % alpha
(`Home.kt` `SubCard`).

Transient outcomes use the right surface for their weight:

| Outcome | Surface | Source |
|---|---|---|
| Timetable not yet published | `Snackbar` via `SnackbarHostState` | `Home.kt` |
| Class switched inside the PDF | Inline `primary` feedback line under the search bar | `PdfViewer.kt` |
| No search matches | Same inline line | `PdfViewer.kt` |
| Login failed | `error` text above the button + button turns `error` for 700 ms | `Onboarding.kt` |

## 6. Respecting "animations off"

Both continuous custom animations check the system animation scale and skip entirely when it is zero:

```kotlin
val animate = remember {
    android.provider.Settings.Global.getFloat(context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
}
```
<sub>`app/src/main/kotlin/com/lgka/WeatherScreen.kt` — the same check guards `FireworksOverlay` in `Settings.kt`</sub>

With animations off, `SkyBox` still draws the sky (a static frame at `t = 0`) but stops the frame loop, and
precipitation particles and fireworks are not drawn at all. Nothing disappears; only motion stops.

This is also what makes the screenshot suite deterministic — `scripts/screenshots.sh` sets
`animator_duration_scale 0` before capturing.

- [x] Any new `withFrameNanos` loop must make the same check.
- [x] Static content must remain complete and legible with motion disabled.
- [ ] Don't gate information behind an animation.

## Related

[05 · Material 3 Expressive](05-material-expressive.md) · [06 · Components](06-components.md) · [10 · Accessibility](10-accessibility.md)

## Sources

| Source | Accessed |
|---|---|
| [Material 3 — Motion overview](https://m3.material.io/styles/motion/overview) | 2026-09-12 |
| [Compose — Handling user interactions](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions) | 2026-09-12 |
| [Compose — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) | 2026-09-12 |
| [Android — What great technical quality looks like](https://developer.android.com/quality/technical) | 2026-09-12 |
| [Compose — Animation](https://developer.android.com/develop/ui/compose/animation/introduction) | 2026-09-12 |
| App source: `kotlin/com/lgka/{Theme,Widgets,Home,HomeViewModel,WeatherScreen,Settings,Onboarding,PdfViewer,News}.kt`, `scripts/screenshots.sh` | 2026-09-12 |
