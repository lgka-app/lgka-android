# 14 · Android & Material reference

| | |
|---|---|
| **Last updated** | 2026-09-12 |
| **Applies to** | LGKA+ Android, Android 17 / Material 3 Expressive |
| **Owner** | Luka Löhr |
| **Status** | Reference — a cited digest of the official guidance behind these files |

---

Each entry gives the source URL, a faithful short summary, and how LGKA+ applies it or where it deviates
and why. Quoted blocks are verbatim from the captured source.

## Contents

- [Material 3](#material-3)
  - [Color roles](#color-roles) · [Cards](#cards) · [Typography](#typography-overview) · [Layout](#layout-overview) · [Motion](#motion-physics-system) · [Shape](#shape--overview--principles) · [M3 Expressive](#start-building-with-m3-expressive) · [Accessible design](#accessible-design-overview)
- [developer.android.com — design](#developerandroidcom--design)
  - [Color](#android-color-for-mobile-design) · [System bars](#android-system-bars) · [Layout basics](#layout-basics)
- [developer.android.com — develop & quality](#developerandroidcom--develop--quality)
  - [Material 3 in Compose](#material-design-3-in-compose) · [Semantics](#semantics) · [API defaults](#api-defaults) · [Handling interactions](#handling-user-interactions) · [Accessibility](#make-apps-more-accessible) · [Technical quality](#what-great-technical-quality-looks-like)
  - [Dark theme](#implement-dark-theme) · [Adaptive icons](#adaptive-icons) · [Navigation bar](#navigation-bar) · [Animation](#animation) · [Fonts](#work-with-fonts)
- [A note on relocated pages](#a-note-on-relocated-pages)

---

# Material 3

## Color roles

**URL** <https://m3.material.io/styles/color/roles> · accessed 2026-09-12

**Summary.** Twenty-six standard color roles in six groups (primary, secondary, tertiary, error, surface,
outline). Roles describe use, not hue. `On`-prefixed roles are for text and icons on their paired parent;
`Container` roles are fills and must not be used for text; `Variant` roles are the lower-emphasis
alternative. Five surface-container roles build hierarchy. Outline is for boundaries, outline variant for
dividers and decoration.

> Color roles ensure accessibility. The color system is built on accessible color pairings. These color pairs provide an accessible minimum 3:1 contrast.
> — [Material 3 — Color roles](https://m3.material.io/styles/color/roles)

> Don't use the outline color for dividers (use outline variant). Don't use the outline color for components that contain multiple elements, such as cards (use outline variant).
> — [Material 3 — Color roles](https://m3.material.io/styles/color/roles)

**LGKA+ applies it.** `Theme.kt` populates primary, secondary, their containers, background, surface, all
five surface containers, `onSurfaceVariant`, outline and outline variant. Pairings are respected throughout
(see the mapping table in [02 · Color §6](02-color.md#6-role-mapping-in-practice)). Dividers use
`HorizontalDivider`, which resolves to `outlineVariant`.

**Deviations.**
1. **No tertiary.** `primary` and `secondary` are the same accent value; tertiary is left at its default and
   never referenced. The app has one accent by design.
2. **Surfaces are not derived from a tonal palette.** They are fixed neutrals so the layout reads identically
   under all five accents and matches the iOS build.
3. **Accent-as-text on light surfaces** falls below 4.5:1 for four of five accents — documented at
   [02 · Color §2.1](02-color.md#21-how-onprimary-is-chosen).

---

## Cards

**URL** <https://m3.material.io/components/cards/guidelines> · accessed 2026-09-12

**Summary.** A card holds content and actions on a single subject. Three variants — elevated, filled,
outlined — differ only in style. The container is the only required element. A card can be one large touch
target. Cards in a collection are coplanar. Cards must not scroll internally. Layering text on images needs
a scrim. On large screens, prefer multiple columns over stretching.

> Use a card to display content and actions on a single topic. Cards should be easy to scan for relevant and actionable information.
> — [Material 3 — Cards guidelines](https://m3.material.io/components/cards/guidelines)

> Layering text, icons, and images: it isn't recommended to place text or icons on images. If it's necessary, ensure the background image provides sufficient contrast for the text to meet accessibility standards. Add a translucent scrim or bounding shape beneath the text or icon to help ensure proper contrast.
> — [Material 3 — Cards guidelines](https://m3.material.io/components/cards/guidelines)

**LGKA+ applies it.** Filled variant everywhere; one subject per card; whole-card targets via
`Card(onClick)`; no internal scrolling; the weather surfaces use 32 % black scrims over the sky.

**Deviation.** Large-screen adaptation is absent — cards stretch to full width on tablets. See
[04 · Layout §6](04-layout-and-spacing.md#6-large-screens--a-known-gap).

---

## Typography overview

**URL** <https://m3.material.io/styles/typography/overview> · accessed 2026-09-12

**Summary.** The M3 type scale has 30 styles: 15 baseline and 15 emphasized (added May 2025). Five role
families — display, headline, title, body, label. Variable fonts (Roboto Flex, Google Sans Flex) allow
editorial expression. An August 2026 update adds automatic line-height adaptation by language script height.

> Material's type scale includes fifteen baseline type styles, the same as before, and fifteen new emphasized type styles. The emphasized type styles add more expression to highlighted moments.
> — [Material 3 — Typography overview](https://m3.material.io/styles/typography/overview)

**LGKA+ applies it.** Baseline scale, nine of fifteen styles, unmodified.

**Deviations.** Emphasized styles and variable fonts are not used; emphasis comes from `FontWeight`
overrides. Written up at [03 · Typography §6](03-typography.md#6-what-is-not-done) as a backlog item.

---

## Layout overview

**URL** <https://m3.material.io/foundations/layout/understanding-layout/overview> · accessed 2026-09-12

**Summary.** Layout organizes elements, signals hierarchy and draws attention. Adapt across breakpoints
(compact → extra-large; window size classes were renamed to breakpoints in May 2026). Start from canonical
layouts, design for bidirectionality, apply consistent arrangement, sizing and spacing. Defines margin, gap,
pane, rail, safety region, scaffold.

> Adapt layouts to compact, medium, expanded, large, and extra-large breakpoints (previously window size classes).
> — [Material 3 — Layout overview](https://m3.material.io/foundations/layout/understanding-layout/overview)

> Design for bidirectionality to support both left-to-right (LTR) and right-to-left (RTL) languages.
> — [Material 3 — Layout overview](https://m3.material.io/foundations/layout/understanding-layout/overview)

**LGKA+ applies it.** Consistent margins and gaps ([04 · Layout §1](04-layout-and-spacing.md#1-the-spacing-scale));
`Scaffold` on every app-bar screen; `android:supportsRtl="true"` with `Icons.AutoMirrored` for every
directional glyph.

**Deviation.** No breakpoint adaptation. The single largest gap in the app's design system.

---

## Motion physics system

**URL** <https://m3.material.io/styles/motion/overview> · accessed 2026-09-12

**Summary.** M3 Expressive replaced easing-and-duration with a spring-based physics system. Two preset
schemes: **expressive** (overshoots, bouncy) and **standard** (eases, utilitarian). Springs have stiffness,
damping and initial velocity. Spring tokens split into spatial (overshoot allowed) and effects (no
overshoot), each at default, fast and slow speeds. Three levels of customization; level 1 is using a preset
scheme. Twenty-one Compose Material components use the system by default.

> Expressive is Material's opinionated motion scheme, and should be used for most situations, particularly hero moments and key interactions.
> — [Material 3 — Motion overview](https://m3.material.io/styles/motion/overview)

> Level 1: Use a default motion scheme. The expressive and standard schemes should be sufficient for all motion needs. On Jetpack Compose, components use these schemes by default.
> — [Material 3 — Motion overview](https://m3.material.io/styles/motion/overview)

**LGKA+ applies it.** `MotionScheme.expressive()` at the theme, level 1, no per-component overrides, no
hand-named spring tokens. See [08 · Motion](08-motion-and-feedback.md).

**Beyond the system.** Three continuous `withFrameNanos` animations (sky shader, precipitation, fireworks)
are outside the scheme because they are ambient, not transitional. Each checks
`ANIMATOR_DURATION_SCALE` and stops when animations are disabled.

---

## Shape — overview & principles

**URL** <https://m3.material.io/styles/shape/overview-principles> · accessed 2026-09-12

**Summary.** The M3 shape system adds 35 shapes, built-in morphing, and updated corner-radius tokens
(large 20 dp, extra large 32 dp, extra extra large 48 dp). Shape should respond to interaction, create
tension through contrast, stay non-semantic, and be used sparingly.

> Be intentional when using shape in product UI. Don't compromise clarity for the sake of visual design.
> — [Material 3 — Shape overview & principles](https://m3.material.io/styles/shape/overview-principles)

> Caution: shapes without clear meaning behind why they're different can add more visual clutter than delight.
> — [Material 3 — Shape overview & principles](https://m3.material.io/styles/shape/overview-principles)

**LGKA+ applies it.** A deliberately narrow shape vocabulary: 16 dp cards and buttons, 12–14 dp icon tiles,
18 dp weather glass, 50 % news tag pills. No shape without a reason.

**Deviation.** The shape library and shape morphing are unused. Expression is spent on color and the sky,
not on silhouette. Recorded at [05 · Material 3 Expressive §5](05-material-expressive.md#5-the-expressive-tactics-scored).

---

## Start building with M3 Expressive

**URL** <https://m3.material.io/blog/building-with-m3-expressive> · accessed 2026-09-12

**Summary.** M3 Expressive is an evolution of M3, not a new version. Backed by 46 studies with over 18,000
participants. Fourteen new or updated components, four style systems (motion physics, emphasized typography,
expanded shape library, vibrant color), and seven design tactics culminating in "hero moments".

> And to be clear — M3 Expressive isn't a new version of the system. We're not deprecating M3, and this isn't "M4."
> — [Material 3 — Start building with M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)

> Stick to one or two hero moments in your product; too many moments can be overwhelming or distracting.
> — [Material 3 — Start building with M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)

> Contain content for emphasis. Organize content into logical groupings or containers. Give the most important content, tasks, or actions visual prominence through ample space and the brightest surface mapping.
> — [Material 3 — Start building with M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive)

**LGKA+ applies it.** `MaterialExpressiveTheme` + `MotionScheme.expressive()`; the new `LoadingIndicator`;
labelled card groups; exactly two hero moments (the animated sky, the 92 sp temperature). Tactic-by-tactic
scoring at [05 · Material 3 Expressive §5](05-material-expressive.md#5-the-expressive-tactics-scored).

**Deviations.** Tactics 1 (shape variety), 3 (emphasized typography) and 6 (component flexibility across
breakpoints) are not met.

---

## Accessible design overview

**URL** <https://m3.material.io/foundations/accessible-design/overview> · accessed 2026-09-12

**Summary.** Accessibility by default is a core Material value. Three principles: honor individuals through
customization; learn before, not after; treat WCAG requirements as a starting point rather than a ceiling.

> Honor individuals. Universal default experiences rarely meet everyone's needs. Introducing customizable features in a default experience allows room for individual adaptation.
> — [Material 3 — Accessible design overview](https://m3.material.io/foundations/accessible-design/overview)

**LGKA+ applies it.** Accent and theme mode are student-chosen and persist; `Accent.onColor` computes a
WCAG-safe pairing instead of assuming one; motion respects the system animation setting; type scales with
the system font size.

---

# developer.android.com — design

## Android color for mobile design

**URL** <https://developer.android.com/design/ui/mobile/guides/styles/color> · accessed 2026-09-12
(page's own "Last updated" line: 2024-12-12)

**Summary.** Explains HCT (hue, chroma, tone) and how M3 derives five key colors and tonal palettes from a
source color. Distinguishes accent, semantic and surface colors. Dynamic color from wallpaper is available
on Android 12+; a static scheme is still recommended as a fallback. Use tokens, not hex values. Color must
never be the only indicator.

> Assign colors with tokens to indicate the element's color role, instead of using a hardcoded value.
> — [Android — Color for mobile design](https://developer.android.com/design/ui/mobile/guides/styles/color)

> Even if you're using dynamic color, we strongly recommend creating a static scheme as a fallback if dynamic color isn't available to the user's device.
> — [Android — Color for mobile design](https://developer.android.com/design/ui/mobile/guides/styles/color)

> Never make color the only affordance or indicator for an available action.
> — [Android — Color for mobile design](https://developer.android.com/design/ui/mobile/guides/styles/color)

**LGKA+ applies it.** A fully static scheme; roles read through `MaterialTheme.colorScheme`; redundant
non-color cues everywhere ([10 · Accessibility §8](10-accessibility.md#8-color-and-contrast)).

**Deliberate deviation.** Dynamic color is **not** implemented — it would override the student's accent.
Reasoned at [02 · Color §5](02-color.md#5-theme-mode--dynamic-color-policy).

---

## Android system bars

**URL** <https://developer.android.com/design/ui/mobile/guides/foundations/system-bars> · accessed 2026-09-12
(page's own "Last updated" line: 2026-09-01)

**Summary.** Status, caption and navigation bars. Keep them transparent or translucent and draw behind them.
Use `WindowInsets` so content is not obscured. Edge-to-edge is enforced on Android 15; call
`enableEdgeToEdge()` for backward compatibility. Transparent gesture navigation bars are always recommended;
button-mode scrims can be removed with `setNavigationBarContrastEnforced(false)`. Account for display
cutouts and the keyboard.

> Keep the system status and navigation bars transparent or translucent and draw content behind these bars to go edge-to-edge.
> — [Android — System bars](https://developer.android.com/design/ui/mobile/guides/foundations/system-bars)

> Transparent gesture navigation bars are always recommended. Do: Keep the gesture navigation bar transparent. Don't: Add a background to the gesture navigation bar.
> — [Android — System bars](https://developer.android.com/design/ui/mobile/guides/foundations/system-bars)

**LGKA+ applies it.** `enableEdgeToEdge()` in `MainActivity`; both XML themes set transparent bars with
`enforceNavigationBarContrast=false` and the correct `windowLightStatusBar` per theme; insets consumed via
`Scaffold` padding or `safeDrawingPadding()`; the PDF dialog sets `decorFitsSystemWindows = false` and then
handles its own insets.

---

## Layout basics

**URL** <https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-basics> · accessed 2026-09-12
(page's own "Last updated" line: 2026-09-11)

**Summary.** Verify both orientations and multiple screen sizes. Honor device safe areas. Keep essential
interactions reachable. Use containment to group related content. Keep alignment and spacing consistent.
Don't overwhelm with actions. Keep keyboard-focused inputs visible.

> Use containment to group related content to guide the user through content and actions. Cards using explicit containment to group content with related actions.
> — [Android — Layout basics](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-basics)

> Do: Establish consistent spacing between like elements. Don't: Disrupt readability by inconsistently spacing like elements, which can make designs appear haphazard.
> — [Android — Layout basics](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-basics)

**LGKA+ applies it.** Cards as containment; a single row grammar; three app bar actions and nothing more;
`adjustResize` keeps login fields above the keyboard.

**Partial deviation.** "different screen sizes and form factors" is met functionally but not ergonomically on
tablets.

---

# developer.android.com — develop & quality

## Material Design 3 in Compose

**URL** <https://developer.android.com/develop/ui/compose/designsystems/material3> · accessed 2026-09-12
(page's own "Last updated" line: 2026-09-01)

**Summary.** How to use M3 and M3 Expressive in Compose: the `material3` dependency, experimental opt-ins,
the theme's three subsystems (color scheme, typography, shapes), generating schemes with Material Theme
Builder, dynamic color on Android 12+, the type scale table, the shape scale, emphasis via color and weight,
tonal elevation, component suites, navigation components by screen size, per-component theming, and the
system ripple and overscroll behaviours.

> Jetpack Compose offers an implementation of Material You and Material 3 Expressive, the next evolution of Material Design.
> — [Compose — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)

> Use on-primary on top of primary, and on-primary-container on top of primary-container, and the same for other accent and neutral colors to provide accessible contrast to the user.
> — [Compose — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)

> NavigationBar is used for compact devices when you want to target 5 or less destinations
> — [Compose — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)

**LGKA+ applies it.** `MaterialExpressiveTheme` with narrow `@OptIn` scopes; correct on-color pairings;
default ripple and overscroll; default type scale; per-component `shape = CardShape` where a specific radius
is wanted.

**Deviations.** No `Typography` or `Shapes` object is passed to the theme; no `NavigationBar` or
`NavigationRail` (the app is hub-and-detail, not tabbed — [07 · Navigation §6](07-navigation.md#6-why-there-is-no-navigation-bar)).

---

## Semantics

**URL** <https://developer.android.com/develop/ui/compose/accessibility/semantics> · accessed 2026-09-12
(page's own "Last updated" line: 2026-07-21)

**Summary.** Semantics describe meaning and role to accessibility, autofill and testing. Properties include
`heading()`, `liveRegion`, `paneTitle`, `error`, `progressBarRangeInfo`, `collectionInfo` /
`collectionItemInfo`, `stateDescription` and `customActions`. A parallel semantics tree exists alongside the
composition; Foundation and Material fill it automatically, and nodes can be merged or cleared.

> To improve the navigation experience, some accessibility services allow for easier navigation directly between sections or headings. To enable this, indicate that your component is a heading by defining its semantics property
> — [Compose — Semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics)

> Alert-like components can be marked with the liveRegion semantics property. This allows accessibility services to automatically notify the user of changes to this component, or its children
> — [Compose — Semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics)

**LGKA+ applies it.** `heading()` on every section and screen title; `mergeDescendants = true` on multi-part
rows; `Role.Button` / `Role.RadioButton` where a container is the control.

**Not used.** `liveRegion`, `paneTitle`, `collectionInfo`, `stateDescription`, `customActions`, `error`.
The most valuable of these for LGKA+ would be `liveRegion` for loading and error transitions — see the
skeleton-announcement limitation at [10 · Accessibility §6](10-accessibility.md#6-unique-descriptions-in-lists).

---

## API defaults

**URL** <https://developer.android.com/develop/ui/compose/accessibility/api-defaults> · accessed 2026-09-12
(page's own "Last updated" line: 2026-07-21)

**Summary.** Material, Compose UI and Foundation ship accessible defaults. Interactive elements need a 48 dp
minimum; many Material components enforce it when actionable, and Compose expands small targets beyond their
bounds — but overlapping targets are avoided by setting a real minimum size. `Image` and `Icon` need a
`contentDescription`, or `null` when decorative. `clickable` and `toggleable` merge descendants, and
`onClickLabel` / `onLongClickLabel` refine the announced action.

> Any on-screen element that someone can click, touch, or interact with must be large enough for reliable interaction. When sizing these elements, make sure to set the minimum size to 48dp to correctly follow the Material Design accessibility guidelines.
> — [Compose — Accessibility API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)

> To prevent possible overlap between touch areas of different composables, always use a large enough minimum size for the composable.
> — [Compose — Accessibility API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)

**LGKA+ applies it.** Explicit minimum sizes rather than relying on target expansion — notably
`Modifier.size(maxOf(swatchSize, 48).dp)` on the accent swatches; decorative icons pass `null`;
`onClickLabel` is used on tappable article images.

---

## Handling user interactions

**URL** <https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions> · accessed 2026-09-12

**Summary.** Compose abstracts pointer events into `Interaction` events (`PressInteraction.Press` /
`Release` / `Cancel`). `Modifier.clickable` interprets clicks from touch or keyboard. `InteractionSource`
exposes interaction state so components can react; custom visual responses are built as an `Indication`.

> User interface components give feedback to the device user by the way they respond to user interactions. Every component has its own way of responding to interactions, which helps the user know what their interactions are doing.
> — [Compose — Handling user interactions](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions)

> Button relies on Modifier.clickable to figure out whether the user clicked the button. […] That means you don't need to know whether the user tapped the screen or selected the button with a keyboard; Modifier.clickable figures out that the user performed a click, and responds by running your onClick code.
> — [Compose — Handling user interactions](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions)

**LGKA+ applies it.** Standard `clickable` / `selectable` / `combinedClickable` and component defaults. No
custom `Indication`, no `InteractionSource` collection. Feedback is layered instead: ripple + haptic +
state-dependent color/alpha ([08 · Motion §4–5](08-motion-and-feedback.md#4-haptics)).

---

## Make apps more accessible

**URL** <https://developer.android.com/guide/topics/ui/accessibility/apps> · accessed 2026-09-12
(page's own "Last updated" line: 2026-07-21)

**Summary.** Text contrast at least 4.5:1 below 18 sp (or bold below 14 sp), 3:1 otherwise. Touch targets at
least 48×48 dp. Describe every UI element's purpose; no `contentDescription` needed on `Text`; avoid
redundant words; keep descriptions unique per list item; mark decorative elements so services ignore them;
test with Lint and Compose testing.

> If the text is smaller than 18sp, or if the text is bold and smaller than 14sp, use foreground and background colors that result in a color contrast ratio of at least 4.5:1. For all other text, set the color contrast ratio to at least 3:1.
> — [Android — Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps)

> Each description should be unique. That way, when screen reader users encounter a repeated element description, they correctly recognize that the focus is on an element that already had focus earlier.
> — [Android — Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps)

**LGKA+ applies it.** Twelve-plus dedicated `a11y_*` strings in both locales; unique descriptions for events,
hourly, daily and PDF pages; decorative icons pass `null`; Lint runs with `warningsAsErrors` and
`abortOnError`.

**Known exceptions.** Repeated "Lädt…" on stacked skeletons; accent text below 4.5:1 on light surfaces.
Both are documented rather than hidden — [10 · Accessibility](10-accessibility.md).

---

## What great technical quality looks like

**URL** <https://developer.android.com/quality/technical> · accessed 2026-09-12
(page's own "Last updated" line: 2026-03-06)

**Summary.** Quality spans form factors, stability (crashes, ANRs, LMKs), performance (start-up time,
rendering at 60 fps without jank), battery and network use, app size, freshness and healthy releases.
Recommends Kotlin, baseline profiles, `reportFullyDrawn`, app bundles, phased rollout.

> Most apps should run at 60 fps without any dropped or delayed frames. Poor rendering performance can cause users to perceive stuttering, also known as jank.
> — [Android — What great technical quality looks like](https://developer.android.com/quality/technical)

> Users want to be able to interact with your app or game as quickly as possible. […] as a general principle you should minimize the time between launch and first interaction.
> — [Android — What great technical quality looks like](https://developer.android.com/quality/technical)

**LGKA+ applies it.** Kotlin throughout; cache-first bootstrap so content is on screen before the network
answers; lazy PDF rasterization with a six-bitmap LRU and a mutex-guarded renderer; R8 with
`isMinifyEnabled` and `isShrinkResources`; BouncyCastle excluded from PdfBox, removing a permissive
TrustManager and roughly 3 MB.

**Not adopted.** No baseline profile, no `reportFullyDrawn`. Both are unverified for this app and are
recorded here as opportunities, not as claims.

---

## Implement dark theme

**URL** <https://developer.android.com/develop/ui/views/theming/darktheme> · accessed 2026-09-12
(page's own "Last updated" line: 2026-09-01)

**Summary.** Dark theme arrived in Android 10 (API 29) and reduces power use, helps users with low vision or
light sensitivity, and helps in low light. Views apps inherit a `DayNight` theme; hardcoded light-theme colors
and icons must be replaced with theme attributes or night-qualified resources. Apps should offer Light / Dark /
System default, with system default recommended. On API 31+ use `UiModeManager#setApplicationNightMode` so the
system can match the theme during the splash screen. *Force Dark* exists for apps that have not implemented a
theme. Launch screens, notifications and widgets must not assume a light background. A theme change triggers a
`uiMode` configuration change.

> Dark theme is available in Android 10 (API level 29) and higher. It has the following benefits:
> - Reduces power usage by a significant amount, depending on the device's screen technology.
> - Improves visibility for users with low vision and those who are sensitive to bright light.
> - Makes it easier to use a device in a low-light environment.
>
> — [Android — Implement dark theme](https://developer.android.com/develop/ui/views/theming/darktheme)

> Avoid using hardcoded colors or icons intended for use under a light theme. Use theme attributes or night-qualified resources instead.
> — [Android — Implement dark theme](https://developer.android.com/develop/ui/views/theming/darktheme)

> You can let users change the app's theme while the app is running. The following are recommended options:
> - Light
> - Dark
> - System default (the recommended default option)
>
> — [Android — Implement dark theme](https://developer.android.com/develop/ui/views/theming/darktheme)

> Remove any hardcoded colors such as background colors set programmatically to white. Use the `?android:attr/colorBackground` theme attribute instead.
> — [Android — Implement dark theme](https://developer.android.com/develop/ui/views/theming/darktheme)

**LGKA+ applies it.** `minSdk` is 29, so every supported device has dark theme. Three modes are offered in the
recommended order with `"system"` as the stored default (`Prefs.kt`, `Theme.kt`). `res/values-night/` carries
night-qualified overrides for both `themes.xml` and `colors.xml`. Pure-black surfaces take the power-saving
benefit literally on OLED panels.
`android:configChanges` includes `uiMode`, and state survives in a `ViewModel` and `SharedPreferences`.

The launch-screen advice is met too: `splash_background` is night-qualified (`#F2F2F7` light, `#000000` dark),
so the splash follows the system appearance and hands off to the first app frame on the same color. Documented
at [02 · Color §7.3](02-color.md#73-the-splash-background-follows-the-system-appearance).

**Known limitation.** `UiModeManager#setApplicationNightMode` is not called, so an **in-app** override is not
reflected in the splash: choosing **Hell** on a device in system dark mode still shows the dark splash for the
duration of the launch image. The system-appearance cases are all correct. Tracked as a follow-up; the API is
API 31+, so devices on 29–30 would keep the current behaviour regardless.

**Deviations.**
1. `android:windowBackground` is hardcoded per theme rather than using `?android:attr/colorBackground`. The
   result is correct because both values are night-qualified; the mechanism is not the recommended one.
2. The page is Views-oriented (`DayNight`, `AppCompatDelegate`, Force Dark). LGKA+ is Compose, so the
   *mechanisms* do not transfer — only the principles quoted above do. Force Dark is irrelevant: the app
   implements its own dark theme.

---

## Adaptive icons

**URL** <https://developer.android.com/develop/ui/views/launch/icon_design_adaptive> · accessed 2026-09-12
(page's own "Last updated" line: 2026-08-13)

**Summary.** An `AdaptiveIconDrawable` renders under an OEM-supplied mask, supports launcher visual effects,
and supports user theming from Android 13 (API 33) when a `monochrome` layer is present — with Android 16 QPR 2
auto-theming icons that lack one. Requirements: two color layers (vectors preferred over bitmaps), an optional
monochrome layer, all layers 108×108 dp, clean edges with no mask or outline shadow, and a logo of 48–66 dp
inside the 66×66 dp safe zone, the outer 18 dp per side being reserved for masking and effects. Declared with
`android:icon` and optionally `android:roundIcon`, defined in `res/mipmap-anydpi-v26/ic_launcher.xml`.

> Size all layers to 108x108 dp.
> — [Android — Adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive)

> Use a logo that's at least 48x48 dp. It must not exceed 66x66 dp, because the inner 66x66 dp of the icon appears within the masked viewport.
> — [Android — Adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive)

> The foreground and monochrome layers are using the same drawable. However, you can create separate drawables for each layer if needed.
> — [Android — Adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive)

**LGKA+ applies it.** All three layers declared; foreground PNGs measure exactly 108 dp at all five densities
(108 / 162 / 216 / 324 / 432 px); transparent background with clean edges and no baked-in mask or shadow; the
motif sits inside the safe zone. The monochrome layer reuses the foreground drawable, which this page
explicitly permits. Full measurement table at [09 · Iconography §7](09-iconography.md#7-the-launcher-icon).

**Deviations.**
1. **Bitmaps where vectors are preferred.** The art is a shaded illustration with gradients; five density PNGs
   are the pragmatic choice. Cost: APK size and scaling crispness.
2. **`android:roundIcon` is declared with a byte-identical duplicate.** The icon has no circular background
   element, and the page notes that *"most apps only specify `android:icon`"*.

> **Correction note**
> An earlier revision of these guidelines called the monochrome-layer reuse a deviation. It is not. This page
> documents it as a supported pattern, and the claim has been corrected in
> [01 · Brand identity](01-brand-identity.md#22-motif) and [09 · Iconography](09-iconography.md#72-what-the-monochrome-layer-buys).

---

## Navigation bar

**URL** <https://developer.android.com/develop/ui/compose/components/navigation-bar> · accessed 2026-09-12
(page's own "Last updated" line: 2026-09-01)

**Summary.** `NavigationBar` plus `NavigationBarItem` let users switch between destinations. Recommended for
three to five destinations of equal importance, compact window sizes, and destinations that are consistent
across screens. `NavigationBarItem` takes `selected`, `onClick()`, and optional `label` and `icon`. The
canonical implementation puts the bar in a `Scaffold`'s `bottomBar` and drives a `NavHost`.

> The navigation bar allows users to switch between destinations in an app. You should use navigation bars for:
> - Three to five destinations of equal importance
> - Compact window sizes
> - Consistent destinations across app screens
>
> — [Compose — Navigation bar](https://developer.android.com/develop/ui/compose/components/navigation-bar)

> `selected`: Determines whether the current item is visually highlighted.
> — [Compose — Navigation bar](https://developer.android.com/develop/ui/compose/components/navigation-bar)

**LGKA+ deliberately does not use it.** The app fails two of the three preconditions: its destinations are not
of equal importance, and they are not consistent across screens. With a single root there is nothing for
`selected` to indicate. The three most-used detail destinations are top app bar actions on the home hub
instead. Reasoned in full at [07 · Navigation §6](07-navigation.md#6-why-there-is-no-navigation-bar).

**Note on the API surface.** The page's example uses `rememberNavController` and `NavHost` (Navigation 2).
LGKA+ is on Navigation 3 (`rememberNavBackStack`, `NavDisplay`, `entryProvider`), so the wiring in the snippet
does not transfer even if a bar were added — only the "when to use one" guidance does.

---

## Animation

**URL** <https://developer.android.com/develop/ui/compose/animation/introduction> · accessed 2026-09-12
(page's own "Last updated" line: 2024-07-01)

**Summary.** A landing page for Compose animation. It states why animation matters and indexes the API
families: modifiers and composables (`AnimatedVisibility`, `animateContentSize()`, `AnimatedContent`),
value-based animations (`animate*AsState`, `Transition`, `InfiniteTransition`), customization of duration,
easing and spring configuration, and animation testing.

> Animations are essential in a modern mobile app in order to realize a smooth and understandable user experience.
> — [Compose — Animation](https://developer.android.com/develop/ui/compose/animation/introduction)

> Animate properties indefinitely — Use `InfiniteTransition` to animate properties continuously.
> — [Compose — Animation](https://developer.android.com/develop/ui/compose/animation/introduction)

**LGKA+ applies it.** Sparingly and on purpose. Transitions and component motion are delegated entirely to
`MotionScheme.expressive()`, so none of the value-based APIs appear in the app.

**Deviation.** The three continuous animations (sky shader, precipitation, fireworks) use raw `withFrameNanos`
loops rather than `rememberInfiniteTransition`. The loop suits a shader uniform that needs monotonic elapsed
seconds rather than a bounded oscillation, and it is what makes the explicit `ANIMATOR_DURATION_SCALE` check
possible — but it is hand-rolled where an API exists, and it puts those animations outside Compose's animation
testing surface. Recorded at [08 · Motion §2](08-motion-and-feedback.md#2-where-motion-appears).

> **Note**
> This page carries the oldest "Last updated" date in the source set (2024-07-01) and predates the M3
> Expressive motion-physics system. Where the two overlap, the
> [M3 motion page](https://m3.material.io/styles/motion/overview) is the newer authority and the one LGKA+ follows.

---

## Work with fonts

**URL** <https://developer.android.com/develop/ui/compose/text/fonts> · accessed 2026-09-12
(page's own "Last updated" line: 2026-09-01)

**Summary.** `Text` takes a `fontFamily`; serif, sans-serif, monospace and cursive are built in. Custom faces
go in `res/font` and are assembled with the `Font` function per weight and style. Downloadable fonts fetch
Google Fonts asynchronously from Compose 1.2.0, with fallback chains, but custom providers are unsupported and
newly published fonts can take months to become available. Variable fonts put multiple styles in one file via
axes (weight, width, slant, italic, plus custom axes) and require Android O or above.

> `Text` has a `fontFamily` parameter to allow setting the font used in the composable. By default, serif, sans-serif, monospace and cursive font families are included
> — [Compose — Work with fonts](https://developer.android.com/develop/ui/compose/text/fonts)

> Using variable fonts instead of regular font files allows you to only have one font file instead of multiple.
> — [Compose — Work with fonts](https://developer.android.com/develop/ui/compose/text/fonts)

> Warning: Variable fonts are only supported on Android O and above.
> — [Compose — Work with fonts](https://developer.android.com/develop/ui/compose/text/fonts)

> Google Fonts takes several months to make new fonts available on Android. There's a gap in time between when a font is added in fonts.google.com and when it's available through the downloadable fonts API (either in the View system or in Compose).
> — [Compose — Work with fonts](https://developer.android.com/develop/ui/compose/text/fonts)

**LGKA+ applies it.** By not using any of it. `fontFamily` is never set, `res/font` does not exist, and the
platform default resolves through the built-in families. This is the correct decision for an app whose
identity is carried by an icon and an accent color rather than by a typeface, and it keeps the APK smaller —
which the [technical quality](#what-great-technical-quality-looks-like) guidance asks for.

**If that ever changes.** `minSdk` 29 clears the Android O floor, so a single variable `.ttf` would cover all
six weights the app uses. The cost is that M3's `Typography` has no `defaultFontFamily`, so all nine styles in
[03 · Typography §2](03-typography.md#2-where-each-style-is-used) would need redeclaring. Written up at
[03 · Typography §6.1](03-typography.md#61-what-a-custom-font-would-cost).

---

# A note on relocated pages

Five Android design pages under `developer.android.com/design/ui/mobile/guides/…` are permanently gone
(HTTP 404, re-checked 2026-09-12). Their subject matter now lives at the URLs digested above, and every
citation in these guidelines points at a live page.

| Retired URL | Current source used |
|---|---|
| `…/design/ui/mobile/guides/foundations/dark-theme` | [Implement dark theme](https://developer.android.com/develop/ui/views/theming/darktheme) |
| `…/design/ui/mobile/guides/home-screen/app-icons` | [Adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive) |
| `…/design/ui/mobile/guides/components/navigation-bar` | [Compose — Navigation bar](https://developer.android.com/develop/ui/compose/components/navigation-bar) |
| `…/design/ui/mobile/guides/styles/motion` | [Compose — Animation](https://developer.android.com/develop/ui/compose/animation/introduction) + [M3 — Motion overview](https://m3.material.io/styles/motion/overview) |
| `…/design/ui/mobile/guides/styles/typography` | [Compose — Work with fonts](https://developer.android.com/develop/ui/compose/text/fonts) + [Compose — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) |

> **Note**
> The replacement set also included a second capture of
> `https://developer.android.com/develop/ui/compose/designsystems/material3` taken for its typography section.
> It is the same page already digested under
> [Material Design 3 in Compose](#material-design-3-in-compose); its type-scale table was compared against the
> earlier capture and is identical, so it is not digested twice.

> **Note**
> The seven `m3.material.io` pages were captured through a JavaScript-rendered digest rather than raw HTML;
> the saved HTML shells contain only "This website requires JavaScript." The quoted text in this file comes
> from the rendered markdown digests in the source set, each of which records its own URL and retrieval date
> of 2026-09-12.

## Related

Every other file in this folder. Start at the [index](README.md).

## Sources

All URLs listed in the sections above, each accessed 2026-09-12, plus the app source tree
(`app/src/main/kotlin/com/lgka/*.kt`, `app/src/main/res/**`, `app/src/androidTest/**`,
`app/build.gradle.kts`, `gradle/libs.versions.toml`, `scripts/screenshots.sh`) as of 2026-09-12.
