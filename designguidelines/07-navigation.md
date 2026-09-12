# 07 · Navigation

| | |
|---|---|
| **Last updated** | 2026-09-12 |
| **Applies to** | LGKA+ Android, Android 17 / Material 3 Expressive, Navigation 3 `1.1.7` |
| **Owner** | Luka Löhr |
| **Status** | Normative |

---

## 1. Shape of the app

LGKA+ is a **single-root hub-and-detail app**. There is no bottom navigation bar, no navigation rail and
no drawer. Everything is reached from the home hub, and everything returns to it.

```
RootNav                                   (gate, not a destination)
 ├─ !onboardingCompleted ──► OnboardingFlow  (welcome → features → accent → appearance → auth)
 ├─ !isSignedIn          ──► AuthScreen
 └─ else                 ──► MainNav
                              └─ NavDisplay(backStack)
                                   HomeRoute            ← always the root
                                   WeatherRoute
                                   NewsRoute
                                   NewsDetailRoute(url) ← re-entrant
                                   KrankmeldungInfoRoute
                                   KrankmeldungFormRoute
                                   BugReportRoute
```
<sub>`app/src/main/kotlin/com/lgka/MainActivity.kt`</sub>

Two surfaces sit **outside** the back stack, as overlays over the current destination:

| Overlay | Component | Dismissal |
|---|---|---|
| Settings | `ModalBottomSheet` | Scrim tap, swipe down, system back |
| PDF viewer | full-screen `Dialog` | Close (×) in its app bar, system back |

## 2. Navigation 3 in code

```kotlin
@Serializable sealed interface Route : NavKey
@Serializable data object HomeRoute : Route
@Serializable data object WeatherRoute : Route
@Serializable data object NewsRoute : Route
@Serializable data class NewsDetailRoute(val url: String) : Route
@Serializable data object KrankmeldungInfoRoute : Route
@Serializable data object KrankmeldungFormRoute : Route
@Serializable data object BugReportRoute : Route

@Composable
fun MainNav() {
    val backStack = rememberNavBackStack(HomeRoute)
    val pop: () -> Unit = { backStack.removeLastOrNull() }
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider { … },
    )
}
```
<sub>`app/src/main/kotlin/com/lgka/MainActivity.kt`</sub>

| Property | Value |
|---|---|
| Back stack | `rememberNavBackStack(HomeRoute)` — a real list you mutate, not a string graph |
| Routes | `@Serializable` Kotlin types implementing `NavKey`; arguments are typed fields, not string parameters |
| Push | `backStack.add(route)` |
| Pop | `backStack.removeLastOrNull()` |
| Libraries | `androidx.navigation3:navigation3-runtime` and `navigation3-ui` `1.1.7`, plus `lifecycle-viewmodel-navigation3` |

- [x] Add a destination by adding a `@Serializable` object or data class to `Route` **and** an `entry<…>` block.
- [x] Pass arguments as typed constructor fields (`NewsDetailRoute(val url: String)`).
- [ ] Don't hand a screen the whole back stack. Screens receive `onBack: () -> Unit` and
      `onNavigate: (Route) -> Unit` callbacks only.

## 3. Gating, not routing

`RootNav` re-evaluates on every recomposition rather than navigating between onboarding, auth and the app:

```kotlin
@Composable
fun RootNav() {
    val container = LocalContainer.current
    val prefs = container.prefs
    when {
        !prefs.onboardingCompleted -> OnboardingFlow()
        !prefs.isSignedIn(container.credentials) -> AuthScreen()
        else -> MainNav()
    }
}
```
<sub>`app/src/main/kotlin/com/lgka/MainActivity.kt`</sub>

`Prefs` exposes its values as Compose state (`mutableStateOf` behind a `ReadWriteProperty` in `Prefs.kt`),
so logging out from the settings sheet swaps the whole tree back to `AuthScreen` with no navigation call
and no possibility of an authenticated screen surviving underneath.

Onboarding itself is **local state**, not routes:

```kotlin
var step by remember { mutableIntStateOf(0) }
when (step) {
    0 -> WelcomeStep { step = 1 }
    1 -> FeaturesStep { step = 2 }
    2 -> AccentStep { step = 3 }
    3 -> AppearanceStep { step = 4 }
    else -> AuthScreen()
}
```
<sub>`app/src/main/kotlin/com/lgka/Onboarding.kt`</sub>

> **Warning**
> Because onboarding steps are not back-stack entries, **system back does not step backwards through
> onboarding**. A student who taps back on the accent step leaves the app. This is a real gap. Fixing it
> means either giving `OnboardingFlow` a `BackHandler` per step or lifting the steps into the back stack;
> the first is smaller. Do not "fix" it by adding a visible back button — the flow is designed as forward-only.

## 4. Back handling and predictive back

The app opts into the platform back callback at the manifest level:

```xml
android:enableOnBackInvokedCallback="true"
```
<sub>`app/src/main/AndroidManifest.xml`</sub>

That is what lets Android run the **predictive back** animation: `NavDisplay` receives the back gesture
through its `onBack` lambda, and the system renders the peek-and-return transition. There is no
`BackHandler` anywhere in `app/src/main/kotlin` — back is entirely delegated.

| Surface | Back behaviour |
|---|---|
| Any `NavDisplay` destination | Pops one entry (`removeLastOrNull`) |
| Home (back stack empty) | Leaves the app |
| Settings sheet | `ModalBottomSheet` dismisses itself |
| PDF viewer | `Dialog` dismisses via `onDismissRequest = onClose` |
| Onboarding | ⚠️ Leaves the app — see §3 |
| In-app browser | Pops to the previous destination; **does not** navigate the WebView's own history |

> Back returns to the previous view.
> — [Android — System bars](https://developer.android.com/design/ui/mobile/guides/foundations/system-bars)

> Users can choose from various navigation configurations including gesture navigation and adaptive navigation. To deliver an optimal user experience, account for multiple types of navigation.
> — [Android — System bars](https://developer.android.com/design/ui/mobile/guides/foundations/system-bars)

LGKA+ handles both by never drawing its own back affordance at the screen edge and by keeping the gesture
navigation bar transparent — see [04 · Layout §5](04-layout-and-spacing.md#5-system-bars-and-edge-to-edge).

## 5. Special flows

### 5.1 Sick note — a two-step gate

```kotlin
onNavigate(if (prefs.krankmeldungInfoShown) KrankmeldungFormRoute else KrankmeldungInfoRoute)
```
<sub>`app/src/main/kotlin/com/lgka/Home.kt`</sub>

First tap ever shows `KrankmeldungInfoScreen` — two cards explaining that the form belongs to the school
and who to contact. Continuing sets `krankmeldungInfoShown = true`, **replaces** the info entry rather than
stacking on it, and pushes the form:

```kotlin
KrankmeldungInfoScreen(onBack = pop, onContinue = {
    backStack.removeLastOrNull()
    backStack.add(KrankmeldungFormRoute)
})
```
<sub>`app/src/main/kotlin/com/lgka/MainActivity.kt`</sub>

Back from the form therefore returns to the home hub, not to the disclaimer. Every later tap goes straight
to the form.

### 5.2 News — a re-entrant detail route

`NewsDetailRoute` can push another `NewsDetailRoute` (the "Weitere Neuigkeiten" cards at the bottom of an
article). The back stack grows, back unwinds article by article. External links and article images leave via
`Intent.ACTION_VIEW` to the system browser, signalled by the `OpenInNew` glyph.

### 5.3 PDF viewer — a dialog, not a destination

```kotlin
Dialog(onDismissRequest = onClose,
       properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    Surface(Modifier.fillMaxSize()) { PdfViewerContent(request, onClose) }
}
```
<sub>`app/src/main/kotlin/com/lgka/PdfViewer.kt`</sub>

It is a `Dialog` because it can be opened from anywhere on the home hub and must return exactly where it
came from without touching the back stack. Inside:

| Feature | Behaviour |
|---|---|
| Rendering | `LazyColumn`, one page per item, lazy rasterization at the container width, LRU cache of 6 bitmaps, `PdfRenderer` serialized behind a mutex |
| Placeholder | Each page reserves its real `aspectRatio` before its bitmap exists, so scrolling never jumps |
| Zoom | `detectTransformGestures` → `graphicsLayer`, scale clamped 1×–4×, pan only while zoomed |
| Search | Toggled from the app bar; `Search` IME action; match counter `3/12` with up/down `IconButton`s; animated scroll to each match |
| **Cross-PDF class switching** | Typing a class like `j11` or `7b` in the search field selects that class, and if it lives in the other PDF the viewer fetches that PDF, retitles itself and scrolls to the class page |
| Share | Copies to the cache dir under a friendly name (`LGKA_Stundenplan_…pdf` / `LGKA_Vertretungsplan_…pdf`) and shares through `FileProvider` authority `com.lgka.files` |
| Close | × in the navigation-icon slot |

> **Note**
> Cross-PDF class switching is navigation that looks like search. It changes the document under the
> student without a screen transition, so it always writes a feedback line ("Deine Klasse wurde auf
> Klasse 7B geändert.") in `primary` under the search bar. If you touch this path, keep the feedback.

### 5.4 In-app browser

`WebScreen` is used for two destinations, with different confinement:

| Route | URL | `confineToHost` |
|---|---|---|
| `KrankmeldungFormRoute` | `https://drkrankmeldung.lgka-online.de` | `lgka-online.de` |
| `BugReportRoute` | a Google Forms URL | *none* |

```kotlin
/** Host suffix match: "lgka-online.de" confines to that domain and its subdomains only. */
fun isConfined(host: String?, confined: String): Boolean =
    host != null && (host == confined || host.endsWith(".$confined"))
```
<sub>`app/src/main/kotlin/com/lgka/WebScreen.kt`</sub>

A link outside the confined host is handed to the system browser instead of being loaded in place. The
WebView runs with `LOAD_NO_CACHE`, clears cookies on creation, and answers HTTP basic auth **only** for the
school's own host with the stored credentials — see [11 · Onboarding, login & privacy](11-onboarding-login-and-privacy.md).

Its three states are a loading overlay (`Loading()` + "Lädt…"), the page, and a full-screen error with
`WifiOff`, a hint and a retry that increments a `reloadToken` to build a fresh `WebView`.

## 6. Why there is no navigation bar

The navigation bar has three stated preconditions, and LGKA+ meets none of them:

> The navigation bar allows users to switch between destinations in an app. You should use navigation bars for:
> - Three to five destinations of equal importance
> - Compact window sizes
> - Consistent destinations across app screens
>
> — [Compose — Navigation bar](https://developer.android.com/develop/ui/compose/components/navigation-bar)

> NavigationBar is used for compact devices when you want to target 5 or less destinations
> — [Compose — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)

| Precondition | LGKA+ |
|---|---|
| Three to five destinations **of equal importance** | ❌ One root (home) and six details. Home is where a student spends nearly all their time; the sick note is used a handful of times a year. They are not peers. |
| Compact window sizes | Partly — the app also runs on tablets, where a rail would be the counterpart component |
| **Consistent** destinations across app screens | ❌ Three of the six are transient (sick note, bug report, PDF) and two are reached only from another destination (news detail, Krankmeldung form) |

A tab bar would therefore misrepresent the structure and put a permanently-visible control on screen for
actions that are used rarely. The three most-used detail destinations are top app bar actions on the home hub
instead — **news**, **sick note**, **settings** — which keeps them one tap away without claiming they are peers
of the hub.

> **Note**
> `NavigationBarItem` exposes `selected`, `onClick()`, `label` and `icon`
> ([Compose — Navigation bar](https://developer.android.com/develop/ui/compose/components/navigation-bar)),
> and the `selected` state is the part that has no meaning here: with a single root there is nothing for a
> persistent bar to indicate. If the app ever grows a second genuine root — a full timetable browser, say —
> revisit this section rather than bolting a bar onto the current structure.

## Related

[06 · Components](06-components.md) · [08 · Motion & feedback](08-motion-and-feedback.md) · [11 · Onboarding, login & privacy](11-onboarding-login-and-privacy.md) · [13 · Screens](13-screens.md)

## Sources

| Source | Accessed |
|---|---|
| [Android — System bars](https://developer.android.com/design/ui/mobile/guides/foundations/system-bars) | 2026-09-12 |
| [Compose — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) | 2026-09-12 |
| [Compose — Navigation bar](https://developer.android.com/develop/ui/compose/components/navigation-bar) | 2026-09-12 |
| [Material 3 — Cards guidelines](https://m3.material.io/components/cards/guidelines) | 2026-09-12 |
| App source: `kotlin/com/lgka/{MainActivity,Onboarding,Home,News,PdfViewer,WebScreen,Settings,Prefs}.kt`, `AndroidManifest.xml`, `gradle/libs.versions.toml` | 2026-09-12 |
