package lgka.plan

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The verified photo dataset (fictional sheets with exact truth): every case read like the app does (a
 * 3-shot burst of perspective-corrected photos, each parsed, then merged) and built against its
 * Stufenplan, compared with the expected plan. Runs only when the dataset is on the machine:
 * `KURSWAHL_DATASET` (the dataset folder), `KURSWAHL_DATASET_BOXES` (recognised boxes per shot, `NN_i.json`)
 * and `KURSWAHL_J12_WORDS` (words of the J12 Stufenplan).
 */
class KurswahlDatasetBenchmark {
    @Serializable
    data class Course(val subject: String, val codes: List<String>, val hours: Int)

    @Serializable
    data class ExpectedPlan(val stufe: String, val halbjahr: String, val courses: List<Course>, val totalHours: Int)

    @Serializable
    data class Truth(val sums: List<Int>, val expectedPlan: ExpectedPlan)

    private val json = Json { ignoreUnknownKeys = true }

    private fun boxes(file: File): List<TextBox> = json.decodeFromString(ListSerializer(TextBox.serializer()), file.readText())

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
    fun dataset() {
        val dataset = System.getenv("KURSWAHL_DATASET")?.let(::File) ?: return
        val shotBoxes = System.getenv("KURSWAHL_DATASET_BOXES")?.let(::File)
        val j12 = System.getenv("KURSWAHL_J12_WORDS")?.let { StufenplanParser.parse(boxes(File(it))) }
        val j11 = CustomPlanTest.stufenplan()
        var burstsRight = 0
        var photosRight = 0
        val cases = dataset.listFiles { f -> f.isDirectory && f.name.matches(Regex("\\d\\d")) }!!.sortedBy { it.name }
        for (case in cases) {
            val truth = json.decodeFromString(Truth.serializer(), File(case, "truth.json").readText())
            val plan = if (truth.expectedPlan.stufe == "J12") j12 ?: continue else j11
            if (shotBoxes != null) {
                val meta = json.parseToJsonElement(File(case, "shots/shots.json").readText()) as JsonObject
                val sheets = meta.getValue("shots").jsonArray.mapIndexedNotNull { i, shot ->
                    val size = (shot as JsonObject).getValue("size").jsonArray.map { it.jsonPrimitive.int }
                    val file = File(shotBoxes, "${case.name}_${i + 1}.json").takeIf { it.exists() } ?: return@mapIndexedNotNull null
                    val aspect = size[1].toDouble() / size[0]
                    try {
                        val base = boxes(file)
                        val sheet = KurswahlParser.parse(base, aspect)
                        // the app's enlarged rereads of what the first reading missed, when recognised for this shot
                        val extra = File(shotBoxes, "${case.name}_${i + 1}_extra.json").takeIf { it.exists() }?.let(::boxes)
                        if (extra == null) sheet else try {
                            KurswahlParser.fillGaps(sheet, KurswahlParser.parse(base + extra, aspect))
                        } catch (_: KurswahlParser.Failure) {
                            sheet
                        }
                    } catch (_: KurswahlParser.Failure) {
                        null
                    }
                }
                val result = if (sheets.isEmpty()) "no reading" else compare(KurswahlParser.merge(sheets), truth, plan)
                if (result == "ok") burstsRight++
                println("DATASET burst ${case.name} (${truth.expectedPlan.stufe}, ${sheets.size} shots): $result")
            }
            val photo = File(case, "vision_boxes.json")
            if (photo.exists()) {
                val result = try {
                    compare(KurswahlParser.parse(boxes(photo), 5376.0 / 4032.0), truth, plan)
                } catch (e: KurswahlParser.Failure) {
                    "failed ${e.reason}"
                }
                if (result == "ok") photosRight++
                println("DATASET photo ${case.name}: $result")
            }
        }
        println("DATASET bursts right: $burstsRight/${cases.size}, single photos right: $photosRight/${cases.size}")
    }
}
