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

    private fun save(name: String) {
        compose.waitForIdle()
        Thread.sleep(600) // let ripples and springs settle
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitForHome() {
        compose.waitUntil(45_000) { compose.onAllNodesWithTag("home.weather").fetchSemanticsNodes().isNotEmpty() }
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
        Thread.sleep(2_500); save("05_news_detail")
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
        tap(tag!!); Thread.sleep(3_000); save("06_plan")
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
