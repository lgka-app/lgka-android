package com.lgka

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.ContentTransform
import kotlinx.serialization.Serializable

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

@Serializable data object BugReportRoute : Route

/** Any web page in the app's own web screen (privacy, legal notice, article links, …). */
@Serializable data class WebRoute(val url: String, val title: String) : Route

@Composable
fun MainNav() {
    val backStack = rememberNavBackStack(HomeRoute)
    val haptics = rememberHaptics()
    val context = LocalContext.current
    // A second tap on a back arrow while the pop transition still shows the old screen
    // would remove HomeRoute too, and NavDisplay crashes on an empty back stack.
    val pop: () -> Unit = { if (backStack.size > 1) { haptics.light(); backStack.removeLastOrNull() } }
    // Every http(s) link — Compose text links included — opens the app's own web screen;
    // anything else (mailto, tel, files) goes to the system. Only the Krankmeldung form
    // deliberately opens the user's real browser.
    val openWeb = remember(context) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val parsed = uri.toUri()
                if (parsed.scheme == "http" || parsed.scheme == "https") {
                    backStack.add(WebRoute(uri, parsed.host?.removePrefix("www.") ?: ""))
                } else {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, parsed)) }
                }
            }
        }
    }
    CompositionLocalProvider(LocalUriHandler provides openWeb) {
    NavDisplay(
        backStack = backStack,
        onBack = pop,
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
                    if (backStack.size > 1) backStack.removeLastOrNull()
                    openKrankmeldungForm(context)
                })
            }
            entry<WebRoute> { key -> WebScreen(url = key.url, title = key.title, onBack = pop) }
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
