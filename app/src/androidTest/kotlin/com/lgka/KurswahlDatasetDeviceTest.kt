package com.lgka

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import lgka.plan.CustomPlanBuilder
import lgka.plan.Kurswahl
import lgka.plan.Stufenplan
import lgka.plan.StufenplanParser
import lgka.plan.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The verified Kurswahl photo dataset through the real app path on a phone: each case's three
 * perspective-corrected shots read by [KurswahlScanner.read] (ML Kit, rereads, merge) and built against its
 * Stufenplan, compared with the expected plan. Driven by scripts/kurswahl-dataset-device.sh, which pushes
 * the dataset to the app's external files dir:
 *
 *   dataset/stufenplan_j11_words.json, dataset/stufenplan_j12_words.json
 *   dataset/NN/truth.json, dataset/NN/1.jpg, 2.jpg, 3.jpg
 *
 * Output in `dataset-out/`: results.txt, and NN_shots.json with the recognised boxes of every shot (for
 * replaying ML Kit's reading in the JVM tests). Skipped when no dataset was pushed.
 */
@RunWith(AndroidJUnit4::class)
class KurswahlDatasetDeviceTest {
    @Serializable
    data class Course(val subject: String, val codes: List<String>, val hours: Int)

    @Serializable
    data class ExpectedPlan(val stufe: String, val halbjahr: String, val courses: List<Course>, val totalHours: Int)

    @Serializable
    data class Truth(val sums: List<Int>, val expectedPlan: ExpectedPlan)

    private val json = Json { ignoreUnknownKeys = true }
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val root: File by lazy { File(target.getExternalFilesDir(null), "dataset") }
    private val outDir: File by lazy { File(target.getExternalFilesDir(null), "dataset-out").apply { mkdirs() } }

    private fun stufenplan(name: String): Stufenplan =
        StufenplanParser.parse(json.decodeFromString(ListSerializer(TextBox.serializer()), File(root, name).readText()))

    /** Decoded like a captured photo: software bitmap, at most 4096 px on the long side. */
    private fun decode(file: File): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longSide = maxOf(info.size.width, info.size.height)
        if (longSide > 4096) {
            val scale = 4096f / longSide
            decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
        }
    }

    /** "ok", or what differs from the expected plan. */
    private fun compare(kurswahl: Kurswahl, truth: Truth, plan: Stufenplan): String {
        val built = CustomPlanBuilder.build(kurswahl, plan, truth.expectedPlan.halbjahr)
        val got = built.courses.associate { it.subjectKey to (it.codes.sorted() to it.hours) }
        val want = truth.expectedPlan.courses.associate { it.subject to (it.codes.sorted() to it.hours) }
        val problems = mutableListOf<String>()
        for ((subject, value) in want) {
            val have = got[subject]
            if (have == null) problems += "missing $subject${value.first}" else if (have != value) problems += "$subject ${have.first}≠${value.first}"
        }
        for (subject in got.keys - want.keys) problems += "extra $subject${got.getValue(subject).first}"
        if (built.checks.totalHours != truth.expectedPlan.totalHours) problems += "total ${built.checks.totalHours}≠${truth.expectedPlan.totalHours}"
        if (built.checks.issues.isNotEmpty()) problems += "issues ${built.checks.issues.map { it.kind }}"
        if (kurswahl.sums != truth.sums) problems += "sums ${kurswahl.sums}≠${truth.sums}"
        return if (problems.isEmpty()) "ok" else problems.joinToString("; ")
    }

    @Test
    fun everyDatasetBurstGivesItsExpectedPlan() {
        val cases = root.listFiles { f -> f.isDirectory && f.name.matches(Regex("\\d\\d")) }?.sortedBy { it.name }.orEmpty()
        assumeTrue("no dataset pushed to $root", cases.isNotEmpty())
        val j11 = stufenplan("stufenplan_j11_words.json")
        val j12 = stufenplan("stufenplan_j12_words.json")
        val lines = mutableListOf<String>()
        for (case in cases) {
            val truth = json.decodeFromString(Truth.serializer(), File(case, "truth.json").readText())
            val plan = if (truth.expectedPlan.stufe == "J12") j12 else j11
            val images = (1..3).map { File(case, "$it.jpg") }.filter { it.exists() }.map(::decode)
            val started = System.currentTimeMillis()
            val line = try {
                val result = runBlocking { KurswahlScanner.read(images) }
                result.shots?.let { File(outDir, "${case.name}_shots.json").writeText(json.encodeToString(ListSerializer(KurswahlScanner.Shot.serializer()), it)) }
                "${case.name} (${truth.expectedPlan.stufe}, ${images.size} shots, ${System.currentTimeMillis() - started} ms): ${compare(result.kurswahl, truth, plan)}"
            } catch (e: Exception) {
                "${case.name}: failed ${e.message}"
            } finally {
                images.forEach { it.recycle() }
            }
            Log.i(TAG, line)
            lines += line
        }
        File(outDir, "results.txt").writeText(lines.joinToString("\n", postfix = "\n"))
        assertEquals(cases.map { "ok" }, lines.map { it.substringAfter("): ", "failed") })
    }

    private companion object {
        const val TAG = "KurswahlDataset"
    }
}
