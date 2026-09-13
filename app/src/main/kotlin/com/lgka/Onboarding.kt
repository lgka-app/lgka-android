package com.lgka

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
