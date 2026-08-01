package com.lgka

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

lateinit var prefs: Prefs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PDFBoxResourceLoader.init(applicationContext)
        DiskCache.init(applicationContext)
        prefs = Prefs(applicationContext)

        setContent {
            LgkaTheme {
                LaunchedEffect(Unit) { HomeModel.bootstrap() }
                RootNav()
            }
        }
    }
}

/// Theme — pure-black dark / F2F2F7 light, accent-driven (app_theme.dart).
@Composable
fun LgkaTheme(content: @Composable () -> Unit) {
    val dark = when (prefs.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val accent = prefs.accent
    val scheme: ColorScheme = if (dark) {
        darkColorScheme(
            primary = accent, onPrimary = Color.White,
            surface = Color.Black, background = Color.Black,
            surfaceContainer = Color(0xFF1E1E1E),
            surfaceContainerHigh = Color(0xFF262626),
            onSurface = Color.White)
    } else {
        lightColorScheme(
            primary = accent, onPrimary = Color.White,
            surface = Color(0xFFF2F2F7), background = Color(0xFFF2F2F7),
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White,
            onSurface = Color(0xFF1A1A1A))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/// Route gating — mirrors main.dart's initialRoute logic, kept live.
@Composable
fun RootNav() {
    if (!prefs.onboardingCompleted) {
        OnboardingFlow()
    } else if (!prefs.isAuthenticated) {
        AuthScreen()
    } else {
        MainNav()
    }
}

@Composable
fun MainNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") { HomeScreen(nav) }
        composable("weather") { WeatherScreen(nav) }
        composable("news") { NewsListScreen(nav) }
        composable("newsDetail/{index}") { entry ->
            NewsDetailScreen(nav, entry.arguments?.getString("index")?.toIntOrNull() ?: 0)
        }
        composable("krankmeldungInfo") { KrankmeldungInfoScreen(nav) }
        composable("krankmeldungForm") {
            WebScreen(nav, "https://drkrankmeldung.lgka-online.de", L.s("krankmeldung"))
        }
        composable("bugReport") {
            WebScreen(nav,
                "https://docs.google.com/forms/d/e/1FAIpQLSdknGu7-xgFurrghbUYOwoYu-Vsaftar6PGLzMv64UFpwJtRw/viewform?usp=publish-editor",
                L.s("bugReportTitle"))
        }
    }
}
