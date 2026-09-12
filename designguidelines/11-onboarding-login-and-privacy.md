# 11 · Onboarding, login & privacy

| | |
|---|---|
| **Last updated** | 2026-09-12 |
| **Applies to** | LGKA+ Android, Android 17 / Material 3 Expressive |
| **Owner** | Luka Löhr |
| **Status** | Normative |

---

## 1. The flow

```
WelcomeStep  →  FeaturesStep  →  AccentStep  →  AppearanceStep  →  AuthScreen  →  Home
  logo           6 features      5 swatches     3 modes            user + pass
  "Weiter"       "Weiter"        "Weiter"       "Los geht's!"      "Anmelden"
```
<sub>`app/src/main/kotlin/com/lgka/Onboarding.kt`</sub>

Four screens before a password is asked for. The order is the point: **show value, let the student make it
theirs, then ask for credentials.**

| Step | Purpose | Headline (de / en) |
|---|---|---|
| 1 · Welcome | Identity — 180 dp app logo | Willkommen! / Welcome! |
| 2 · Features | What the app does — six cards | Alle Funktionen im Überblick / All features at a glance |
| 3 · Accent | Personalization — five swatches, live | Deine Akzentfarbe / Your accent color |
| 4 · Appearance | Personalization — dark / auto / light | Erscheinungsbild / Appearance |
| 5 · Auth | The gate | Anmeldung erforderlich / Login required |

### 1.1 Shared scaffold

Every step has identical geometry: 24 dp padding, `safeDrawingPadding()`, content weighted to fill,
and a full-width ≥ 52 dp CTA pinned to the bottom.

```kotlin
@Composable
private fun OnboardingScaffold(button: String, onContinue: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, content = content)
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("onboarding.continue"),
                   shape = RoundedCornerShape(16.dp)) {
                Text(button, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
```
<sub>`app/src/main/kotlin/com/lgka/Onboarding.kt`</sub>

The button label changes only on the last step ("Los geht's!"), which is the flow's one signal of completion.
There is no progress dots row, no skip, and no back button — the flow is four taps and forward-only.

### 1.2 The six features

| Icon | de title | en title |
|---|---|---|
| `CalendarToday` | Vertretungsplan | Substitution plan |
| `Schedule` | Stundenplan | Timetable |
| `Cloud` | Wetterdaten | Weather data |
| `Newspaper` | Neuigkeiten | News |
| `MedicalServices` | Krankmeldung | Sick note |
| `Event` | Schulveranstaltungen | School events |

Each is a card with a 48 dp `primary` @ 10 % tile, a `SemiBold` title and a `bodyMedium` description in
`onSurfaceVariant`, merged into one TalkBack announcement.

### 1.3 Personalization is immediate

Choosing an accent or theme in steps 3 and 4 writes straight to `SharedPreferences` through
`Prefs`, which exposes Compose state, so the **whole app recomposes under the new theme while the student is
still in onboarding**. The choice is demonstrated, not promised.

> Honor individuals. […] Introducing customizable features in a default experience allows room for individual adaptation.
> — [Material 3 — Accessible design overview](https://m3.material.io/foundations/accessible-design/overview)

Both settings remain editable forever in the settings sheet, which reuses the *same composables*
(`AccentRow`, `ThemeModeRow`) at a smaller swatch size.

> **Warning**
> Onboarding steps are local state, not back-stack entries, so system back exits the app instead of
> stepping back. See [07 · Navigation §3](07-navigation.md#3-gating-not-routing).

## 2. Login

### 2.1 What is asked for

The school publishes one read-only HTTP basic-auth login for its substitution-plan area. The app asks for
exactly that, and says so:

> Verwende die Zugangsdaten, die du bereits von der Schulwebsite kennst
> — `R.string.auth_subtitle`, `app/src/main/res/values/strings.xml`

> Use the credentials you already know from the school website
> — `R.string.auth_subtitle`, `app/src/main/res/values-en/strings.xml`

There is no account creation, no email, no password reset and no "forgot password" — there is nothing the
app could reset.

### 2.2 Verification is server-side

```kotlin
/// Login gate — the school website's credentials are verified against the
/// server and stored privately; the app never compares them locally.
```
<sub>`app/src/main/kotlin/com/lgka/Onboarding.kt`</sub>

`container.api.verify(pair)` performs a real request. Only on success are the credentials saved and
`isAuthenticated` set. No credential, or any part of one, is embedded in the source tree.

### 2.3 Feedback states

| State | Duration | Visual | Haptic |
|---|---|---|---|
| Loading | until the request returns | 22 dp spinner replaces the label; button stays `primary` | — |
| Success | 500 ms | Container ![#2E7D32](assets/swatches/2E7D32.svg) `#2E7D32`, `Check` icon | `Confirm` |
| Wrong credentials | 700 ms | Container `error`, message "Zugangsdaten sind falsch." | `Reject` |
| Offline | 700 ms | Container `error`, message "Anmeldung nicht möglich – bitte Internetverbindung prüfen." | `Reject` |

Distinguishing *wrong password* from *no connection* matters: one is the student's problem, the other is not.

```kotlin
val canLogin = username.isNotBlank() && password.isNotBlank() && !loading
```
<sub>`app/src/main/kotlin/com/lgka/Onboarding.kt`</sub>

The button is disabled until both fields are non-blank, and `flash != 0` keeps it visible during the
feedback window so the state animation is never interrupted.

### 2.4 Field configuration

| Field | Icon | Keyboard | IME | Extra |
|---|---|---|---|---|
| Benutzername | `Person` | `Text` | `Next` | `autoCorrectEnabled = false` |
| Passwort | `Lock` | `Password` | `Go` → submits | `PasswordVisualTransformation()` |

`android:windowSoftInputMode="adjustResize"` in the manifest keeps both fields above the keyboard.

## 3. Credential storage

```kotlin
/**
 * The school website's read-only HTTP basic-auth credentials, entered by the
 * user at login, verified against the server and kept in a private
 * SharedPreferences file that is excluded from backups (see
 * res/xml/backup_rules.xml). Nothing in the source tree contains a password.
 */
class Credentials(context: Context) {
    private val sp = context.getSharedPreferences("lgka-auth", Context.MODE_PRIVATE)
```
<sub>`app/src/main/kotlin/com/lgka/Credentials.kt`</sub>

| Property | Value |
|---|---|
| Store | `SharedPreferences` file `lgka-auth`, `MODE_PRIVATE` |
| Separate from settings | Yes — preferences live in a different file, `lgka` (`Prefs.kt`) |
| Cloud backup | **Excluded** |
| Device transfer | **Excluded** |
| Use | `Basic` header built on demand, and WebView basic-auth for the school host only |

```xml
<data-extraction-rules>
    <cloud-backup>
        <include domain="sharedpref" path="." />
        <exclude domain="sharedpref" path="lgka-auth.xml" />
    </cloud-backup>
    <device-transfer>
        <include domain="sharedpref" path="." />
        <exclude domain="sharedpref" path="lgka-auth.xml" />
    </device-transfer>
</data-extraction-rules>
```
<sub>`app/src/main/res/xml/data_extraction_rules.xml`; `backup_rules.xml` mirrors it for pre-Android-12 devices and carries the comment *"Preferences back up; the credential store does not. Cache files are never included."*</sub>

The design consequence: **a student's accent and theme survive a device transfer; their password does not.**
That is the correct trade and it is enforced by configuration, not by convention.

### 3.1 Sign-in state is conjunctive

```kotlin
fun isSignedIn(credentials: Credentials): Boolean = isAuthenticated && credentials.load() != null
```
<sub>`app/src/main/kotlin/com/lgka/Prefs.kt`</sub>

A restored backup brings `isAuthenticated = true` but no credential file, so the student lands on the login
screen instead of on a home hub where every request fails.

### 3.2 Logout

Destructive, confirmed, and honest about the consequence:

> Wirklich abmelden? Du musst die Zugangsdaten danach erneut eingeben.
> — `R.string.logout_confirm`, `app/src/main/res/values/strings.xml`

The confirm action is tinted `error`; the cancel action is a plain `TextButton`. `signOut` clears the
credential file **and** the flag, and `RootNav` swaps the tree to `AuthScreen` immediately.

## 4. Basic auth inside the WebView

```kotlin
override fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler, host: String?, realm: String?) {
    val creds = credentials.load()
    if (SchoolApi.isSchoolHost(host) && creds != null) handler.proceed(creds.user, creds.password)
    else handler.cancel()
}
```
<sub>`app/src/main/kotlin/com/lgka/WebScreen.kt`</sub>

Credentials are offered **only** to the school's own host. Anything else gets `cancel()`. The WebView also
runs with `LOAD_NO_CACHE` and clears all cookies on creation, so nothing from a school session persists.

## 5. Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
```
<sub>`app/src/main/AndroidManifest.xml` — the complete list</sub>

No location (weather is fixed to Karlsruhe), no storage (sharing goes through
`FileProvider` authority `com.lgka.files`), no notifications, no camera, no contacts. There is no runtime
permission dialog anywhere in the app, so there is no permission-rationale UI to design.

## 6. Legal surfaces

Two links in the settings sheet, both marked with the `OpenInNew` glyph because they leave the app:

| Label (de / en) | Destination |
|---|---|
| Datenschutzerklärung / Privacy policy | `https://luka-loehr.github.io/LGKA/privacy.html` |
| Impressum / Legal notice | `https://luka-loehr.github.io/LGKA/impressum.html` |

<sub>`app/src/main/kotlin/com/lgka/Settings.kt`</sub>

The sheet footer shows `© <year> Luka Löhr • v3.0.0`, with the year computed at runtime
(`java.time.Year.now()`) and the version from `BuildConfig.VERSION_NAME`.

## 7. Third-party disclosure

Two places where the app says plainly that something is not its own:

**Sick note** — a dedicated pre-screen the first time, with two cards:

> Die Krankmeldung wird vom Lessing-Gymnasium bereitgestellt und ist unabhängig von der LGKA+ App.
> — `R.string.krankmeldung_disclaimer`

> Bei technischen Fragen oder Problemen wende dich bitte direkt an das Lessing-Gymnasium Karlsruhe.
> — `R.string.krankmeldung_contact`

**Weather** — a tappable attribution line at the bottom of the weather screen, with a 48 dp minimum height:

> Wetterdaten von Open-Meteo.com
> — `R.string.weather_attribution`

- [x] Any new third-party surface gets an equivalent disclosure before or inside it.
- [ ] Don't present external content as if the app produced it.

## Related

[01 · Brand identity](01-brand-identity.md) · [06 · Components](06-components.md) · [07 · Navigation](07-navigation.md) · [12 · Writing & localization](12-writing-and-localization.md)

## Sources

| Source | Accessed |
|---|---|
| [Material 3 — Accessible design overview](https://m3.material.io/foundations/accessible-design/overview) | 2026-09-12 |
| [Compose — Handling user interactions](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions) | 2026-09-12 |
| [Android — Layout basics](https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-basics) | 2026-09-12 |
| [Android — What great technical quality looks like](https://developer.android.com/quality/technical) | 2026-09-12 |
| App source: `kotlin/com/lgka/{Onboarding,Credentials,Prefs,Settings,WebScreen,MainActivity}.kt`, `res/xml/{backup_rules,data_extraction_rules,file_paths}.xml`, `AndroidManifest.xml`, `res/values/strings.xml`, `res/values-en/strings.xml` | 2026-09-12 |
