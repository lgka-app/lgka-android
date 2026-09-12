---
title: Brand identity
last_updated: 2026-09-12
sources:
  - https://m3.material.io/blog/building-with-m3-expressive (retrieved 2026-09-12)
  - app/src/main/kotlin/com/lgka/Theme.kt
---

# Brand identity

## What the app is

LGKA+ is a utility, used in short bursts, usually at speed: a student checking
whether first period is cancelled while walking to the bus. Everything in the
design follows from that. The app is not a place anyone browses. It is a place
where four facts — weather, substitutions, timetable, next events — must be
legible in under two seconds, and where the path to a PDF must be one tap.

The Android app is a rewrite of a Flutter app that shipped first. Parity with
that app is an explicit constraint, and it is why several values here are
specific numbers rather than Material defaults: `#F2F2F7` light surfaces, pure
black dark surfaces, 16dp card corners. Comments in the source call this out
(`Theme.kt:18-23`, `Home.kt:74`). Where the Flutter app set a precedent, the
Android app matches it rather than drifting.

## Five commitments

### 1. The accent is the user's, not ours

There is no brand colour. The user picks one of five accents during onboarding,
and it becomes `primary` and `secondary` across the whole app
(`Theme.kt:34-35`, `Theme.kt:58-59`). This is the app's single strongest identity
decision, and it is the reason the neutral layer has to be rigorously neutral:
every surface, outline and text colour is a grey, so that the one chromatic thing
on screen is always the user's choice.

Material frames personalisation as a purpose of the expressive colour system:

> Rich visual styles support personalization and dynamic colors. The expressive color system can create a wide range of accessible palettes.
>
> — [Start building with Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive), retrieved 2026-09-12

The app does **not** use Material You dynamic colour from the wallpaper. A
wallpaper-derived scheme would override a choice the user made deliberately,
three screens into onboarding, and would break parity with the iOS and Flutter
builds. The five-accent picker is the personalisation story instead. See
[02-color](02-color.md).

### 2. Neutral surfaces, no tint

Material 3 tints surfaces with the primary hue as elevation rises. This app does
not. Light surfaces are `#F2F2F7` with white containers; dark surfaces are pure
`#000000` with neutral grey containers from `#141414` to `#2E2E2E`
(`Theme.kt:43-55`, `Theme.kt:66-78`). Tinting every card with the accent would
make the accent ambient, and an ambient accent stops being a signal. Keeping
containers grey means the accent only ever appears where it means something: an
icon tile, a link, a selected state, a primary button.

Pure black in dark mode is a deliberate departure from Material's tonal dark
surfaces. It is chosen for OLED power and for the contrast it gives the weather
card's sky shader, which is the app's one full-colour element.

### 3. Cards are the layout

Almost every screen is a vertical list of rounded cards on a flat background:
home, feature list, news, settings, the Krankmeldung notice. The card is the unit
of content, of tap target, and of rhythm — a uniform 12dp gap between cards, 20dp
screen margins (`Home.kt:122-124`). Material's own guidance is what the app is
leaning on:

> Cards can serve as entry points into deeper levels of detail or navigation
>
> — [Cards — Guidelines](https://m3.material.io/components/cards/guidelines), retrieved 2026-09-12

Critically, a card that navigates is *entirely* tappable, not just its text. See
[06-components](06-components.md).

### 4. One hero moment

Material warns against spreading expressiveness thin:

> Stick to one or two hero moments in your product; too many moments can be overwhelming or distracting.
>
> — [Start building with Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive), retrieved 2026-09-12

This app's hero moment is the weather. An AGSL fragment shader draws a live sky —
procedural fbm clouds, a sun with bloom, twinkling stars at night, animated rain
and snow (`WeatherScreen.kt:93-195`). It appears twice: as the full-bleed
background of the weather screen, and cropped into the 112dp card at the top of
home. Nothing else in the app animates decoratively. The New Year's fireworks
overlay (`Settings.kt:149`) is the single exception, and it is visible on one day
a year.

The shader requires Android 13 (API 33). Below that, a two-stop vertical gradient
stands in (`WeatherScreen.kt:232-236`). The fallback is plain, not broken.

### 5. Honest empty and error states

Nothing is faked while loading. Skeleton rows hold the exact geometry of the
cards they replace (`Widgets.kt:84`). When the school's server is unreachable the
app says so in plain German and offers a retry, per card rather than for the
whole screen (`Home.kt:214-232`, `Home.kt:245-253`). When no substitution plan has
been published yet, the card reads "Noch keine Infos", renders at 45% opacity and
is not clickable (`Home.kt:262-268`) — the state is communicated by text and by
disabled affordance, never by colour alone.

## What the app is not

- Not a social product. There is no avatar, no badge, no notification count.
- Not a dashboard. Four sections on home, in a fixed order, no reordering.
- Not branded with school colours. The school's identity is the content, not the chrome.
- Not animated for its own sake. Motion is either the weather, or feedback on a touch.

## Voice

German first, informal *du*, no exclamation marks outside onboarding. Buttons are
verbs ("Anmelden", "Erneut versuchen", "Speichern"). Errors state what happened
and what to do, and never blame the user. See
[08-localization-and-writing](08-localization-and-writing.md).
