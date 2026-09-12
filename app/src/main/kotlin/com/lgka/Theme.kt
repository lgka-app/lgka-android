package com.lgka

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.core.graphics.drawable.toDrawable
import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
val LocalHomeViewModel = staticCompositionLocalOf<HomeViewModel> { error("HomeViewModel not provided") }

/**
 * Brand theme on Material 3 Expressive: pure-black dark / #F2F2F7 light
 * surfaces (app_theme.dart parity), the user's accent as `primary` with a
 * WCAG-safe `onPrimary`, and the expressive spring motion scheme.
 * See DESIGN_GUIDELINES.md §1.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LgkaTheme(prefs: Prefs, content: @Composable () -> Unit) {
    val dark = when (prefs.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    // The window itself must match the in-app theme (an in-app dark override on a light
    // system otherwise shows the light window background behind the navigation bar), and
    // the status/navigation bar icons must contrast with it.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.setBackgroundDrawable((if (dark) 0xFF000000.toInt() else 0xFFF2F2F7.toInt()).toDrawable())
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val accent = prefs.accent
    val scheme: ColorScheme = if (dark) {
        darkColorScheme(
            primary = accent.color,
            onPrimary = accent.onColor,
            primaryContainer = accent.color.copy(alpha = 0.24f).compositeOver(Color.Black),
            onPrimaryContainer = Color.White,
            secondary = accent.color,
            onSecondary = accent.onColor,
            secondaryContainer = accent.color.copy(alpha = 0.18f).compositeOver(Color.Black),
            onSecondaryContainer = Color.White,
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFB8B8BE),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF141414),
            surfaceContainer = Color(0xFF1E1E1E),
            surfaceContainerHigh = Color(0xFF262626),
            surfaceContainerHighest = Color(0xFF2E2E2E),
            outline = Color(0xFF8E8E93),
            outlineVariant = Color(0xFF2C2C2E),
        )
    } else {
        lightColorScheme(
            primary = accent.color,
            onPrimary = accent.onColor,
            primaryContainer = accent.color.copy(alpha = 0.14f).compositeOver(Color.White),
            onPrimaryContainer = Color(0xFF1A1A1A),
            secondary = accent.color,
            onSecondary = accent.onColor,
            secondaryContainer = accent.color.copy(alpha = 0.12f).compositeOver(Color.White),
            onSecondaryContainer = Color(0xFF1A1A1A),
            background = Color(0xFFF2F2F7),
            onBackground = Color(0xFF1A1A1A),
            surface = Color(0xFFF2F2F7),
            onSurface = Color(0xFF1A1A1A),
            onSurfaceVariant = Color(0xFF5C5C63),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color.White,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color(0xFFF7F7FA),
            surfaceContainerHighest = Color(0xFFEDEDF0),
            outline = Color(0xFF8E8E93),
            outlineVariant = Color(0xFFD8D8DC),
        )
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/** WMO weather code -> localized description resource. */
fun wmoRes(code: Int): Int = when (code) {
    0 -> R.string.wmo0; 1 -> R.string.wmo1; 2 -> R.string.wmo2; 3 -> R.string.wmo3
    45 -> R.string.wmo45; 48 -> R.string.wmo48
    51 -> R.string.wmo51; 53 -> R.string.wmo53; 55 -> R.string.wmo55; 56 -> R.string.wmo56; 57 -> R.string.wmo57
    61 -> R.string.wmo61; 63 -> R.string.wmo63; 65 -> R.string.wmo65; 66 -> R.string.wmo66; 67 -> R.string.wmo67
    71 -> R.string.wmo71; 73 -> R.string.wmo73; 75 -> R.string.wmo75; 77 -> R.string.wmo77
    80 -> R.string.wmo80; 81 -> R.string.wmo81; 82 -> R.string.wmo82; 85 -> R.string.wmo85; 86 -> R.string.wmo86
    95 -> R.string.wmo95; 96 -> R.string.wmo96; 99 -> R.string.wmo99
    else -> R.string.wmo_unknown
}

/** German Untis weekday name -> localized resource; null for "weekend"/unknown. */
fun weekdayRes(german: String?): Int? = when (german) {
    "Montag" -> R.string.weekday_montag
    "Dienstag" -> R.string.weekday_dienstag
    "Mittwoch" -> R.string.weekday_mittwoch
    "Donnerstag" -> R.string.weekday_donnerstag
    "Freitag" -> R.string.weekday_freitag
    "Samstag" -> R.string.weekday_samstag
    "Sonntag" -> R.string.weekday_sonntag
    else -> null
}
