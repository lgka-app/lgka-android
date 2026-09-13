package com.lgka

import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.remember
import android.view.View
import android.view.HapticFeedbackConstants
import android.os.Build
import androidx.compose.runtime.Composable

/**
 * Haptics — the same grammar as the iOS app (light for navigation and secondary taps,
 * medium for primary actions, success/error for outcomes), driven through the View so
 * it works inside dialogs and sheets too.
 */
class Haptics(private val view: View) {
    private fun perform(constant: Int) { view.performHapticFeedback(constant) }
    fun light() = perform(HapticFeedbackConstants.CONTEXT_CLICK)
    fun medium() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP)
    fun success() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
    fun error() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { view.isHapticFeedbackEnabled = true; Haptics(view) }
}
