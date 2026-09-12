package com.lgka

import android.app.LocaleManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Automated store/marketing screenshots — the native port of the Flutter
 * `screenshot_test.dart`, driven by scripts/screenshots.sh.
 *
 * Instrumentation arguments (`-e key value`):
 *   login  user:pass for the school website (required except welcome)
 *   theme  dark | light | system (default light)
 *   locale de | en (default de)
 *   cls    schedule class to preselect (default 7b)
 *
 * Output: the app's external files dir, `screenshots/<name>.png`, pulled
 * by the script. Every test launches the activity fresh through the DEBUG
 * seed extras (see MainActivity.applyDebugSeed) — no coordinates.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private val target = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private val outDir: File by lazy { File(target.getExternalFilesDir(null), "screenshots").apply { mkdirs() } }

    @Before
    fun setLocale() {
        val locale = args.getString("locale") ?: "de"
        target.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(locale)
    }

    @After
    fun close() { scenario?.close() }

    private fun launch(seeded: Boolean) {
        val intent = Intent(target, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("lgka_debug_reset", true)
            putExtra("lgka_debug_theme", args.getString("theme") ?: "light")
            putExtra("lgka_debug_accent", args.getString("accent") ?: "blue")
            if (seeded) {
                putExtra("lgka_debug_login", args.getString("login") ?: "")
                putExtra("lgka_debug_class", args.getString("cls") ?: "7b")
            }
        }
        scenario = ActivityScenario.launch(intent)
        compose.waitForIdle()
    }

    private val device: UiDevice by lazy { UiDevice.getInstance(instrumentation) }

    /** Emulator system dialogs (ANR "isn't responding", crash) must never end up in a capture. */
    private fun dismissSystemDialogs() {
        repeat(3) {
            val wait = device.findObject(By.textContains("Wait")) ?: device.findObject(By.res("android:id/aerr_wait"))
            val close = device.findObject(By.res("android:id/aerr_close"))
            when {
                wait != null -> wait.click()
                close != null -> close.click()
                else -> return
            }
            device.wait(Until.gone(By.textContains("responding")), 3_000)
        }
    }

    /** Clean status bar (09:41, full battery, Wi-Fi) — SystemUI drops demo state on a uimode
     *  change, so it is re-applied from inside the test right before the first capture. */
    private fun demoStatusBar() {
        listOf(
            "enter", "clock -e hhmm 0941", "battery -e level 100 -e plugged false",
            "network -e wifi show -e level 4 -e fully true", "network -e mobile hide",
            "network -e airplane hide -e nosim hide", "notifications -e visible false",
        ).forEach { cmd ->
            instrumentation.uiAutomation.executeShellCommand("am broadcast -a com.android.systemui.demo -e command $cmd").close()
        }
        Thread.sleep(500)
    }

    /** A frame that is (almost) one flat colour is a window transition, not the screen. */
    private fun isFlat(bitmap: Bitmap): Boolean {
        val small = Bitmap.createScaledBitmap(bitmap, 54, 120, false)
        val px = IntArray(54 * 120).also { small.getPixels(it, 0, 54, 0, 0, 54, 120) }
        val top = px.toList().groupingBy { it }.eachCount().values.max()
        return top > px.size * 0.995 // a real transition frame is >99.8 % one colour; sparse dark screens sit around 97 %
    }

    private fun save(name: String) {
        compose.waitForIdle()
        device.wakeUp() // the emulator display must never have blanked
        dismissSystemDialogs()
        demoStatusBar()
        Thread.sleep(600) // let ripples and springs settle
        check(device.findObject(By.textContains("responding")) == null) { "system dialog visible during capture of $name" }
        var bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot()
        var attempts = 0
        while (isFlat(bitmap) && attempts++ < 15) { // cold swiftshader first frames can take >5 s
            Thread.sleep(1_000)
            bitmap = instrumentation.uiAutomation.takeScreenshot()
        }
        if (isFlat(bitmap)) {
            File(outDir, "$name.flat.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            error("capture of $name is a flat frame after $attempts retries (saved as $name.flat.png)")
        }
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitForHome() {
        compose.waitUntil(45_000) { compose.onAllNodesWithTag("home.weather").fetchSemanticsNodes().isNotEmpty() }
        // events load last; never capture their skeleton (an empty calendar is legitimate, so no assert)
        runCatching { compose.waitUntil(25_000) { compose.onAllNodesWithTag("home.event").fetchSemanticsNodes().isNotEmpty() } }
        Thread.sleep(1_500)
    }

    private fun tap(tag: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    @Test fun t01Welcome() {
        launch(seeded = false)
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("onboarding.continue").fetchSemanticsNodes().isNotEmpty() }
        save("01_welcome")
    }

    @Test fun t02Home() { launch(seeded = true); waitForHome(); save("02_home") }

    @Test fun t03Weather() {
        launch(seeded = true); waitForHome()
        tap("home.weather"); Thread.sleep(2_500); save("03_weather")
    }

    @Test fun t04News() {
        launch(seeded = true); waitForHome()
        tap("home.news")
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("news.row").fetchSemanticsNodes().isNotEmpty() }
        save("04_news")
    }

    @Test fun t05NewsDetail() {
        launch(seeded = true); waitForHome()
        tap("home.news")
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("news.row").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithTag("news.row")[0].performClick()
        // the article body must be loaded — never capture the loading indicator
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("news.detail").fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(2_000); save("05_news_detail")
    }

    @Test fun t06Plan() {
        launch(seeded = true); waitForHome()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("home.plan.today").fetchSemanticsNodes().isNotEmpty() }
        val today = compose.onAllNodesWithTag("home.plan.today").fetchSemanticsNodes().firstOrNull()
        val tomorrow = compose.onAllNodesWithTag("home.plan.tomorrow").fetchSemanticsNodes().firstOrNull()
        fun enabled(n: androidx.compose.ui.semantics.SemanticsNode?) =
            n != null && !n.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
        val tag = when { enabled(today) -> "home.plan.today"; enabled(tomorrow) -> "home.plan.tomorrow"; else -> null }
        assumeTrue("no substitution plan available today", tag != null)
        tap(tag!!)
        // the first PDF page must be rasterized — never capture an empty viewer
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("plan.page").fetchSemanticsNodes().isNotEmpty() }
        Thread.sleep(1_000); save("06_plan")
    }

    @Test fun t07Settings() {
        launch(seeded = true); waitForHome()
        tap("home.settings"); Thread.sleep(800); save("07_settings")
    }

    /** Regression for the login gate: real credentials must lead to the home hub. */
    @Test fun t08LoginFlow() {
        val login = args.getString("login") ?: ""
        assumeTrue("login argument not set", login.contains(":"))
        launch(seeded = false)
        repeat(4) { tap("onboarding.continue") }
        compose.onNodeWithTag("auth.username").performTextInput(login.substringBefore(":"))
        compose.onNodeWithTag("auth.password").performTextInput(login.substringAfter(":"))
        tap("auth.login")
        waitForHome()
        save("08_after_login")
    }
}
