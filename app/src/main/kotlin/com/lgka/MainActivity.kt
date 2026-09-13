package com.lgka

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels { HomeViewModel.factory(appContainer) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = appContainer
        applyDebugSeed(container)

        // Every resume is one cheap /v1/sync (hashes out, changes in; debounced).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (container.prefs.isSignedIn(container.credentials)) homeViewModel.refreshOnForeground()
            }
        }
        // While on screen, poll the sync endpoint once a minute (~0.5 KB when
        // nothing changed) so a new substitution plan shows up within a minute.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(60_000)
                    if (container.prefs.isSignedIn(container.credentials)) homeViewModel.sync()
                }
            }
        }

        setContent {
            CompositionLocalProvider(
                LocalContainer provides container,
                LocalHomeViewModel provides homeViewModel,
            ) {
                LgkaTheme(container.prefs) {
                    Box {
                        RootNav()
                        FireworksOverlay()
                    }
                }
            }
        }
    }
}

/**
 * Debug builds only: seed login and preferences from launch intent extras so
 * screenshots and UI checks can skip onboarding, e.g.
 * `adb shell am start -n com.lgka/.MainActivity --es lgka_debug_login user:pass --es lgka_debug_accent mint --es lgka_debug_theme dark`.
 */
private fun MainActivity.applyDebugSeed(container: AppContainer) {
    if (!BuildConfig.DEBUG) return
    if (intent?.hasExtra("lgka_debug_reset") == true) {
        container.prefs.reset(container.credentials)
        container.store.clear() // a reset run must not start from a previous login's snapshot
    }
    intent?.getStringExtra("lgka_debug_login")?.let { pair ->
        val (user, password) = pair.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        container.credentials.save(lgka.api.Login(user, password))
        container.prefs.isAuthenticated = true
        container.prefs.onboardingCompleted = true
    }
    intent?.getStringExtra("lgka_debug_accent")?.let { container.prefs.accentColor = it }
    intent?.getStringExtra("lgka_debug_theme")?.let { container.prefs.themeMode = it }
    intent?.getStringExtra("lgka_debug_class")?.let { container.prefs.selectedScheduleClass = it }
}
