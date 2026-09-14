package lgka.plan

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules added so a photo reads reliably even when recognition misses or garbles values; each one
 * only fills what was not read. [KurswahlBenchmark] measures them together on degraded fixtures.
 */
class KurswahlRobustnessTest {
    private fun cell(text: String) = KurswahlParser.parseCell(text)
    private val missing = Kurswahl.Cell.MISSING
    private fun row(subject: String, vararg halves: Kurswahl.Cell, fachart: String? = null, perCourse: String? = null) =
        Kurswahl.Row(subject, fachart, halves.toList(), perCourse = perCourse)

    // ── recognition slips ───────────────────────────────────────────────────────

    @Test
    fun lookAlikeDigitsInBracketedValues() {
        assertTrue(cell("S(3)").let { it.hours == 5 && it.parallel == 3 })
        assertTrue(cell("5(l)").let { it.hours == 5 && it.parallel == 1 })
        assertTrue(cell("Z(1).p").let { it.hours == 2 && it.parallel == 1 && it.suffix == "p" })
        assertTrue(cell("3(Z").let { it.hours == 3 && it.parallel == 2 })
        // hours a course cannot have, and a lone letter, stay what they were
        assertNull(cell("B(3)").hours)
        assertFalse(cell("S").taken)
        assertFalse(cell("S").unreadable)
    }

    @Test
    fun digitAndBracketRecognisedAsTwoBoxes() {
        val boxes = CustomPlanTest.boxes("kurswahl_ocr").flatMap { b ->
            val m = Regex("(\\d)(\\(\\d\\).*)").matchEntire(b.text) ?: return@flatMap listOf(b)
            val w = b.width / b.text.length
            listOf(b.copy(text = m.groupValues[1], width = w), b.copy(text = m.groupValues[2], x = b.x + w * 1.4, width = b.width - w))
        }
        val kurswahl = KurswahlParser.parse(boxes)
        val d = kurswahl.rows.first { it.subject == "D" }.halves[0]
        assertTrue(d.hours == 5 && d.parallel == 3)
        assertEquals(1, kurswahl.rows.first { it.subject == "Sport" }.halves[0].parallel)
    }

    // ── column sums ─────────────────────────────────────────────────────────────

    @Test
    fun singleMissingCellFromTheColumnSum() {
        val rows = listOf(row("D", cell("3"), cell("3"), cell("3"), cell("3")), row("E", missing, cell("5"), cell("5"), cell("5"), perCourse = "5"))
        val filled = KurswahlParser.completeFromSums(rows, listOf(8, 8, 8, 8))
        assertTrue(filled[1].halves[0].let { it.hours == 5 && it.inferred == true })
        // the remainder must be exactly the row's hours
        assertFalse(KurswahlParser.completeFromSums(rows, listOf(9, 8, 8, 8))[1].halves[0].taken)
    }

    @Test
    fun twoHalbjahrCourseIsNotFilledFromTheSum() {
        // "2.p" / "2.s" read without the suffix: taken in 2. and 3. Hj only
        val rows = listOf(row("D", cell("3"), cell("3"), cell("3"), cell("3")), row("Geo", missing, cell("2"), cell("2"), missing))
        assertFalse(KurswahlParser.completeFromSums(rows, listOf(5, 5, 5, 3))[1].halves[0].taken)
        // nor a row with a "-" read in another Halbjahr
        val dashed = listOf(row("D", cell("3"), cell("3"), cell("3"), cell("3")), row("Psy", missing, cell("2"), cell("2"), cell("-")))
        assertFalse(KurswahlParser.completeFromSums(dashed, listOf(5, 5, 5, 3))[1].halves[0].taken)
    }

    @Test
    fun sportWithNothingReadTakesTheColumnRemainder() {
        val rows = listOf(
            row("D", cell("3"), cell("3"), cell("3"), cell("3")),
            row("E", missing, cell("5"), cell("5"), cell("5")),
            row("Sport", missing, missing, missing, missing),
        )
        val filled = KurswahlParser.completeFromSums(rows, listOf(10, 10, 10, 10))
        assertEquals(listOf(2, 2, 2, 2), filled[2].halves.map { it.hours })
        assertTrue(filled[2].halves.all { it.inferred == true })
        // Sport was a gap in the first column too, so E only gets its 5 once Sport is known
        assertTrue(filled[1].halves[0].let { it.hours == 5 && it.inferred == true })
    }

    @Test
    fun requiredSubjectIsAGapSoItsHoursAreNotGivenToAnotherRow() {
        val rows = listOf(row("Sport", missing, missing, missing, missing), row("Inf", missing, cell("2"), cell("2"), cell("2")))
        val filled = KurswahlParser.completeFromSums(rows, listOf(2, null, null, null))
        assertFalse(filled[1].halves[0].taken)
    }

    @Test
    fun requiredSubjectFromFachartWhenNoSumHelps() {
        val basis = KurswahlParser.completeFromSums(listOf(row("Sport", missing, missing, missing, missing, fachart = "B")), listOf(null, null, null, null))
        assertEquals(listOf(2, 2, 2, 2), basis[0].halves.map { it.hours })
        val lf = KurswahlParser.completeFromSums(listOf(row("Sport", missing, missing, missing, missing, fachart = "L")), listOf(null, null, null, null))
        assertEquals(listOf(5, 5, 5, 5), lf[0].halves.map { it.hours })
        // a single remainder vote needs the Fachart to agree
        val rows = listOf(row("D", cell("3"), missing, missing, missing), row("Sport", missing, missing, missing, missing))
        assertFalse(KurswahlParser.completeFromSums(rows, listOf(8, null, null, null))[1].halves[0].taken)
    }

    @Test
    fun impossibleProKursForARequiredSubjectIsIgnored() {
        val halves = listOf(missing, cell("-"), missing, missing)
        val inferred = KurswahlParser.inferMissing(halves, "3", allHalves = true, possibleHours = KurswahlParser.possibleHours("Sport"))
        assertTrue(inferred.none { it.taken })
        // the dash is the neighbouring row's: no longer a reading
        assertTrue(inferred[1].unreadable)
        assertEquals(setOf(2, 5), KurswahlParser.possibleHours("Sport"))
        assertEquals(setOf(3, 5), KurswahlParser.possibleHours("M"))
        assertNull(KurswahlParser.possibleHours("Bio"))
        // unchanged without the subject's hours
        assertEquals(3, KurswahlParser.inferMissing(halves, "3", allHalves = true)[0].hours)
    }

    // ── several photos ──────────────────────────────────────────────────────────

    @Test
    fun mergeIgnoresDashesOfRequiredSubjectsAndImpossibleInferredHours() {
        fun inferred(hours: Int) = Kurswahl.Cell(hours = hours, unreadable = false, inferred = true)
        val d = row("D", cell("3(3)"), cell("3"), cell("3"), cell("3"))
        val first = Kurswahl(rows = listOf(d, row("Sport", cell("-"), inferred(3), inferred(3), inferred(3), perCourse = "3")), sums = listOf(null, null, null, null))
        val second = Kurswahl(rows = listOf(d, row("Sport", inferred(2), inferred(2), inferred(2), inferred(2), perCourse = "2")), sums = listOf(null, null, null, null))
        val third = Kurswahl(rows = listOf(d, row("Sport", inferred(3), inferred(3), inferred(3), inferred(3), perCourse = "3")), sums = listOf(null, null, null, null))
        val sport = KurswahlParser.merge(listOf(first, second, third)).rows.first { it.subject == "Sport" }
        assertEquals(listOf(2, 2, 2, 2), sport.halves.map { it.hours })
    }

    @Test
    fun unrecognisedRowWithAnotherPhotosSubjectValuesIsDropped() {
        val sums = listOf(null, null, null, null)
        val base = Kurswahl(rows = listOf(
            row("D", cell("3(3)"), cell("3"), cell("3"), cell("3")), row("E", cell("5(4)"), cell("5"), cell("5"), cell("5")),
            row("Rel", missing, missing, missing, missing), row("?", cell("2(1)"), cell("2"), cell("2"), cell("2")),
        ), sums = sums)
        val other = Kurswahl(rows = listOf(row("Rel", cell("2(1)"), cell("2"), missing, cell("2"))), sums = sums)
        val merged = KurswahlParser.merge(listOf(base, other))
        assertTrue(merged.rows.none { it.subject == "?" })
        assertEquals(1, merged.rows.first { it.subject == "Rel" }.halves[0].parallel)
        // a "?" row with a different course number stays
        val different = Kurswahl(rows = listOf(row("Rel", cell("2(2)"), cell("2"), missing, cell("2"))), sums = sums)
        assertTrue(KurswahlParser.merge(listOf(base, different)).rows.any { it.subject == "?" })
    }

    @Test
    fun enlargedRereadFillsOnlyWhatWasNotRead() {
        val sums = listOf(7, null, null, null)
        val base = Kurswahl(rows = listOf(row("D", cell("5(3)"), cell("5"), cell("5"), cell("5")), row("Sport", missing, missing, missing, missing)), sums = sums)
        val extra = Kurswahl(rows = listOf(row("D", cell("3(3)"), cell("5"), cell("5"), cell("5")), row("Sport", cell("2"), missing, missing, missing)),
            sums = listOf(7, 9, 9, 9))
        val filled = KurswahlParser.fillGaps(base, extra)
        assertEquals(5, filled.rows[0].halves[0].hours)
        val sport = filled.rows[1].halves
        assertTrue(sport[0].hours == 2 && sport[0].inferred != true)
        assertEquals(listOf(2, 2, 2), sport.drop(1).map { it.hours })
        assertEquals(listOf(7, 9, 9, 9), filled.sums)
    }

    @Test
    fun rereadRegionsCoverTheSportRowAndTheFirstHalbjahr() {
        val detail = KurswahlParser.parseDetailed(CustomPlanTest.boxes("kurswahl_ocr_c"), aspect = 1.5092336103416435)
        val regions = KurswahlParser.recheckRegions(detail)
        assertTrue(regions.size in 1..6)
        val sport = detail.subjectBoxes.first { it.text == "Sport" }
        val hj1 = assertNotNull(detail.sumBoxes.firstOrNull())
        assertTrue(regions.any { it.top < sport.midY && it.bottom > sport.midY && it.left < sport.midX && it.right > hj1.midX })
        assertTrue(regions.all { it.left >= 0 && it.top >= 0 && it.right <= 1 && it.bottom <= 1 })
    }

    /**
     * A real 3-photo burst (name replaced, SchID and birth date removed): on the first photo the sums line
     * search took 36 36 34 46 (the fourth sum and the "Anrechnung" numbers), every Halbjahr column moved one
     * to the right, Geo's "2.p" of the 2. Hj became a 1. Hj value and the merged plan had 39 hours.
     */
    @Test
    fun sumsRowWithAnrechnungNumbersDoesNotShiftTheHalbjahre() {
        val text = checkNotNull(javaClass.getResourceAsStream("/plan/kurswahl_ocr_burst.json")).bufferedReader().use { it.readText() }
        val burst = json.decodeFromString(Burst.serializer(), text)
        val sheets = burst.shots.map { KurswahlParser.parse(it.boxes, it.aspect) }
        assertEquals(listOf(34, 36, 36, 34), sheets[0].sums)
        assertTrue(sheets.none { sheet -> sheet.rows.first { it.subject == "Geo" }.halves[0].taken })
        val plan = CustomPlanBuilder.build(KurswahlParser.merge(sheets), CustomPlanTest.stufenplan(), "1. Halbjahr")
        assertEquals(34, plan.checks.totalHours)
        assertTrue(plan.choices.none { it.subject == "Geo" })
        assertEquals(listOf(), plan.checks.issues.filter { it.kind != CustomPlan.Issue.Kind.INFERRED }.map { it.message })
    }

    private fun burst(name: String): Burst {
        val text = checkNotNull(javaClass.getResourceAsStream("/plan/$name.json")) { "missing fixture $name" }.bufferedReader().use { it.readText() }
        return json.decodeFromString(Burst.serializer(), text)
    }

    /** A second real burst of the same sheet (anonymised): the iPhone read Geo's "2.p" into the 1. Hj, 42 hours. */
    @Test
    fun secondBurstKeepsGeoOutOfTheFirstHalbjahr() {
        val sheets = burst("kurswahl_ocr_burst_b").shots.map { KurswahlParser.parse(it.boxes, it.aspect) }
        assertTrue(sheets.all { it.sums == listOf(34, 36, 36, 34) })
        assertTrue(sheets.none { sheet -> sheet.rows.first { it.subject == "Geo" }.halves[0].taken })
        val plan = CustomPlanBuilder.build(KurswahlParser.merge(sheets), CustomPlanTest.stufenplan(), "1. Halbjahr")
        assertEquals(34, plan.checks.totalHours)
        assertTrue(plan.choices.none { it.subject == "Geo" })
        assertEquals(listOf(), plan.checks.issues.filter { it.kind != CustomPlan.Issue.Kind.INFERRED }.map { it.message })
    }

    /** A third real burst of the same sheet (anonymised) that the phone already read right, Sport included: it stays right. */
    @Test
    fun thirdBurstStaysRight() {
        val sheets = burst("kurswahl_ocr_burst_c").shots.map { KurswahlParser.parse(it.boxes, it.aspect) }
        for (sheet in sheets) {
            val plan = CustomPlanBuilder.build(sheet, CustomPlanTest.stufenplan(), "1. Halbjahr")
            assertEquals(34, plan.checks.totalHours)
            assertTrue(plan.choices.none { it.subject == "Geo" })
        }
        val plan = CustomPlanBuilder.build(KurswahlParser.merge(sheets), CustomPlanTest.stufenplan(), "1. Halbjahr")
        assertEquals(34, plan.checks.totalHours)
        assertEquals(2, plan.choices.first { it.subject == "Sport" }.hours)
        assertTrue(plan.choices.none { it.subject == "Geo" })
        assertEquals(listOf(), plan.checks.issues.map { it.message })
    }

    /** Too few brackets read to place the columns by them: Geo's plain "2.p" in the column taken for the 1. Hj does. */
    @Test
    fun plainSuffixValueInTheFirstColumnShiftsTheColumns() {
        val shot = burst("kurswahl_ocr_burst_b").shots[0]
        val firstSum = shot.boxes.filter { it.text == "34" }.minOf { it.midX }
        var brackets = 0
        val boxes = shot.boxes.filter { b ->
            val firstSumBox = b.text == "34" && abs(b.midX - firstSum) < 0.01
            val bracket = KurswahlParser.parseCell(b.text).parallel != null
            !firstSumBox && (!bracket || brackets++ < 2)
        }
        val kurswahl = KurswahlParser.parse(boxes, shot.aspect)
        assertEquals(listOf(36, 36, 34), kurswahl.sums.drop(1))
        assertFalse(kurswahl.rows.first { it.subject == "Geo" }.halves[0].taken)
        assertEquals(2, kurswahl.rows.first { it.subject == "Geo" }.halves[1].hours)
    }

    @Test
    fun rowPitchFromLabelsTwoRowsApart() {
        // D, F, Mu and M not recognised: many neighbouring labels are two rows apart
        val sparse = listOf(0.030, 0.030, 0.031, 0.015, 0.015, 0.031, 0.016, 0.031, 0.014, 0.017, 0.046, 0.028, 0.015, 0.030)
        assertEquals(0.015, KurswahlParser.finerPitch(sparse, 0.028), 0.001)
        // every label read, and a jittered second pass: the pitch stays
        val clean = List(12) { 0.018 } + 0.036
        assertEquals(0.018, KurswahlParser.finerPitch(clean, 0.018))
        val jittered = List(12) { 0.018 } + listOf(0.009, 0.010)
        assertEquals(0.018, KurswahlParser.finerPitch(jittered, 0.018))
        // labels about 0.0104 high: the real half pitch is still taken
        assertEquals(0.015, KurswahlParser.finerPitch(sparse, 0.028, minimum = 0.0104 * 1.1), 0.001)
        // two passes' labels a little apart under jitter look like half rows, but lower than a label
        val split = listOf(0.0287, 0.0127, 0.0135, 0.0121, 0.0172, 0.0308, 0.0153, 0.0106, 0.0127, 0.0153, 0.0062, 0.0070,
            0.0480, 0.0316, 0.0120, 0.0165, 0.0062, 0.0127)
        assertEquals(0.0127, KurswahlParser.finerPitch(split, 0.0127, minimum = 0.0102 * 1.1))
    }

    @Test
    fun sumNotFoundAfterMovingTheColumnsKeepsTheLineReading() {
        val shot = burst("kurswahl_ocr_burst").shots[0]
        val firstSum = shot.boxes.filter { it.text == "34" }.minOf { it.midX }
        val boxes = shot.boxes.filterNot { it.text == "34" && abs(it.midX - firstSum) < 0.01 }
        val kurswahl = KurswahlParser.parse(boxes, shot.aspect)
        assertNotNull(kurswahl.sums[0])
        assertEquals(listOf(36, 36, 34), kurswahl.sums.drop(1))
        assertFalse(kurswahl.rows.first { it.subject == "Geo" }.halves[0].taken)
    }

    @Test
    fun valueOnlyOnePhotoReadIsDroppedWhenItIsTheExcessOverTheSum() {
        val sums = listOf(5, null, null, null)
        fun sheet(geo: Kurswahl.Cell) = Kurswahl(rows = listOf(
            row("D", cell("3(3)"), cell("3"), cell("3"), cell("3")), row("Geo", geo, cell("2.p"), cell("2.s"), cell("-")),
            row("Bio", cell("2(1)"), missing, missing, missing),
        ), sums = sums)
        val merged = KurswahlParser.merge(listOf(sheet(cell("2")), sheet(missing), sheet(missing)))
        assertFalse(merged.rows.first { it.subject == "Geo" }.halves[0].taken)
        // without an excess the lone value stays
        val fits = KurswahlParser.merge(listOf(sheet(cell("2")), sheet(missing)).map { it.copy(sums = listOf(7, null, null, null)) })
        assertEquals(2, fits.rows.first { it.subject == "Geo" }.halves[0].hours)
    }

    @kotlinx.serialization.Serializable
    data class Shot(val boxes: List<TextBox>, val aspect: Double)

    @kotlinx.serialization.Serializable
    data class Burst(val shots: List<Shot>)

    private companion object {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    }

    // ── plan ────────────────────────────────────────────────────────────────────

    @Test
    fun unrecognisedRowOnlyBecomesASubjectOfItsBlock() {
        val k = CustomPlanTest.kurswahl()
        val g = k.rows.first { it.subject == "G" }
        val unknown = g.copy(subject = "?", fachart = null)
        val inPlace = k.rows.map { if (it.subject == "G") unknown else it }
        val placed = CustomPlanBuilder.build(k.copy(rows = inPlace), CustomPlanTest.stufenplan(), "1. Halbjahr")
        assertTrue(placed.courses.any { it.id == "g2" })
        // the same values in the last block (after Psy) are not Geschichte
        val moved = k.rows.filter { it.subject != "G" }.toMutableList().apply { add(indexOfFirst { it.subject == "Psy" } + 1, unknown) }
        val misplaced = CustomPlanBuilder.build(k.copy(rows = moved), CustomPlanTest.stufenplan(), "1. Halbjahr")
        assertTrue(misplaced.courses.none { it.subjectKey == "G" })
        assertTrue(misplaced.checks.issues.any { it.kind == CustomPlan.Issue.Kind.UNKNOWN_ROW })
    }
}
