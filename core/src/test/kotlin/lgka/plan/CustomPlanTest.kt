package lgka.plan

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The custom J11 timetable end to end on recorded input: the words of the real J11 Stufenplan
 * (2026/27, 1. Halbjahr; teacher codes replaced) and the text recognised on photos of a
 * Kurswahlprotokoll (names replaced, SchID and birth date removed). Same fixtures and expectations
 * as the iOS app.
 */
class CustomPlanTest {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun boxes(name: String): List<TextBox> {
            val text = checkNotNull(CustomPlanTest::class.java.getResourceAsStream("/plan/$name.json")) { "missing fixture $name" }
                .bufferedReader().use { it.readText() }
            return json.decodeFromString(ListSerializer(TextBox.serializer()), text)
        }

        fun stufenplan(): Stufenplan = StufenplanParser.parse(boxes("stufenplan_j11_words"))
        fun kurswahl(): Kurswahl = KurswahlParser.parse(boxes("kurswahl_ocr"))
    }

    // ── Stufenplan ──────────────────────────────────────────────────────────────

    @Test
    fun stufenplanHeaderAndSlots() {
        val plan = stufenplan()
        assertEquals("J11", plan.stufe)
        assertEquals("2026-2027", plan.schuljahr)
        assertEquals("7.9.2026 14:39", plan.stand)
        assertEquals(129, plan.slots.size)
    }

    @Test
    fun stufenplanDoubleSingleAndRooms() {
        val plan = stufenplan()
        fun slot(code: String, day: Int, start: Int) = plan.slots.firstOrNull { it.code == code && it.day == day && it.start == start }
        assertEquals(2, slot("M3", 0, 1)?.end)
        assertEquals("301", slot("M3", 0, 1)?.room)
        assertEquals(9, slot("bio2", 1, 9)?.end)
        assertEquals("NWT1", slot("bio2", 1, 9)?.room)
        assertEquals(9, slot("bio3", 2, 8)?.end)
        assertNull(slot("Esp", 0, 5)?.room)
        assertEquals(listOf("WB2", "FrEb", "Less"), listOf("s1", "s2", "s3").map { slot(it, 0, 10)?.room })
    }

    // ── Kurswahlprotokoll ───────────────────────────────────────────────────────

    @Test
    fun cellValues() {
        assertEquals(5, KurswahlParser.parseCell("5(3)").hours)
        assertEquals(3, KurswahlParser.parseCell("5(3)").parallel)
        assertEquals(3, KurswahlParser.parseCell("2(3).Р").parallel) // Cyrillic P from recognition
        assertEquals(2, KurswahlParser.parseCell("2 (3) .p").hours)
        assertEquals(2, KurswahlParser.parseCell("2.s").hours)
        assertNull(KurswahlParser.parseCell("2.s").parallel)
        assertFalse(KurswahlParser.parseCell("-").taken)
        assertFalse(KurswahlParser.parseCell("-").unreadable)
        assertFalse(KurswahlParser.parseCell("..E").unreadable)
        assertEquals(5, KurswahlParser.parseCell("5(8").hours)
        assertTrue(KurswahlParser.parseCell("a7x").unreadable)
    }

    @Test
    fun kurswahlFromPhoto() {
        val k = kurswahl()
        assertEquals("Max Muster", k.name)
        assertEquals(2028, k.abiturjahr)
        assertEquals(Kurswahl.Konfession.KATHOLISCH, k.konfession)
        assertEquals(34, k.sums.first())
        assertEquals(11, k.grade("2026-2027"))
        assertEquals(12, k.grade("2027-2028"))
        fun hj1(subject: String) = k.rows.firstOrNull { it.subject == subject }?.halves?.first()
        assertTrue(hj1("D")?.hours == 5 && hj1("D")?.parallel == 3)
        assertTrue(hj1("Gk")?.hours == 2 && hj1("Gk")?.parallel == 3)
        assertEquals(1, hj1("Sport")?.parallel)
        assertEquals(1, hj1("Psy")?.parallel)
        assertEquals(false, hj1("Geo")?.taken)
        assertEquals(2, k.rows.first { it.subject == "Geo" }.halves[1].hours)
    }

    // ── Plan ────────────────────────────────────────────────────────────────────

    @Test
    fun planFromPhotoMatchesTheHandMadeOne() {
        val plan = CustomPlanBuilder.build(kurswahl(), stufenplan(), "1. Halbjahr")
        assertEquals(emptyList(), plan.checks.issues.map { it.message })
        assertEquals(34, plan.checks.totalHours)
        assertEquals(setOf("D3", "E3", "esp", "bk2", "g2", "gk3", "kR1", "m1", "ch1", "S1", "psy1"), plan.courses.map { it.id }.toSet())
        assertEquals("Sport (LF, S1)", plan.courses.first { it.id == "S1" }.title)
        val days = listOf("Mo", "Di", "Mi", "Do", "Fr")
        val lessons = plan.lessons.map { "${days[it.day]} ${it.start}-${it.end} ${it.course} ${it.roomLabel}" }.toSet()
        val expected = setOf(
            "Mo 3-4 bk2 BKOG", "Mo 5-6 E3 209", "Mo 8-8 S1 201", "Mo 9-9 D3 112", "Mo 10-11 S1 WB1",
            "Di 3-4 ch1 CHHS", "Di 5-6 D3 301", "Di 8-8 m1 109", "Di 11-11 esp 109",
            "Mi 1-2 kR1 310", "Mi 3-4 D3 301", "Mi 5-6 E3 409", "Mi 8-9 g2 323", "Mi 11-11 ch1 CHHS",
            "Do 1-2 S1 WB1", "Do 3-4 esp 310", "Do 8-8 E3 409", "Do 10-11 gk3 323",
            "Fr 3-4 m1 102", "Fr 5-6 psy1 403",
        )
        assertEquals(expected, lessons)
    }

    @Test
    fun basisSportIsOneCourseAcrossParallelSlots() {
        val choices = listOf(
            CustomPlan.Choice("M", CustomPlan.Level.LEISTUNGSFACH, 5, 3),
            CustomPlan.Choice("Gk", CustomPlan.Level.LEISTUNGSFACH, 5, null),
            CustomPlan.Choice("Sport", CustomPlan.Level.BASISFACH, 2, null),
        )
        val plan = CustomPlanBuilder.build("Test", choices, null, stufenplan(), "1. Halbjahr", 12)
        assertEquals(listOf("Mathematik (LF, M3)", "Gemeinschaftskunde (LF)", "Sport (s1 / s2 / s3)"), plan.courses.map { it.title })
        assertTrue(plan.checks.ok)
        assertEquals("WB2 / FrEb / Less", plan.lessons.first { it.course == "s1/s2/s3" }.roomLabel)
        assertEquals(1, plan.notes.size)
    }

    @Test
    fun overlappingCoursesAreReported() {
        val choices = listOf(
            CustomPlan.Choice("M", CustomPlan.Level.LEISTUNGSFACH, 5, 3),
            CustomPlan.Choice("E", CustomPlan.Level.LEISTUNGSFACH, 5, 1),
        )
        val plan = CustomPlanBuilder.build("", choices, null, stufenplan(), "1. Halbjahr", null)
        assertTrue(plan.checks.issues.any { it.kind == CustomPlan.Issue.Kind.CONFLICT && it.codes == listOf("E1", "M3") })
    }

    @Test
    fun missingParallelCourseAndCandidates() {
        val stufenplan = stufenplan()
        val plan = CustomPlanBuilder.build("", listOf(CustomPlan.Choice("D", CustomPlan.Level.LEISTUNGSFACH, 5, 7)), null, stufenplan, "1. Halbjahr", null)
        assertEquals(CustomPlan.Issue.Kind.NOT_IN_PLAN, plan.checks.issues.first().kind)
        assertEquals(listOf("D1", "D2", "D3"), CustomPlanBuilder.candidates("D", CustomPlan.Level.LEISTUNGSFACH, null, stufenplan))
        assertEquals(listOf("d1", "d2", "d3"), CustomPlanBuilder.candidates("D", CustomPlan.Level.BASISFACH, null, stufenplan))
        assertEquals(listOf("eR1", "eR2"), CustomPlanBuilder.candidates("Rel", CustomPlan.Level.BASISFACH, Kurswahl.Konfession.EVANGELISCH, stufenplan))
    }

    @Test
    fun missingSubjectIsRecoveredFromThePlan() {
        val boxes = boxes("kurswahl_ocr").filterNot { it.text == "D" || it.text.lowercase().startsWith("summen") }
        val kurswahl = KurswahlParser.parse(boxes)
        assertEquals(34, kurswahl.sums.first())
        val plan = CustomPlanBuilder.build(kurswahl, stufenplan(), "1. Halbjahr")
        assertEquals(emptyList(), plan.checks.issues.map { it.message })
        assertTrue(plan.courses.any { it.id == "D3" })
        assertEquals(34, plan.checks.totalHours)
    }

    @Test
    fun unreadableSumIsReported() {
        val boxes = boxes("kurswahl_ocr").filterNot { it.text.lowercase().startsWith("summen") || (it.y > 0.87 && Regex("\\d{2}").matches(it.text)) }
        val plan = CustomPlanBuilder.build(KurswahlParser.parse(boxes), stufenplan(), "1. Halbjahr")
        assertTrue(plan.checks.issues.any { it.kind == CustomPlan.Issue.Kind.SUM_UNREADABLE })
        assertEquals(34, plan.checks.totalHours)
    }

    @Test
    fun tiltedPhotoStillFindsColumnsAndSums() {
        // about 2.3° clockwise: the right edge sits 4 % of the height lower than the left
        val boxes = boxes("kurswahl_ocr").map { it.copy(y = it.y + (it.midX - 0.5) * 0.04) }
        val kurswahl = KurswahlParser.parse(boxes)
        assertEquals(34, kurswahl.sums.first())
        val plan = CustomPlanBuilder.build(kurswahl, stufenplan(), "1. Halbjahr")
        assertEquals(emptyList(), plan.checks.issues.map { it.message })
        assertEquals(34, plan.checks.totalHours)
    }

    /** A second real sheet (iPhone photo): "D", "E" and "Summen" were not recognised at all. */
    @Test
    fun sheetWithUnrecognisedSubjectsAndSumLabel() {
        val kurswahl = KurswahlParser.parse(boxes("kurswahl_ocr_b"))
        assertEquals(34, kurswahl.sums.first())
        val plan = CustomPlanBuilder.build(kurswahl, stufenplan(), "1. Halbjahr")
        assertEquals(emptyList(), plan.checks.issues.map { it.message })
        assertEquals(setOf("d3", "E4", "GK", "mus2", "g4", "kR1", "M3", "bio2", "ph", "inf1", "s1/s2/s3"), plan.courses.map { it.id }.toSet())
        assertEquals(34, plan.checks.totalHours)
    }

    /** The same sheet scanned in the app: Sport's 1. Hj value was never recognised; "pro Kurs" fills it. */
    @Test
    fun phoneScanFillsRequiredSubjectFromPerCourse() {
        val kurswahl = KurswahlParser.parse(boxes("kurswahl_ocr_c"), aspect = 1.5092336103416435)
        val sport = kurswahl.rows.firstOrNull { it.subject == "Sport" }?.halves?.first()
        assertTrue(sport?.hours == 2 && sport.inferred == true)
        val plan = CustomPlanBuilder.build(kurswahl, stufenplan(), "1. Halbjahr")
        assertEquals(listOf(CustomPlan.Issue.Kind.INFERRED), plan.checks.issues.map { it.kind })
        assertEquals(34, plan.checks.totalHours)
    }

    @Test
    fun overviewAndCloseUpsMerge() {
        val boxes = boxes("kurswahl_ocr")
        val top = boxes.filter { it.y < 0.66 && it.text != "Ph" }
        val bottom = boxes.filter { it.y > 0.55 || it.text.contains("Abiturjahr") }
        val overview = boxes.filter { it.text != "D" && it.text != "5(1)" }
        val sheets = listOf(overview, top, bottom).mapNotNull { runCatching { KurswahlParser.parse(it) }.getOrNull() }
        assertEquals(3, sheets.size)
        val merged = KurswahlParser.merge(sheets)
        assertEquals(3, merged.rows.first { it.subject == "D" }.halves.first().parallel)
        assertEquals(1, merged.rows.first { it.subject == "Sport" }.halves.first().parallel)
        assertEquals(34, merged.sums.first())
        val plan = CustomPlanBuilder.build(merged, stufenplan(), "1. Halbjahr")
        assertEquals(emptyList(), plan.checks.issues.map { it.message })
        assertEquals(34, plan.checks.totalHours)
    }

    @Test
    fun missingHalfIsTakenFromTheOtherThree() {
        val cells = listOf("-", "2", "2", "2").map { KurswahlParser.parseCell(it) }
        val halves = cells.toMutableList().also { it[0] = Kurswahl.Cell.MISSING }
        val inferred = KurswahlParser.inferMissing(halves, "2")
        assertTrue(inferred[0].hours == 2 && inferred[0].inferred == true)
        val geo = KurswahlParser.inferMissing(listOf(Kurswahl.Cell.MISSING, KurswahlParser.parseCell("2.p"), KurswahlParser.parseCell("2.s"), Kurswahl.Cell.MISSING), "2")
        assertFalse(geo[0].taken)
        assertFalse(KurswahlParser.inferMissing(cells, "2")[0].taken)
    }

    /** The second Halbjahr column reads "5", not "5(3)": the course number carries over from the first. */
    @Test
    fun secondHalbjahrKeepsTheParallelCourse() {
        val (choices, _) = CustomPlanBuilder.choices(kurswahl(), 1)
        assertEquals(3, choices.first { it.subject == "D" }.parallel)
        assertEquals(3, choices.first { it.subject == "E" }.parallel)
        assertNull(choices.first { it.subject == "Geo" }.parallel)
    }

    @Test
    fun handPickedCodeWins() {
        val plan = CustomPlanBuilder.build("", listOf(CustomPlan.Choice("M", CustomPlan.Level.LEISTUNGSFACH, 5, 3, code = "M1")),
            null, stufenplan(), "1. Halbjahr", null)
        assertEquals(listOf("M1"), plan.courses.map { it.id })
    }

    @Test
    fun jsonRoundTrip() {
        val plan = CustomPlanBuilder.build(kurswahl(), stufenplan(), "1. Halbjahr", now = Instant.ofEpochSecond(1_788_000_000))
        val encoded = Json.encodeToString(CustomPlan.serializer(), plan)
        assertEquals(plan, Json.decodeFromString(CustomPlan.serializer(), encoded))
        assertTrue(encoded.contains("\"level\":\"LF\""))
    }
}
