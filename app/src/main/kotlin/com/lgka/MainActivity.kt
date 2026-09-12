package com.lgka

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.ContentTransform
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels { HomeViewModel.factory(appContainer.api) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = appContainer
        applyDebugSeed(container)

        // main.dart parity: subs + weather are invalidated on background, so
        // every resume refreshes them (debounced in the view model).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (container.prefs.isSignedIn(container.credentials)) homeViewModel.refreshOnForeground()
            }
        }
        // main.dart parity: 1-minute expired-cache refresh timer — only while
        // the activity is started, never in the background.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(60_000)
                    if (container.prefs.isSignedIn(container.credentials)) homeViewModel.loadAll()
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
        container.prefs.signOut(container.credentials)
        container.prefs.onboardingCompleted = false
        container.prefs.krankmeldungInfoShown = false
        container.prefs.selectedScheduleClass = ""
        container.prefs.accentColor = "blue"
        container.prefs.themeMode = "system"
    }
    intent?.getStringExtra("lgka_debug_login")?.let { pair ->
        val (user, password) = pair.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        container.credentials.save(Credentials.Pair(user, password))
        container.prefs.isAuthenticated = true
        container.prefs.onboardingCompleted = true
    }
    intent?.getStringExtra("lgka_debug_accent")?.let { container.prefs.accentColor = it }
    intent?.getStringExtra("lgka_debug_theme")?.let { container.prefs.themeMode = it }
    intent?.getStringExtra("lgka_debug_class")?.let { container.prefs.selectedScheduleClass = it }
}

/// Route gating — mirrors main.dart's initialRoute logic, kept live.
@Composable
fun RootNav() {
    val container = LocalContainer.current
    val prefs = container.prefs
    when {
        !prefs.onboardingCompleted -> OnboardingFlow()
        !prefs.isSignedIn(container.credentials) -> AuthScreen()
        else -> MainNav()
    }
}

@Serializable sealed interface Route : NavKey
@Serializable data object HomeRoute : Route
@Serializable data object WeatherRoute : Route
@Serializable data object NewsRoute : Route
@Serializable data class NewsDetailRoute(val url: String) : Route
@Serializable data object KrankmeldungInfoRoute : Route
@Serializable data object KrankmeldungFormRoute : Route
@Serializable data object BugReportRoute : Route

@Composable
fun MainNav() {
    val backStack = rememberNavBackStack(HomeRoute)
    val haptics = rememberHaptics()
    val pop: () -> Unit = { haptics.light(); backStack.removeLastOrNull() }
    NavDisplay(
        backStack = backStack,
        onBack = { haptics.light(); backStack.removeLastOrNull() },
        transitionSpec = { iosPush() },
        popTransitionSpec = { iosPop() },
        predictivePopTransitionSpec = { iosPop() },
        entryProvider = entryProvider {
            entry<HomeRoute> { HomeScreen(onNavigate = { backStack.add(it) }) }
            entry<WeatherRoute> { WeatherScreen(onBack = pop) }
            entry<NewsRoute> {
                NewsListScreen(onBack = pop, onOpen = { backStack.add(NewsDetailRoute(it)) })
            }
            entry<NewsDetailRoute> { key ->
                NewsDetailScreen(url = key.url, onBack = pop, onOpen = { backStack.add(NewsDetailRoute(it)) })
            }
            entry<KrankmeldungInfoRoute> {
                KrankmeldungInfoScreen(onBack = pop, onContinue = {
                    backStack.removeLastOrNull()
                    backStack.add(KrankmeldungFormRoute)
                })
            }
            entry<KrankmeldungFormRoute> {
                WebScreen(
                    url = "https://drkrankmeldung.lgka-online.de",
                    title = androidx.compose.ui.res.stringResource(R.string.krankmeldung),
                    confineToHost = "lgka-online.de",
                    onBack = pop,
                )
            }
            entry<BugReportRoute> {
                WebScreen(
                    url = "https://docs.google.com/forms/d/e/1FAIpQLSdknGu7-xgFurrghbUYOwoYu-Vsaftar6PGLzMv64UFpwJtRw/viewform?usp=publish-editor",
                    title = androidx.compose.ui.res.stringResource(R.string.bug_report_title),
                    onBack = pop,
                )
            }
        },
    )
}

// ── iOS navigation transitions: the new screen slides in from the right while the
// previous one parallaxes a third of the way out and dims; pop reverses it.
private const val NAV_MS = 380
private val navEasing = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)

fun iosPush(): ContentTransform =
    (slideInHorizontally(tween(NAV_MS, easing = navEasing)) { it }) togetherWith
        (slideOutHorizontally(tween(NAV_MS, easing = navEasing)) { -it / 3 } + fadeOut(tween(NAV_MS), targetAlpha = 0.85f))

fun iosPop(): ContentTransform =
    (slideInHorizontally(tween(NAV_MS, easing = navEasing)) { -it / 3 } + fadeIn(tween(NAV_MS), initialAlpha = 0.85f)) togetherWith
        slideOutHorizontally(tween(NAV_MS, easing = navEasing)) { it }
