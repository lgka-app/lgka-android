package com.lgka

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.CancellationException
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

    // before Android 13 the language picked in Settings is applied here (13+: per-app language)
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = appContainer
        applyDebugSeed(container)

        // the saved personal plan's PDF follows the app language: re-rendered silently when it changed
        container.customPlans.saved?.let { saved ->
            if (AppLanguage.pdfNeedsRender(this)) lifecycleScope.launch {
                try {
                    CustomPlanSource.pdfFile(this@MainActivity, saved.plan)
                    AppLanguage.pdfRendered(this@MainActivity)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // rendered again when it is opened
                }
            }
        }

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
        container.prefs.signIn(container.credentials, lgka.api.Login(user, password))
    }
    intent?.getStringExtra("lgka_debug_accent")?.let { container.prefs.accentColor = it }
    intent?.getStringExtra("lgka_debug_theme")?.let { container.prefs.themeMode = it }
    intent?.getStringExtra("lgka_debug_class")?.let { container.prefs.selectedScheduleClass = it }
    DebugCustomPlan.seed(container, intent)
}
