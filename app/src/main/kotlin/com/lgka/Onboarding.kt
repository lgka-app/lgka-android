package com.lgka

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/// welcome -> features -> accent -> appearance -> auth (app_router parity)
@Composable
fun OnboardingFlow() {
    var step by remember { mutableStateOf(0) }
    when (step) {
        0 -> WelcomeStep { step = 1 }
        1 -> FeaturesStep { step = 2 }
        2 -> AccentStep { step = 3 }
        3 -> AppearanceStep { step = 4 }
        else -> AuthScreen()
    }
}

@Composable
private fun OnboardingScaffold(button: String, onContinue: () -> Unit,
                               content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp).systemBarsPadding()) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                   content = content)
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)) {
                Text(button, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun WelcomeStep(onContinue: () -> Unit) {
    OnboardingScaffold(L.s("continueLabel"), onContinue) {
        Spacer(Modifier.weight(1f))
        Image(painterResource(R.mipmap.ic_launcher), null, Modifier.size(140.dp))
        Spacer(Modifier.height(16.dp))
        Text(L.s("welcomeHeadline"), fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(L.s("welcomeSubtitle"), textAlign = TextAlign.Center,
             color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun FeaturesStep(onContinue: () -> Unit) {
    val features = listOf<Triple<ImageVector, String, String>>(
        Triple(Icons.Outlined.CalendarToday, "featureSubstitutionTitle", "featureSubstitutionDesc"),
        Triple(Icons.Outlined.Schedule, "featureScheduleTitle", "featureScheduleDesc"),
        Triple(Icons.Outlined.Cloud, "featureWeatherTitle", "featureWeatherDesc"),
        Triple(Icons.Outlined.Newspaper, "featureNewsTitle", "featureNewsDesc"),
        Triple(Icons.Outlined.MedicalServices, "featureSickTitle", "featureSickDesc"),
        Triple(Icons.Outlined.Event, "featureEventsTitle", "featureEventsDesc"))
    OnboardingScaffold(L.s("continueLabel"), onContinue) {
        Text(L.s("infoHeader"), fontSize = 28.sp, fontWeight = FontWeight.Bold,
             modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(features) { (icon, title, desc) ->
                Card(shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(L.s(title), fontWeight = FontWeight.SemiBold)
                            Text(L.s(desc), style = MaterialTheme.typography.bodyMedium,
                                 color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccentStep(onContinue: () -> Unit) {
    OnboardingScaffold(L.s("continueLabel"), onContinue) {
        Spacer(Modifier.weight(1f))
        Text(L.s("accentColorTitle"), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(L.s("accentColorDescription"), textAlign = TextAlign.Center,
             color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(32.dp))
        AccentRow(swatchSize = 56)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun AccentRow(swatchSize: Int) {
    val haptic = LocalHapticFeedback.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Accent.entries.forEach { accent ->
            val selected = prefs.accentColor == accent.key
            Box(
                Modifier.size(swatchSize.dp)
                    .background(accent.color, RoundedCornerShape((swatchSize * 0.32f).dp))
                    .border(if (selected) 3.dp else 0.dp,
                            if (selected) Color.White else Color.Transparent,
                            RoundedCornerShape((swatchSize * 0.32f).dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        prefs.accentColor = accent.key
                    },
                contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Filled.Check, null, tint = Color.White)
                else Box(Modifier.size((swatchSize * 0.24).dp)
                    .background(Color.White.copy(alpha = 0.24f), CircleShape))
            }
        }
    }
}

@Composable
fun AppearanceStep(onContinue: () -> Unit) {
    OnboardingScaffold(L.s("letsGo"), onContinue) {
        Spacer(Modifier.weight(1f))
        Text(L.s("appearanceTitle"), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        ThemeModeRow()
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun ThemeModeRow() {
    val options = listOf(
        Triple("dark", Icons.Filled.DarkMode, L.s("themeDark")),
        Triple("system", Icons.Filled.BrightnessAuto, L.s("themeAuto")),
        Triple("light", Icons.Filled.LightMode, L.s("themeLight")))
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { i, (mode, icon, label) ->
            SegmentedButton(
                selected = prefs.themeMode == mode,
                onClick = { prefs.themeMode = mode },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                icon = { Icon(icon, null, Modifier.size(16.dp)) }) {
                Text(label)
            }
        }
    }
}

/// Password gate — mirrors auth_screen.dart (school website credentials).
@Composable
fun AuthScreen() {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var flash by remember { mutableStateOf(0) } // 0 none, 1 error, 2 success
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val canLogin = username.isNotBlank() && password.isNotBlank() && !loading
    val buttonColor = when (flash) {
        1 -> Color(0xFFD32F2F)
        2 -> Color(0xFF2E7D32)
        else -> if (canLogin) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    }

    fun validate() {
        if (username.trim() == "vertretungsplan" && password.trim() == "ephraim") {
            flash = 2
            scope.launch {
                delay(600)
                loading = true
                delay(600)
                prefs.isAuthenticated = true
                prefs.onboardingCompleted = true
            }
        } else {
            flash = 1
            scope.launch { delay(600); flash = 0 }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp).systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(L.s("authTitle"), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(L.s("authSubtitle"), textAlign = TextAlign.Center,
                 style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(Modifier.height(48.dp))
            OutlinedTextField(
                value = username, onValueChange = { username = it },
                label = { Text(L.s("username")) },
                leadingIcon = { Icon(Icons.Outlined.Person, null) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text(L.s("password")) },
                leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { if (canLogin && flash == 0) validate() },
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp)) {
                if (loading) CircularProgressIndicator(
                    Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
                else Text(L.s("login"), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
