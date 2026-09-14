package lgka.plan

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Robustness benchmark for reading a Kurswahlprotokoll: the recorded recognition fixtures, each
 * degraded in the ways a phone photo goes wrong (boxes missed, the Sport row or "pro Kurs" not read,
 * no sums, jitter, tilt, keystone, merged or split lines, look-alike characters), scored against the
 * known 1. Halbjahr of the sheet. Every variant must score at least as well as the recorded
 * baseline, so a change to the parser can only make detection better.
 *
 * Regenerate the baseline (only when a change is proven better): `KURSWAHL_BENCHMARK_UPDATE=1 ./gradlew :core:test`.
 */
class KurswahlBenchmark {
    data class Truth(val fixture: String, val aspect: Double, val choices: Map<String, Pair<Int, Int?>>, val sum: Int)

    data class Score(
        val failed: Boolean,
        /** Expected 1. Hj choices read with the right hours and course number. */
        val right: Int,
        /** Expected 1. Hj choices read with the right hours (course number right, or not known). */
        val hoursRight: Int,
        /** Choices that are not on the sheet or have the wrong hours. */
        val wrongHours: Int,
        /** Choices with the right hours but a wrong course number (a missing number is not counted: it is reported as ambiguous). */
        val wrongCourse: Int,
        val sport: Boolean,
        val sum: Boolean,
        val total: Boolean,
        val issues: Int,
    ) {
        fun tsv() = listOf(if (failed) 1 else 0, right, hoursRight, wrongHours, wrongCourse, if (sport) 1 else 0, if (sum) 1 else 0,
            if (total) 1 else 0, issues).joinToString("\t")

        /** Not worse than [base] in anything that is about reading the sheet correctly. */
        fun atLeast(base: Score) = (!failed || base.failed) && right >= base.right && hoursRight >= base.hoursRight &&
            wrongHours <= base.wrongHours && wrongCourse <= base.wrongCourse && (sport || !base.sport) && (sum || !base.sum) && (total || !base.total)

        companion object {
            const val HEADER = "failed\tright\thoursRight\twrongHours\twrongCourse\tsport\tsum\ttotal\tissues"

            fun of(fields: List<String>) = fields.map { it.toInt() }.let {
                Score(it[0] == 1, it[1], it[2], it[3], it[4], it[5] == 1, it[6] == 1, it[7] == 1, it[8])
            }
        }
    }

    companion object {
        private val sheetA = Truth("kurswahl_ocr", 4.0 / 3.0, mapOf(
            "D" to (5 to 3), "E" to (5 to 3), "Sp" to (3 to 1), "BK" to (2 to 2), "G" to (2 to 2), "Gk" to (2 to 3),
            "Rel" to (2 to 1), "M" to (3 to 1), "Ch" to (3 to 1), "Sport" to (5 to 1), "Psy" to (2 to 1)), 34)
        private val sheetB = mapOf(
            "D" to (3 to 3), "E" to (5 to 4), "Mu" to (2 to 2), "G" to (2 to 4), "Gk" to (5 to 1), "Rel" to (2 to 1),
            "M" to (5 to 3), "Bio" to (3 to 2), "Ph" to (3 to 1), "Sport" to (2 to null), "Inf" to (2 to 1))
        val truths = listOf(sheetA, Truth("kurswahl_ocr_b", 4.0 / 3.0, sheetB, 34), Truth("kurswahl_ocr_c", 1.5092336103416435, sheetB, 34))

        private val stufenplan by lazy { CustomPlanTest.stufenplan() }

        fun score(truth: Truth, read: () -> Kurswahl): Score {
            val kurswahl = try {
                read()
            } catch (e: KurswahlParser.Failure) {
                return Score(true, 0, 0, 0, 0, false, false, false, 0)
            }
            val plan = CustomPlanBuilder.build(kurswahl, stufenplan, "1. Halbjahr")
            var right = 0
            var hoursRight = 0
            var wrongHours = 0
            var wrongCourse = 0
            for (choice in plan.choices) {
                val expected = truth.choices[choice.subject]
                when {
                    expected == null || expected.first != choice.hours -> wrongHours++
                    expected.second == choice.parallel -> { right++; hoursRight++ }
                    choice.parallel == null -> hoursRight++
                    else -> wrongCourse++
                }
            }
            val sport = plan.choices.firstOrNull { it.subject == "Sport" }?.hours == truth.choices["Sport"]?.first
            val sum = kurswahl.sums.firstOrNull() == truth.sum
            return Score(false, right, hoursRight, wrongHours, wrongCourse, sport, sum, plan.checks.totalHours == truth.sum, plan.checks.issues.size)
        }

        // ── degradations ─────────────────────────────────────────────────────

        /** Where things are on the clean sheet: Halbjahr columns, row pitch, tilt, the Sport row. */
        class Layout(boxes: List<TextBox>, aspect: Double) {
            val detail = KurswahlParser.parseDetailed(boxes, aspect)
            val columns = detail.sumBoxes.map { it.midX }
            val spacing = columns.zipWithNext { a, b -> b - a }.average()
            val slope = detail.sumBoxes.let { (it.last().midY - it.first().midY) / (it.last().midX - it.first().midX) }
            val pitch = detail.subjectBoxes.map { it.midY }.zipWithNext { a, b -> b - a }.sorted().let { it[it.size / 2] }
            val sport = detail.subjectBoxes.first { it.text == "Sport" }

            fun onSportRow(b: TextBox) = abs(b.midY - (sport.midY + slope * (b.midX - sport.midX))) < pitch * 0.45
            fun inColumn(b: TextBox, x: Double) = abs(b.midX - x) < spacing * 0.45
            fun inHalf(b: TextBox, h: Int) = inColumn(b, columns[h])
            fun inPerCourse(b: TextBox) = inColumn(b, columns[0] - spacing)
            fun isSumNumber(b: TextBox) = detail.sumBoxes.any { abs(it.midX - b.midX) < 0.01 && abs(it.midY - b.midY) < 0.01 }
        }

        private fun dropRandom(boxes: List<TextBox>, p: Double, seed: Int): List<TextBox> {
            val random = Random(seed)
            return boxes.filter { random.nextDouble() >= p }
        }

        private fun jitter(boxes: List<TextBox>, sigma: Double, seed: Int): List<TextBox> {
            val random = Random(seed)
            fun g() = (random.nextDouble() - 0.5) * 2 * sigma
            return boxes.map { it.copy(x = it.x + g(), y = it.y + g()) }
        }

        /** Rotation about the image centre in pixel space ([aspect] = height ÷ width). */
        private fun rotate(boxes: List<TextBox>, degrees: Double, aspect: Double): List<TextBox> {
            val a = degrees * PI / 180
            return boxes.map { b ->
                val px = b.midX - 0.5
                val py = (b.midY - 0.5) * aspect
                val nx = px * cos(a) - py * sin(a)
                val ny = px * sin(a) + py * cos(a)
                b.copy(x = 0.5 + nx - b.width / 2, y = 0.5 + ny / aspect - b.height / 2)
            }
        }

        /** The sheet photographed from below or above: rows further away get narrower. */
        private fun keystone(boxes: List<TextBox>, k: Double): List<TextBox> = boxes.map { b ->
            val f = 1 + k * (b.midY - 0.5)
            b.copy(x = 0.5 + (b.midX - 0.5) * f - b.width * f / 2, width = b.width * f)
        }

        /** Neighbouring boxes on one line recognised as a single line ("5(3) 5 5 5"). */
        private fun mergeLines(boxes: List<TextBox>, p: Double, seed: Int): List<TextBox> {
            val random = Random(seed)
            val result = mutableListOf<TextBox>()
            for (b in boxes.sortedWith(compareBy({ (it.midY * 200).toInt() }, { it.x }))) {
                val last = result.lastOrNull()
                if (last != null && abs(last.midY - b.midY) < b.height * 0.5 && b.x - last.maxX in -0.005..0.04 && random.nextDouble() < p) {
                    val y = minOf(last.y, b.y)
                    result[result.size - 1] = last.copy(text = "${last.text} ${b.text}", width = b.maxX - last.x, y = y,
                        height = maxOf(last.maxY, b.maxY) - y)
                } else {
                    result += b
                }
            }
            return result
        }

        /** "5(3)" recognised as two boxes, "5" and "(3)". */
        private fun splitBrackets(boxes: List<TextBox>): List<TextBox> = boxes.flatMap { b ->
            val m = Regex("(\\d)(\\(\\d\\)?.*)").matchEntire(b.text) ?: return@flatMap listOf(b)
            val w = b.width / b.text.length
            listOf(b.copy(text = m.groupValues[1], width = w), b.copy(text = m.groupValues[2], x = b.x + w * 1.4, width = b.width - w))
        }

        private val confusions = mapOf('0' to 'O', '1' to 'l', '5' to 'S', '8' to 'B', '2' to 'Z', '3' to '3', '(' to '(', ')' to ' ')

        /** Look-alike characters in value-like boxes: O/0, l/1, S/5, B/8, Z/2, a lost closing bracket. */
        private fun confuse(boxes: List<TextBox>, p: Double, seed: Int): List<TextBox> {
            val random = Random(seed)
            return boxes.map { b ->
                if (b.text.length > 6 || b.text.none { it.isDigit() } || random.nextDouble() >= p) return@map b
                val text = b.text.map { c -> if (random.nextBoolean()) confusions[c] ?: c else c }.joinToString("").trimEnd()
                b.copy(text = text)
            }
        }

        fun variants(truth: Truth): List<Pair<String, List<TextBox>>> {
            val clean = CustomPlanTest.boxes(truth.fixture)
            val layout = Layout(clean, truth.aspect)
            val v = mutableListOf<Pair<String, List<TextBox>>>()
            v += "clean" to clean
            v += "drop-sport-hj1" to clean.filterNot { layout.onSportRow(it) && layout.inHalf(it, 0) }
            v += "drop-sport-cells" to clean.filterNot { layout.onSportRow(it) && (0..3).any { h -> layout.inHalf(it, h) } }
            v += "drop-sport-cells-perkurs" to clean.filterNot { layout.onSportRow(it) && ((0..3).any { h -> layout.inHalf(it, h) } || layout.inPerCourse(it)) }
            v += "drop-sport-row" to clean.filterNot { layout.onSportRow(it) && it.midX > layout.sport.maxX + 0.01 }
            v += "drop-sport-hj1-no-perkurs" to clean.filterNot { layout.inPerCourse(it) || (layout.onSportRow(it) && layout.inHalf(it, 0)) }
            v += "drop-perkurs" to clean.filterNot { layout.inPerCourse(it) }
            v += "drop-summen-label" to clean.filterNot { it.text.lowercase().startsWith("summ") }
            v += "drop-sum-numbers" to clean.filterNot { layout.isSumNumber(it) }
            v += "drop-sums" to clean.filterNot { it.text.lowercase().startsWith("summ") || layout.isSumNumber(it) }
            for (seed in 1..6) {
                val random = Random(seed * 31)
                val hj1 = layout.detail.cellBoxes[0].filter { random.nextDouble() < 0.25 }
                v += "drop-hj1-cells#$seed" to clean.filterNot { b -> hj1.any { abs(it.midX - b.midX) < 0.004 && abs(it.midY - b.midY) < 0.004 } }
            }
            for (p in listOf(0.1, 0.2, 0.3)) for (seed in 1..6) v += "drop-random-$p#$seed" to dropRandom(clean, p, seed)
            for (s in listOf(0.002, 0.004)) for (seed in 1..3) v += "jitter-$s#$seed" to jitter(clean, s, seed)
            for (d in listOf(-3.0, -1.5, 1.5, 3.0)) v += "rotate-$d" to rotate(clean, d, truth.aspect)
            for (k in listOf(-0.15, 0.15)) v += "keystone-$k" to keystone(clean, k)
            for (seed in 1..3) v += "merge-lines#$seed" to mergeLines(clean, 0.5, seed)
            v += "split-brackets" to splitBrackets(clean)
            for (p in listOf(0.15, 0.3)) for (seed in 1..4) v += "confuse-$p#$seed" to confuse(clean, p, seed)
            for (seed in 1..8) {
                v += "combo#$seed" to confuse(rotate(jitter(dropRandom(clean, 0.15, seed), 0.002, seed), (seed % 5 - 2) * 1.0, truth.aspect), 0.15, seed)
            }
            return v
        }

        /** Burst photos: three degraded readings merged. */
        fun bursts(truth: Truth): List<Pair<String, List<List<TextBox>>>> {
            val clean = CustomPlanTest.boxes(truth.fixture)
            val layout = Layout(clean, truth.aspect)
            val noSport = clean.filterNot { layout.onSportRow(it) && (0..3).any { h -> layout.inHalf(it, h) } }
            val result = mutableListOf<Pair<String, List<List<TextBox>>>>()
            for (seed in 1..6) result += "burst-drop-0.25#$seed" to (0..2).map { dropRandom(clean, 0.25, seed * 10 + it) }
            for (seed in 1..4) result += "burst-no-sport-cells#$seed" to (0..2).map { dropRandom(noSport, 0.15, seed * 10 + it) }
            for (seed in 1..4) result += "burst-combo#$seed" to (0..2).map {
                confuse(rotate(jitter(dropRandom(clean, 0.2, seed * 7 + it), 0.002, seed + it), (it - 1) * 1.5, truth.aspect), 0.2, seed + it)
            }
            return result
        }

        fun run(): Map<String, Score> {
            val scores = LinkedHashMap<String, Score>()
            for (truth in truths) {
                for ((name, boxes) in variants(truth)) scores["${truth.fixture}\t$name"] = score(truth) { KurswahlParser.parse(boxes, truth.aspect) }
                for ((name, shots) in bursts(truth)) scores["${truth.fixture}\t$name"] = score(truth) {
                    val sheets = shots.mapNotNull { runCatching { KurswahlParser.parse(it, truth.aspect) }.getOrNull() }
                    if (sheets.isEmpty()) throw KurswahlParser.Failure(KurswahlParser.Failure.Reason.NO_SUBJECTS)
                    KurswahlParser.merge(sheets)
                }
            }
            return scores
        }

        private const val BASELINE = "src/test/resources/plan/kurswahl_benchmark_baseline.tsv"
    }

    @Test
    fun noVariantReadsWorseThanTheBaseline() {
        val scores = run()
        val header = "fixture\tvariant\t${Score.HEADER}"
        val lines = scores.map { (k, s) -> "$k\t${s.tsv()}" }
        File("build/kurswahl_benchmark.tsv").apply { parentFile.mkdirs() }.writeText((listOf(header) + lines).joinToString("\n") + "\n")
        fun summary(values: Collection<Score>) = "variants=${values.size} failed=${values.count { it.failed }} " +
            "right=${values.sumOf { it.right }} hoursRight=${values.sumOf { it.hoursRight }} wrongHours=${values.sumOf { it.wrongHours }} " +
            "wrongCourse=${values.sumOf { it.wrongCourse }} sport=${values.count { it.sport }} " +
            "sum=${values.count { it.sum }} total=${values.count { it.total }} issues=${values.sumOf { it.issues }}"
        println("benchmark now:      ${summary(scores.values)}")
        if (System.getenv("KURSWAHL_BENCHMARK_UPDATE") == "1") {
            File(BASELINE).writeText((listOf(header) + lines).joinToString("\n") + "\n")
            return
        }
        val baseline = File(BASELINE).readLines().drop(1).filter { it.isNotBlank() }.associate { line ->
            val f = line.split("\t")
            "${f[0]}\t${f[1]}" to Score.of(f.drop(2))
        }
        println("benchmark baseline: ${summary(baseline.values)}")
        assertEquals(baseline.keys, scores.keys)
        val worse = scores.filter { (k, s) -> !s.atLeast(baseline.getValue(k)) }.map { (k, s) -> "$k: ${baseline.getValue(k).tsv()} → ${s.tsv()}" }
        assertTrue(worse.isEmpty(), "worse than baseline:\n" + worse.joinToString("\n"))
    }
}
