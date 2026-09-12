package com.lgka

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/// welcome -> features -> accent -> appearance -> auth (app_router parity)
@Composable
fun OnboardingFlow() {
    var step by remember { mutableIntStateOf(0) }
    when (step) {
        0 -> WelcomeStep { step = 1 }
        1 -> FeaturesStep { step = 2 }
        2 -> AccentStep { step = 3 }
        3 -> AppearanceStep { step = 4 }
        else -> AuthScreen()
    }
}

/** The one primary button of the onboarding flow (welcome, features, accent, appearance, login). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                  containerColor: Color = MaterialTheme.colorScheme.primary, content: (@Composable () -> Unit)? = null) {
    Button(onClick = onClick, enabled = enabled,
           colors = ButtonDefaults.buttonColors(containerColor = containerColor, disabledContainerColor = containerColor),
           modifier = modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
        if (content != null) content() else Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OnboardingScaffold(button: String, onContinue: () -> Unit, horizontalPadding: Dp = 32.dp,
                               content: @Composable ColumnScope.() -> Unit) {
    val haptics = rememberHaptics()
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().readableWidth(560.dp).padding(horizontal = horizontalPadding)) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = content)
            // padding BEFORE the height: the old order shrank the button to 36 dp
            PrimaryButton(button, onClick = { haptics.medium(); onContinue() },
                          modifier = Modifier.padding(top = 16.dp, bottom = 16.dp).testTag("onboarding.continue"))
        }
    }
}

@Composable
fun WelcomeStep(onContinue: () -> Unit) {
    // WelcomeScreen (iOS): 160dp mark, large bold headline, secondary subtitle, centred
    OnboardingScaffold(stringResource(R.string.continue_label), onContinue) {
        Spacer(Modifier.weight(1f))
        Image(painterResource(R.mipmap.ic_launcher_foreground), stringResource(R.string.a11y_app_logo), Modifier.size(160.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.welcome_headline), style = MaterialTheme.typography.headlineLarge,
             fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.welcome_subtitle), textAlign = TextAlign.Center,
             style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun FeaturesStep(onContinue: () -> Unit) {
    val features = listOf<Triple<ImageVector, Int, Int>>(
        Triple(Icons.Outlined.CalendarToday, R.string.feature_substitution_title, R.string.feature_substitution_desc),
        Triple(Icons.Outlined.Schedule, R.string.feature_schedule_title, R.string.feature_schedule_desc),
        Triple(Icons.Outlined.Cloud, R.string.feature_weather_title, R.string.feature_weather_desc),
        Triple(Icons.Outlined.Newspaper, R.string.feature_news_title, R.string.feature_news_desc),
        Triple(Icons.Outlined.MedicalServices, R.string.feature_sick_title, R.string.feature_sick_desc),
        Triple(Icons.Outlined.Event, R.string.feature_events_title, R.string.feature_events_desc))
    // what_you_can_do_screen.dart: one card per feature (radius 16, 48 dp icon tile), uniform height
    OnboardingScaffold(stringResource(R.string.continue_label), onContinue, horizontalPadding = 20.dp) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.info_header), style = MaterialTheme.typography.headlineMedium,
                 fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() })
            Spacer(Modifier.height(16.dp))
            features.forEach { (icon, title, desc) ->
                Card(shape = CardShape, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).semantics(mergeDescendants = true) {}) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(2.dp))
                            Text(stringResource(desc), style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                                 overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
fun AccentStep(onContinue: () -> Unit) {
    OnboardingScaffold(stringResource(R.string.continue_label), onContinue) {
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.accent_color_title), style = MaterialTheme.typography.headlineMedium,
             fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.accent_color_description), textAlign = TextAlign.Center,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        AccentRow(swatchSize = 56)
        Spacer(Modifier.weight(1f))
    }
}

/** Accent picker — the iOS palette: plain colour circles, the selected one carries a white check
 *  and a soft ring in its own colour. Every swatch is a 48dp+ radio-button target with a spoken name. */
@Composable
fun AccentRow(swatchSize: Int, modifier: Modifier = Modifier) {
    val prefs = LocalContainer.current.prefs
    val haptics = rememberHaptics()
    val selectedLabel = stringResource(R.string.a11y_selected)
    val target = maxOf(swatchSize + 14, 48)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (swatchSize >= 44) 12.dp else 4.dp)) {
        Accent.entries.forEach { accent ->
            val selected = prefs.accentColor == accent.key
            val name = stringResource(accent.labelRes)
            val ring by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "accentRing")
            val check by animateFloatAsState(if (selected) 1f else 0f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "accentCheck")
            Box(
                Modifier.size(target.dp)
                    .clip(CircleShape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = {
                        if (!selected) { haptics.light(); prefs.accentColor = accent.key }
                    })
                    .semantics { contentDescription = if (selected) "$name, $selectedLabel" else name },
                contentAlignment = Alignment.Center) {
                Box(Modifier.size((swatchSize + 10).dp).border(2.dp, accent.color.copy(alpha = 0.45f * ring), CircleShape))
                Box(Modifier.size(swatchSize.dp).background(accent.color, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, Modifier.size((swatchSize * 0.5f).dp).scale(check), tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun AppearanceStep(onContinue: () -> Unit) {
    OnboardingScaffold(stringResource(R.string.lets_go), onContinue) {
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.headlineMedium,
             fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
        Spacer(Modifier.height(32.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ThemeModeRow() }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun ThemeModeRow() {
    val prefs = LocalContainer.current.prefs
    val haptics = rememberHaptics()
    val options = listOf(
        Triple("dark", Icons.Filled.DarkMode, R.string.theme_dark),
        Triple("system", Icons.Filled.BrightnessAuto, R.string.theme_auto),
        Triple("light", Icons.Filled.LightMode, R.string.theme_light))
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { i, (mode, icon, label) ->
            SegmentedButton(
                selected = prefs.themeMode == mode,
                onClick = { if (prefs.themeMode != mode) { haptics.light(); prefs.themeMode = mode } },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                icon = { Icon(icon, null, Modifier.size(16.dp)) }) {
                Text(stringResource(label))
            }
        }
    }
}

/// Login gate — the school website's credentials are verified by api.lgka.app (never locally) against the
/// server and stored privately; the app never compares them locally.
@Composable
fun AuthScreen() {
    val container = LocalContainer.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var flash by remember { mutableIntStateOf(0) } // 0 none, 1 error, 2 success
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val rotatedText = stringResource(R.string.login_password_rotated)
    // The API confirmed the school changed the password: say so until the next successful login.
    LaunchedEffect(Unit) { if (container.prefs.passwordRotated) message = rotatedText }

    val canLogin = username.isNotBlank() && password.isNotBlank() && !loading
    // auth_screen.dart: 300 ms to red / green, 600 ms hold, 300 ms back; half-opacity accent while empty
    val accent = MaterialTheme.colorScheme.primary
    val buttonColor by animateColorAsState(
        targetValue = when {
            flash == 1 -> Color(0xFFF44336)
            flash == 2 -> Color(0xFF4CAF50)
            canLogin || loading -> accent
            else -> accent.copy(alpha = 0.5f)
        },
        animationSpec = tween(300), label = "loginButton")

    fun validate() {
        if (!canLogin || flash != 0) return
        val pair = lgka.api.Login(username.trim(), password.trim())
        haptics.medium()
        loading = true
        message = null
        scope.launch {
            try {
                if (container.api.checkCredentials(pair)) {
                    container.credentials.save(pair)
                    loading = false
                    flash = 2
                    haptics.success()
                    delay(900)
                    container.prefs.passwordRotated = false
                    container.prefs.isAuthenticated = true
                    container.prefs.onboardingCompleted = true
                } else {
                    loading = false
                    flash = 1; haptics.error()
                    delay(900); flash = 0
                }
            } catch (e: Exception) {
                // offline, 403/429/5xx: the same calm red, no text
                loading = false
                flash = 1; haptics.error()
                delay(900); flash = 0
            } finally {
                loading = false
            }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().readableWidth(560.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.auth_title), style = MaterialTheme.typography.headlineMedium,
                 fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.auth_subtitle), textAlign = TextAlign.Center,
                 style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(48.dp))
            OutlinedTextField(
                value = username, onValueChange = { username = it },
                label = { Text(stringResource(R.string.username)) },
                leadingIcon = { Icon(Icons.Outlined.Person, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("auth.username"))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { validate() }),
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("auth.password"))
            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                     style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(32.dp))
            PrimaryButton(stringResource(R.string.login), onClick = { validate() }, enabled = canLogin || flash != 0,
                          containerColor = buttonColor, modifier = Modifier.testTag("auth.login")) {
                when {
                    loading -> CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
                    flash == 2 -> Icon(Icons.Filled.Check, null, tint = Color.White)
                    else -> Text(stringResource(R.string.login), style = MaterialTheme.typography.titleMedium,
                                 fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}
