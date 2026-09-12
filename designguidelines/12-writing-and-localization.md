# 12 · Writing & localization

| | |
|---|---|
| **Last updated** | 2026-09-12 |
| **Applies to** | LGKA+ Android, Android 17 / Material 3 Expressive |
| **Owner** | Luka Löhr |
| **Status** | Normative |

---

## 1. Two locales, one file each

| Locale | File | Role |
|---|---|---|
| German | `app/src/main/res/values/strings.xml` | **Default** — the school is in Karlsruhe |
| English | `app/src/main/res/values-en/strings.xml` | Full parity, no fallbacks |

Declared in three places, all of which must agree:

```xml
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="de" />
    <locale android:name="en" />
</locale-config>
```
<sub>`app/src/main/res/xml/locales_config.xml`, referenced by `android:localeConfig` in the manifest</sub>

```kotlin
resourceConfigurations += listOf("de", "en")
```
<sub>`app/build.gradle.kts`</sub>

`android:configChanges` includes `locale|layoutDirection`, so a per-app language change from system settings
is applied without restarting the activity.

- [x] Add a string to **both** files in the same commit.
- [x] Adding a third locale means touching `locales_config.xml`, `resourceConfigurations`, and re-running the screenshot suite.
- [ ] Never hardcode a user-visible string in Kotlin.

> **Warning — the one exception**
> The debug-only sky-preview menu in `WeatherScreen.kt` uses German literals ("Klar (Tag)", "Bedeckt", …).
> It is wrapped in `if (BuildConfig.DEBUG)` and never ships. Do not use it as precedent.

## 2. Voice

Established in [01 · Brand identity §3](01-brand-identity.md#3-tone-of-voice). The operating rules:

| Rule | German | English |
|---|---|---|
| Address the student informally | *du* / *dein*, never *Sie* | plain "you" / "your" |
| Imperative for actions | "Wähle deine Lieblingsfarbe aus." | "Choose your favorite color." |
| Questions for setup | "In welcher Klasse bist du?" | *(en uses the same interrogative framing)* |
| No filler | "Zugangsdaten sind falsch." | "The credentials are incorrect." |
| Exclamation marks only at milestones | "Willkommen!", "Los geht's!" | "Welcome!", "Let's go!" |

- [x] Say what to do next.
- [ ] No "Bitte warten", "Oops", "Ups", emoji, or error codes.
- [ ] No second exclamation mark on a screen.

## 3. Error copy pattern

Every failure message is **what happened**, then optionally **why**, then a **retry control**:

| Layer | German | English |
|---|---|---|
| What | Serververbindung fehlgeschlagen | Server connection failed |
| Why | Möglicherweise besteht keine Internetverbindung oder es finden gerade Wartungsarbeiten am Lessing-Gymnasium statt. | You may be offline, or maintenance is being carried out at the Lessing-Gymnasium. |
| Do | Erneut versuchen | Try again |

Note that the English is a **re-write, not a transliteration** — "Möglicherweise besteht keine
Internetverbindung" becomes "You may be offline", which is what an English speaker would actually say.
That is the standard for every translation in this app.

Other paired failures:

| German | English |
|---|---|
| Fehler beim Laden | Error loading |
| Noch keine Infos | No info yet |
| Wetterdaten nicht verfügbar | Weather data not available |
| Bitte prüfe deine Internetverbindung. | Please check your internet connection. |
| Formular konnte nicht geladen werden | Form could not be loaded |
| Anmeldung nicht möglich – bitte Internetverbindung prüfen. | Login not possible – please check your connection. |
| Klasse %1$s existiert nicht. | Class %1$s does not exist. |
| Keine Treffer | No matches |

## 4. Empty states

State the absence, nothing more. No illustration, no "Why not try…".

| German | English |
|---|---|
| Keine Termine verfügbar | No events available |
| Keine Neuigkeiten verfügbar | No news available |
| Keine Stundenpläne verfügbar | No schedules available |
| Keine Vertretungen | No substitutions |

## 5. Formatting patterns

### 5.1 Positional arguments everywhere

Every parameterised string uses `%1$s`-style positional arguments, never bare `%s`, so translators can
reorder:

| Resource | German | English |
|---|---|---|
| `plan_with_date` | `%1$s · %2$s` | `%1$s · %2$s` |
| `title_with_semester` | `%1$s – %2$s` | `%1$s – %2$s` |
| `high_low` | `H: %1$d°  T: %2$d°` | `H: %1$d°  L: %2$d°` |
| `feels_like` | `Gefühlt %1$d°` | `Feels like %1$d°` |
| `schedule_not_available` | `%1$s ist noch nicht verfügbar` | `%1$s is not available yet` |
| `class_changed` | `Deine Klasse wurde auf %1$s geändert.` | `Your class was changed to %1$s.` |

Note `high_low`: the German abbreviation is **T** (Tiefstwert), the English is **L** (low). A naive
translation would have left the German letters in place.

### 5.2 Separators

| Separator | Use | Example |
|---|---|---|
| ` · ` (middle dot, spaced) | Joining peer facts | `14.09.2026 · 12 Vertretungen` (card subtitle, under the weekday title), `Mo., 14. September · 07:45` |
| ` – ` (en dash, spaced) | Title with qualifier | `Klasse 7B – 1. Halbjahr` |
| ` • ` (bullet) | Footer metadata | `© 2026 Luka Löhr • v3.0.0` |

- [x] Use `·` to join, `–` to qualify. Never a hyphen for either.

### 5.3 Plurals are real plurals

```xml
<plurals name="substitutions_count">
    <item quantity="one">1 Vertretung</item>
    <item quantity="other">%d Vertretungen</item>
</plurals>
```
<sub>`app/src/main/res/values/strings.xml`; English has `1 substitution` / `%d substitutions`</sub>

Read with `pluralStringResource(R.plurals.substitutions_count, size, size)` in `Home.kt`. It is the only
plural in the app; any new counted noun must become one too.

> **Note**
> Several accessibility strings carry `tools:ignore="PluralsCandidate"` — `a11y_day`, `a11y_hour`,
> `a11y_match_position`, `a11y_weather_card`. The suppression is correct: these read numeric values aloud
> ("19 Grad", "Treffer 3 von 12") where a grammatical plural would not change the spoken result.

### 5.4 Dates, times and numbers are formatted, not translated

| Value | Mechanism |
|---|---|
| Event date, weekday | `DateTimeFormatter.ofPattern("EEE, d. MMMM", locale)` with the *Compose* locale | 
| Month abbreviation in a date tile | `DateTimeFormatter.ofPattern("MMM", locale)` |
| Forecast day name | `DateTimeFormatter.ofPattern("EEE", locale)` |
| Temperatures | `"${w.temp.toInt()}°"` |
| UV index | `"%.1f · %s".format(Locale.ROOT, w.uvi, uviLabel)` — `Locale.ROOT` for the number, a localized label after it |
| Class key normalisation | `input.trim().lowercase(Locale.ROOT)` |

<sub>`app/src/main/kotlin/com/lgka/{Home,WeatherScreen}.kt`</sub>

The pattern: **`Locale.ROOT` for machine-facing values, the user's locale for anything displayed.**

## 6. Domain vocabulary

Terms from the school's own world are translated once and then never varied:

| German | English | Note |
|---|---|---|
| Vertretungsplan | Substitution plan | |
| Stundenplan | Timetable | Not "schedule" in prose |
| Krankmeldung | Sick note | Not "absence report" |
| Halbjahr | Semester | `1. Halbjahr` → `1st semester` |
| Jahrgang 11 / 12 | Year 11 / 12 | Not "Grade 11" |
| Klasse 7b | Class 7B | The class key is lowercased for storage, title-cased for display |
| Bevorstehende Termine | Upcoming events | |
| Neuigkeiten | News | |
| Impressum | Legal notice | The German legal term has no English equivalent; "Legal notice" is used |
| Zugriffe | Views | Article view count |

The weekday names are a separate mapping, because the upstream Untis data is German-only:

```kotlin
/** German Untis weekday name -> localized resource; null for "weekend"/unknown. */
fun weekdayRes(german: String?): Int? = when (german) {
    "Montag" -> R.string.weekday_montag
    …
}
```
<sub>`app/src/main/kotlin/com/lgka/Theme.kt`</sub>

The same pattern covers the 30 WMO weather descriptions via `wmoRes(code)`. **Never render an upstream
German string directly** — map it to a resource id.

## 7. Casing

| Style | Where | How |
|---|---|---|
| Sentence case | Everything by default | Written that way in the resource |
| ALL CAPS | Section labels ("DARSTELLUNG"/"APPEARANCE", "MEHR"/"MORE") and forecast headers ("STÜNDLICH"/"HOURLY", "3 TAGE"/"3 DAYS") | Written that way in the resource |
| ALL CAPS at runtime | Weather stat-tile labels | `label.uppercase()` in `WeatherScreen.kt` |

See the warning in [03 · Typography §4](03-typography.md#4-all-caps-micro-labels) about the locale-less
`uppercase()` call.

## 8. Adding a string — checklist

- [ ] Added to `values/strings.xml` **and** `values-en/strings.xml`.
- [ ] Key is `snake_case` and describes the *meaning*, not the location (`server_connection_failed`, not `home_error_2`).
- [ ] Accessibility-only strings are prefixed `a11y_`.
- [ ] Parameters are positional (`%1$s`), and their order makes sense in both languages.
- [ ] Counted nouns use `<plurals>`, not `"$n Vertretungen"`.
- [ ] The English is idiomatic English, not translated German.
- [ ] German uses *du*.
- [ ] No trailing whitespace, no emoji, no hardcoded date or number formatting.
- [ ] Long strings checked at the largest system font size — see [10 · Accessibility](10-accessibility.md).

## Related

[01 · Brand identity](01-brand-identity.md) · [03 · Typography](03-typography.md) · [10 · Accessibility](10-accessibility.md) · [13 · Screens](13-screens.md)

## Sources

| Source | Accessed |
|---|---|
| App source: `res/values/strings.xml`, `res/values-en/strings.xml`, `res/xml/locales_config.xml`, `AndroidManifest.xml`, `app/build.gradle.kts`, `kotlin/com/lgka/{Theme,Home,WeatherScreen,Settings,Onboarding,News,PdfViewer,WebScreen}.kt` | 2026-09-12 |
| [Android — Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps) | 2026-09-12 |
| [Material 3 — Layout overview](https://m3.material.io/foundations/layout/understanding-layout/overview) (bidirectionality) | 2026-09-12 |
