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
import kotlin.test.assertEquals

/**
 * The verified photo dataset (ten fictional sheets with exact truth, six J11 and four J12): every case read
 * like the app does (a burst of three perspective-corrected shots, each parsed and completed with the
 * enlarged rereads, then merged) and built against its Stufenplan, compared with the expected plan: the
 * same courses, codes and hours, the total, and no issues.
 *
 * The recorded fixtures (`plan/dataset/NN.json`: truth plus the recognised boxes and rereads of each shot)
 * run always. With the dataset on the machine, `KURSWAHL_DATASET` (the dataset folder),
 * `KURSWAHL_DATASET_BOXES` (boxes per shot, `NN_i.json` and `NN_i_extra.json`) and `KURSWAHL_J12_WORDS`
 * read it live and also report the single uncorrected photos.
 */
class KurswahlDatasetBenchmark {
    @Serializable
    data class Course(val subject: String, val codes: List<String>, val hours: Int)

    @Serializable
    data class ExpectedPlan(val stufe: String, val halbjahr: String, val courses: List<Course>, val totalHours: Int)

    @Serializable
    data class Truth(val sums: List<Int>, val expectedPlan: ExpectedPlan)

    @Serializable
    data class Shot(val aspect: Double, val boxes: List<TextBox>, val extra: List<TextBox> = emptyList())

    @Serializable
    data class Case(val truth: Truth, val shots: List<Shot>)

    private val json = Json { ignoreUnknownKeys = true }

    private fun boxes(text: String): List<TextBox> = json.decodeFromString(ListSerializer(TextBox.serializer()), text)

    private fun resource(path: String): String =
        checkNotNull(javaClass.getResourceAsStream(path)) { "missing fixture $path" }.bufferedReader().use { it.readText() }

    private val j11 by lazy { CustomPlanTest.stufenplan() }
    private val j12 by lazy { StufenplanParser.parse(boxes(resource("/plan/stufenplan_j12_words.json"))) }

    /** One shot as KurswahlScanner reads it: the first reading, completed from the rereads. */
    private fun read(shot: Shot): Kurswahl? = try {
        val sheet = KurswahlParser.parse(shot.boxes, shot.aspect)
        if (shot.extra.isEmpty()) sheet else try {
            KurswahlParser.fillGaps(sheet, KurswahlParser.parse(shot.boxes + shot.extra, shot.aspect))
        } catch (_: KurswahlParser.Failure) {
            sheet
        }
    } catch (_: KurswahlParser.Failure) {
        null
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

    private fun burst(case: Case): String {
        val plan = if (case.truth.expectedPlan.stufe == "J12") j12 else j11
        val sheets = case.shots.mapNotNull(::read)
        return if (sheets.isEmpty()) "no reading" else compare(KurswahlParser.merge(sheets), case.truth, plan)
    }

    @Test
    fun everyDatasetBurstGivesItsExpectedPlan() {
        val results = (1..10).map { n ->
            val name = "%02d".format(n)
            name to burst(json.decodeFromString(Case.serializer(), resource("/plan/dataset/$name.json")))
        }
        results.forEach { (name, result) -> println("DATASET fixture $name: $result") }
        assertEquals(results.map { it.first to "ok" }, results)
    }

    /** The same bursts as ML Kit read them on a Pixel 7 (first readings of the three shots, without rereads). */
    @Test
    fun everyMlKitBurstGivesItsExpectedPlan() {
        val results = (1..10).map { n ->
            val name = "%02d".format(n)
            name to burst(json.decodeFromString(Case.serializer(), resource("/plan/dataset_mlkit/$name.json")))
        }
        results.forEach { (name, result) -> println("DATASET mlkit $name: $result") }
        assertEquals(results.map { it.first to "ok" }, results)
    }

    @Test
    fun liveDataset() {
        val dataset = System.getenv("KURSWAHL_DATASET")?.let(::File) ?: return
        val shotBoxes = System.getenv("KURSWAHL_DATASET_BOXES")?.let(::File)
        val liveJ12 = System.getenv("KURSWAHL_J12_WORDS")?.let { StufenplanParser.parse(boxes(File(it).readText())) } ?: j12
        var burstsRight = 0
        var photosRight = 0
        val cases = dataset.listFiles { f -> f.isDirectory && f.name.matches(Regex("\\d\\d")) }!!.sortedBy { it.name }
        for (case in cases) {
            val truth = json.decodeFromString(Truth.serializer(), File(case, "truth.json").readText())
            val plan = if (truth.expectedPlan.stufe == "J12") liveJ12 else j11
            if (shotBoxes != null) {
                val meta = json.parseToJsonElement(File(case, "shots/shots.json").readText()) as JsonObject
                val shots = meta.getValue("shots").jsonArray.mapIndexedNotNull { i, shot ->
                    val size = (shot as JsonObject).getValue("size").jsonArray.map { it.jsonPrimitive.int }
                    val file = File(shotBoxes, "${case.name}_${i + 1}.json").takeIf { it.exists() } ?: return@mapIndexedNotNull null
                    val extra = File(shotBoxes, "${case.name}_${i + 1}_extra.json").takeIf { it.exists() }?.let { boxes(it.readText()) } ?: emptyList()
                    Shot(size[1].toDouble() / size[0], boxes(file.readText()), extra)
                }
                val sheets = shots.mapNotNull(::read)
                val result = if (sheets.isEmpty()) "no reading" else compare(KurswahlParser.merge(sheets), truth, plan)
                if (result == "ok") burstsRight++
                println("DATASET burst ${case.name} (${truth.expectedPlan.stufe}, ${sheets.size} shots): $result")
            }
            val photo = File(case, "vision_boxes.json")
            if (photo.exists()) {
                val result = try {
                    compare(KurswahlParser.parse(boxes(photo.readText()), 5376.0 / 4032.0), truth, plan)
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
