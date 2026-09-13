package com.lgka

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
