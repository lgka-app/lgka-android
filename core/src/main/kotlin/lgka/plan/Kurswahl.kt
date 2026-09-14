package lgka.plan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the custom plan needs from a winprosa "Kurswahlprotokoll": the subjects, and per Halbjahr
 * the weekly hours with the parallel course. Personal data on the sheet (SchNr, SchID, birth date)
 * is never read.
 */
@Serializable
data class Kurswahl(
    /** "Manuel Göring" (the sheet prints "Göring, Manuel"). */
    val name: String? = null,
    /** "Abiturjahr: 2028-HJ1" → 2028. */
    val abiturjahr: Int? = null,
    val konfession: Konfession? = null,
    val rows: List<Row>,
    /** The "Summen" row, one value per Halbjahr (null when unreadable). */
    val sums: List<Int?>,
) {
    @Serializable
    enum class Konfession { @SerialName("katholisch") KATHOLISCH, @SerialName("evangelisch") EVANGELISCH }

    @Serializable
    data class Row(
        /** Subject key as printed in the "Fächer" column: "D", "Gk", "Sport"; "?" for a row whose subject could not be read. */
        val subject: String,
        /** "L", "B", "m", "L/B" (before the choice is made); null when unreadable. */
        val fachart: String? = null,
        /** One entry per Halbjahr (1. Hj … 4. Hj). */
        val halves: List<Cell>,
        /** What was read in the subject column of a "?" row, if anything. */
        val label: String? = null,
        /** The "pro Kurs" column: "5", "2", "5/3" (not chosen yet). */
        val perCourse: String? = null,
    )

    @Serializable
    data class Cell(
        /** The recognised text, "5(3)", "2(3).p", "-". */
        val raw: String? = null,
        /** Weekly hours; null for "-" (not taken) or unreadable. */
        val hours: Int? = null,
        /** Parallel course number in brackets: "5(3)" → 3. */
        val parallel: Int? = null,
        /** The text was found but does not look like a cell value. */
        val unreadable: Boolean,
        /** "p" / "s" of a subject taken in two Halbjahre only ("2.p"). */
        val suffix: String? = null,
        /** Not read but taken over from the rest of the sheet. */
        val inferred: Boolean? = null,
    ) {
        val taken: Boolean get() = hours != null

        companion object {
            val MISSING = Cell(unreadable = true)
        }
    }

    /** The Jahrgang the sheet belongs to in a school year: Abitur 2028 in 2026-2027 → 11. */
    fun grade(inSchuljahr: String): Int? {
        val abitur = abiturjahr ?: return null
        val start = inSchuljahr.take(4).toIntOrNull() ?: return null
        return (13 - (abitur - start)).takeIf { it in 11..12 }
    }

    companion object {
        /** Which of the four Halbjahr columns belongs to a plan: J11 → 0/1, J12 → 2/3. */
        fun halfIndex(grade: Int, halbjahr: String): Int =
            (if (grade >= 12) 2 else 0) + (if (halbjahr.startsWith("2")) 1 else 0)
    }
}

/**
 * Reads a Kurswahlprotokoll from text recognised on a photo (boxes 0…1, origin top-left). Takes
 * the boxes of several recognition passes at once; for every cell the most plausible reading wins.
 *
 * Anchors: the subject abbreviations form the left column and give the rows, with the regular row
 * pitch filling in rows whose subject was not recognised. The "Summen" row gives the x of the four
 * Halbjahr columns. Rows are then followed column by column (Fachart, pro Kurs, 1.–4. Hj): every
 * value belongs to the one row it is nearest to, and each row's height is carried over from the
 * value found in the previous column, so a curved or skewed sheet stays on its rows.
 */
object KurswahlParser {
    class Failure(val reason: Reason) : Exception(reason.name) {
        enum class Reason {
            /** No subject column was found: not a Kurswahlprotokoll, or the photo is unreadable. */
            NO_SUBJECTS,
            /** The Halbjahr columns could not be located. */
            NO_COLUMNS,
        }
    }

    /** Column spacing ÷ row pitch of the printed form, in pixels. */
    private const val COLUMN_TO_ROW_RATIO = 2.54

    /** Subjects the Kursstufe requires in all four Halbjahre (Belegpflicht "4 Hj"). */
    val allFourHalves = setOf("D", "M", "G", "Sport")

    private val TWO_DIGITS = Regex("\\d{2}\\.?")
    private val PER_COURSE = Regex("\\d(/\\d)?")
    private val CELL = Regex("(\\d)(?:\\((\\d)\\)?)?(?:\\.?([psPS]))?")
    private val NAME = Regex("[A-ZÄÖÜ][\\p{L}\\-]+(?: [\\p{L}\\-]+)*,\\s*[A-ZÄÖÜ][\\p{L}\\- ]+")
    private val ABITUR = Regex("Abiturjahr:?\\s*(\\d{4})")
    private val HEADER_TEXTS = listOf("pro kurs", "fachart", "fächer")
    private val SINGLE_DIGIT = Regex("\\d")
    private val BRACKET_PART = Regex("[(\\[{]\\d[)\\]}]?(?:\\.?[psPS])?")
    private val LOOSE_CELL = Regex("([0-9OoIl|SsZzB])[(C]([0-9OoIl|SsZzB])\\)?(?:\\.?([psPS]))?")
    private val DIGIT_LOOK_ALIKES = mapOf('O' to 0, 'o' to 0, 'I' to 1, 'l' to 1, '|' to 1, 'S' to 5, 's' to 5, 'Z' to 2, 'z' to 2, 'B' to 8)

    /** Basisfach hours of the subjects required in all four Halbjahre; as Leistungsfach they have 5. */
    private val BASIS_HOURS = mapOf("D" to 3, "M" to 3, "G" to 2, "Sport" to 2)

    /** The weekly hours a subject required in all four Halbjahre can have; null for other subjects. */
    fun possibleHours(subject: String): Set<Int>? = BASIS_HOURS[subject]?.let { setOf(it, 5) }

    /** The parsed sheet plus what was found where. */
    class Detail(
        val kurswahl: Kurswahl,
        /** Table rows between the header and the sums: estimated from the row pitch once both are seen. */
        val tableRows: Int,
        /** Per Halbjahr column: rows whose cell was read ("-" included). */
        val readCells: List<Int>,
        val subjectBoxes: List<TextBox>,
        val cellBoxes: List<List<TextBox>>,
        val sumBoxes: List<TextBox>,
    )

    /** [aspect]: image height ÷ width, to relate row and column distances. */
    fun parse(boxes: List<TextBox>, aspect: Double = 4.0 / 3.0): Kurswahl = parseDetailed(boxes, aspect).kurswahl

    fun parseDetailed(boxes: List<TextBox>, aspect: Double = 4.0 / 3.0): Detail {
        val words = withJoinedCells(boxes.flatMap { it.words() }.map { normalised(it) })
        val lines = boxes.map { normalised(it) }

        // subject column: the x where most subject abbreviations line up
        val subjectBoxes = words.filter { subjectKey(it.text) != null }
        val columnX = densestX(subjectBoxes.map { it.midX }, 0.03) ?: throw Failure(Failure.Reason.NO_SUBJECTS)
        val found = mutableListOf<Pair<String, TextBox>>()
        for (b in subjectBoxes.filter { abs(it.midX - columnX) < 0.04 }.sortedBy { it.midY }) {
            val key = subjectKey(b.text) ?: continue
            val last = found.lastOrNull()
            // the same subject from a second pass is one row
            if (last != null && abs(last.second.midY - b.midY) < 0.006) {
                if ((b.confidence ?: 0.0) > (last.second.confidence ?: 0.0)) found[found.size - 1] = key to b
                continue
            }
            found += key to b
        }
        if (found.size < 5) throw Failure(Failure.Reason.NO_SUBJECTS)
        val diffs = found.map { it.second.midY }.adjacentDifferences()
        var pitch = diffs.median() ?: 0.018
        diffs.filter { it < pitch * 1.5 }.median()?.let { pitch = it }

        // rows: every recognised subject, plus rows in gaps of the regular pitch (a subject the
        // recognition missed, or an empty "--" row) and between the table header and the first row
        val anchors = found.map { Pair<String?, Double>(it.first, it.second.midY) }.toMutableList()
        val gaps = mutableListOf<Double>()
        for ((a, b) in anchors.zipWithNext()) {
            val n = ((b.second - a.second) / pitch).roundToInt()
            if (n >= 2) for (j in 1 until n) gaps += a.second + (b.second - a.second) * j / n
        }
        val firstAnchor = anchors.first()
        val header = lines.filter { line -> HEADER_TEXTS.any { line.text.lowercase().contains(it) } && line.midY < firstAnchor.second }
            .maxOfOrNull { it.midY }
        if (header != null) {
            val n = ((firstAnchor.second - header) / pitch).roundToInt()
            if (n >= 2) for (j in 1 until n) gaps += firstAnchor.second - j * pitch
        }
        anchors += gaps.map { Pair<String?, Double>(null, it) }
        anchors.sortBy { it.second }

        // Halbjahr columns and tilt from the sums row ("Summen", or the numbers under the table)
        val summen = lines.firstOrNull { it.text.lowercase().startsWith("summen") }
            ?: words.firstOrNull { it.text.lowercase().startsWith("summen") }
        var columns = emptyList<Double>()
        var sums: List<Int?> = listOf(null, null, null, null)
        val slope: Double
        val lastRowY = found.last().second.midY
        val sumAnchors = if (summen != null) listOf(summen) else words.filter { w ->
            w.midY > lastRowY && w.midY < lastRowY + pitch * 8 && w.midX > columnX + 0.12 && TWO_DIGITS.matches(w.text)
        }.sortedBy { it.midX }
        val sumLine = sumAnchors.asSequence().mapNotNull { sumRow(words, it, columnX, pitch) }.firstOrNull()
        var sumBoxes: List<TextBox> = sumLine?.first ?: emptyList()
        if (sumLine != null) {
            columns = sumLine.first.map { it.midX }
            sums = sumLine.first.map { b -> b.text.filter { it.isDigit() }.toIntOrNull() }
            slope = sumLine.second
            // the line holds more two-digit numbers right of the sums (the "Anrechnung" columns): a missed first sum
            // takes the next four and every column lands one too far right. The bracketed course numbers ("5(3)")
            // are printed in the first Halbjahr column only, so the columns go where the brackets are.
            val bracketsAt = firstColumnFromBrackets(words, columns, columnX) ?: firstColumnFromSuffixes(words, columns, columnX)
            if (bracketsAt != null) {
                val reference = sumLine.first.first()
                val onLine = words.filter { w ->
                    TWO_DIGITS.matches(w.text) && w.midX > columnX + 0.12 &&
                        abs(w.midY - (reference.midY + slope * (w.midX - reference.midX))) < pitch * 0.6
                }
                // the printed spacing is the common small gap between the line's numbers (a missed sum leaves a double gap)
                val gaps = onLine.map { it.midX }.sorted().adjacentDifferences().filter { it > 0.02 }
                val smallest = gaps.minOrNull()
                if (smallest != null) {
                    val spacing = gaps.filter { it < smallest * 1.25 }.median() ?: smallest
                    columns = (0 until 4).map { bracketsAt + it * spacing }
                    val shifted = columns.map { x -> onLine.filter { abs(it.midX - x) < spacing * 0.3 }.minByOrNull { abs(it.midX - x) } }
                    sums = shifted.map { b -> b?.text?.filter { it.isDigit() }?.toIntOrNull() }
                    sumBoxes = shifted.filterNotNull()
                    columns = columns.mapIndexed { i, x -> shifted[i]?.midX ?: x }
                }
            }
        } else {
            slope = bracketSlope(words, found.map { it.second }, pitch)
        }
        if (columns.isEmpty()) {
            // fallback: the bracketed values "5(3)" sit in the first Halbjahr column
            val bracketed = words.filter { parseCell(it.text).parallel != null }.map { it.midX }
            val first = densestX(bracketed, 0.02) ?: throw Failure(Failure.Reason.NO_COLUMNS)
            val spacing = COLUMN_TO_ROW_RATIO * pitch * aspect
            columns = (0 until 4).map { first + it * spacing }
        }
        val spacing = columns.adjacentDifferences().median() ?: (COLUMN_TO_ROW_RATIO * pitch * aspect)
        val lowest = summen?.midY ?: sumLine?.first?.first()?.midY ?: (lastRowY + pitch)
        val rowAnchors = anchors.filter { it.second < lowest - pitch * 0.5 }
        if (rowAnchors.isEmpty()) throw Failure(Failure.Reason.NO_SUBJECTS)

        // follow the rows column by column: Fachart, pro Kurs, then the four Halbjahre
        val columnXs = listOf(columns[0] - spacing * 2, columns[0] - spacing) + columns
        val ys = rowAnchors.map { it.second }.toMutableList()
        var lastX = columnX
        val picked = List(rowAnchors.size) { arrayOfNulls<TextBox>(columnXs.size) }
        val top = rowAnchors[0].second - pitch * 0.7
        for ((ci, cx) in columnXs.withIndex()) {
            val tokens = words.filter { w ->
                abs(w.midX - cx) < spacing * 0.42 && w.midY > top && w.midY < lowest - pitch * 0.3 && isValue(w.text, ci)
            }
            val predicted = ys.map { it + slope * (cx - lastX) }
            val best = HashMap<Int, Pair<TextBox, Double>>()
            for (token in tokens) {
                // each value to the one row it is nearest to
                val row = predicted.indices.minByOrNull { abs(predicted[it] - token.midY) } ?: continue
                val distance = abs(predicted[row] - token.midY)
                if (distance >= pitch * 0.5) continue
                val score = plausibility(token, ci) - distance / pitch
                val current = best[row]
                if (current == null || score > current.second) best[row] = token to score
            }
            val shifts = mutableListOf<Double>()
            for ((row, value) in best) {
                picked[row][ci] = value.first
                shifts += value.first.midY - predicted[row]
            }
            // rows without a value in this column move with their neighbours
            val drift = shifts.median() ?: 0.0
            for (row in ys.indices) ys[row] = picked[row][ci]?.midY ?: (predicted[row] + drift)
            lastX = cx
        }

        val rows = mutableListOf<Kurswahl.Row>()
        for ((index, anchor) in rowAnchors.withIndex()) {
            val halves = columns.indices.map { h -> picked[index][2 + h]?.let { parseCell(it.text) } ?: Kurswahl.Cell.MISSING }
            val fachart = picked[index][0]?.let { fachartValue(it.text) }
            val perCourse = picked[index][1]?.text
            val key = anchor.first
            if (key != null) {
                rows += Kurswahl.Row(key, fachart, inferMissing(halves, perCourse, allHalves = key in allFourHalves, possibleHours = possibleHours(key)),
                    perCourse = perCourse)
            } else {
                // a gap row: only worth keeping when something is taken in it
                if (halves.none { it.taken }) continue
                val label = words.firstOrNull { abs(it.midX - columnX) < 0.04 && abs(it.midY - anchor.second) < pitch * 0.42 }?.text
                rows += Kurswahl.Row(label?.let { misreadSubject(it) } ?: "?", fachart, inferMissing(halves, perCourse),
                    label = label, perCourse = perCourse)
            }
        }

        val firstRowY = found.first().second.midY
        val name = lines.firstOrNull { line ->
            line.midY < firstRowY - 0.1 && NAME.matches(line.text) && !line.text.startsWith("Name") && !line.text.startsWith("Datum")
        }?.let { line ->
            val parts = line.text.split(",", limit = 2).map { it.trim() }
            if (parts.size == 2) "${parts[1]} ${parts[0]}" else line.text
        }
        val abiturjahr = lines.firstNotNullOfOrNull { ABITUR.find(it.text)?.groupValues?.get(1)?.toIntOrNull() }
        val konfession = when {
            lines.any { it.text.lowercase().contains("katholisch") } -> Kurswahl.Konfession.KATHOLISCH
            lines.any { it.text.lowercase().contains("evangelisch") } -> Kurswahl.Konfession.EVANGELISCH
            else -> null
        }

        val kurswahl = Kurswahl(name, abiturjahr, konfession, completeFromSums(rows, sums), sums)
        var tableRows = rowAnchors.size
        val headerY = lines.filter { line -> HEADER_TEXTS.any { line.text.lowercase().contains(it) } && line.midY < rowAnchors[0].second }
            .maxOfOrNull { it.midY }
        val sumY = sumLine?.first?.first()?.midY ?: summen?.midY
        if (headerY != null && sumY != null) tableRows = maxOf(tableRows, ((sumY - headerY) / pitch).roundToInt() - 1)
        return Detail(kurswahl, tableRows,
            readCells = columns.indices.map { h -> picked.count { it[2 + h] != null } },
            subjectBoxes = found.map { it.second },
            cellBoxes = columns.indices.map { h -> picked.mapNotNull { it[2 + h] } },
            sumBoxes = sumBoxes)
    }

    /**
     * Several photos of the same sheet, each parsed on its own, merged cell by cell: the reading most
     * photos agree on wins, a taken value beats a "-" on a tie (a dash is what a neighbouring row lends
     * most often), and a bracketed course number beats none.
     */
    fun merge(sheets: List<Kurswahl>): Kurswahl {
        val base = sheets.maxByOrNull { sheet -> sheet.rows.count { it.subject != "?" } }
            ?: return Kurswahl(rows = emptyList(), sums = listOf(null, null, null, null))
        if (sheets.size <= 1) return base

        // subjects in sheet order: the base's, others inserted after their predecessor
        val order = base.rows.map { it.subject }.filter { it != "?" }.toMutableList()
        for (sheet in sheets) {
            var previous: String? = null
            for (row in sheet.rows) {
                if (row.subject == "?") continue
                if (row.subject !in order) {
                    val at = previous?.let { order.indexOf(it) }?.takeIf { it >= 0 }?.plus(1) ?: 0
                    order.add(at, row.subject)
                }
                previous = row.subject
            }
        }

        val rows = mutableListOf<Kurswahl.Row>()
        for (key in order) {
            val versions = sheets.flatMap { sheet -> sheet.rows.filter { it.subject == key } }
            val perCourse = mostCommon(versions.map { it.perCourse })
            val halves = (0 until 4).map { h ->
                val cells = versions.mapNotNull { it.halves.getOrNull(h) }
                // a subject required in all four Halbjahre is never "-": such a reading is not a vote
                val read = cells.filter { !it.unreadable && it.inferred != true && (it.taken || key !in allFourHalves) }
                if (read.isEmpty()) {
                    // taken over on every photo: the value agreeing with the photos' "pro Kurs" first, then the most common
                    val possible = possibleHours(key)
                    val inferred = cells.filter { it.inferred == true }.let { all ->
                        all.filter { possible == null || it.hours in possible }.ifEmpty { all }
                    }
                    val hours = inferred.map { it.hours }.let { all ->
                        all.firstOrNull { it != null && "$it" == perCourse } ?: mostCommon(all)
                    }
                    inferred.firstOrNull { it.hours == hours } ?: cells.firstOrNull { it.raw != null } ?: Kurswahl.Cell.MISSING
                } else {
                    val counts = read.groupingBy { it.hours ?: -1 }.eachCount()
                    val winner = read.map { it.hours ?: -1 }.maxWithOrNull { a, b ->
                        val ca = counts.getValue(a)
                        val cb = counts.getValue(b)
                        when {
                            ca != cb -> ca.compareTo(cb)
                            a == -1 && b != -1 -> -1
                            b == -1 && a != -1 -> 1
                            else -> 0
                        }
                    } ?: -1
                    val agreeing = read.filter { (it.hours ?: -1) == winner }
                    val chosen = agreeing.firstOrNull { it.parallel != null } ?: agreeing[0]
                    mostCommon(agreeing.map { it.parallel })?.let { chosen.copy(parallel = it) } ?: chosen
                }
            }
            rows += Kurswahl.Row(key, mostCommon(versions.map { it.fachart }), halves, perCourse = mostCommon(versions.map { it.perCourse }))
        }

        // unrecognised rows of the base stay unless another photo named that subject
        val baseKnown = base.rows.map { it.subject }.toSet()
        val recovered = rows.filter { it.subject !in baseKnown }
        // a subject whose row is empty in the base but has values on another photo: the base's "?" row with
        // exactly those values (course number included) is that subject read one row off, not another subject
        val baseEmpty = base.rows.filter { r -> r.subject != "?" && r.halves.none { it.taken } }.map { it.subject }.toSet()
        val filledElsewhere = rows.filter { it.subject in baseEmpty && it.halves.any { c -> c.taken && c.inferred != true } }
        for ((index, row) in base.rows.withIndex()) {
            if (row.subject != "?") continue
            val named = recovered.any { r -> r.halves.zip(row.halves).all { (a, b) -> a.hours == b.hours || b.hours == null } }
            if (named) continue
            val sameValues = filledElsewhere.any { r ->
                r.halves.zip(row.halves).all { (a, b) -> a.hours == null || b.hours == null || (a.hours == b.hours && (a.parallel == null || b.parallel == null || a.parallel == b.parallel)) } &&
                    r.halves.zip(row.halves).any { (a, b) -> b.parallel != null && a.parallel == b.parallel }
            }
            if (sameValues) continue
            val previous = base.rows.subList(0, index).lastOrNull { it.subject != "?" }?.subject
            val at = previous?.let { p -> rows.indexOfFirst { it.subject == p } }?.takeIf { it >= 0 }?.plus(1) ?: 0
            rows.add(at, row)
        }

        val sums = (0 until 4).map { h -> mostCommon(sheets.map { it.sums.getOrNull(h) }) }
        return Kurswahl(
            name = sheets.firstNotNullOfOrNull { it.name },
            abiturjahr = sheets.firstNotNullOfOrNull { it.abiturjahr },
            konfession = sheets.firstNotNullOfOrNull { it.konfession },
            rows = withoutLoneExcess(rows, sheets, sums), sums = sums)
    }

    /**
     * A Halbjahr that adds up to more than its sum after merging: the usual cause is a value only one photo
     * read while the others found the row without it (a photo whose columns were placed one too far right).
     * When exactly one such value is the whole excess, it is dropped. Required subjects keep theirs.
     */
    private fun withoutLoneExcess(rows: List<Kurswahl.Row>, sheets: List<Kurswahl>, sums: List<Int?>): List<Kurswahl.Row> {
        val halves = rows.map { it.halves.toMutableList() }
        for (h in 0 until 4) {
            val sum = sums.getOrNull(h) ?: continue
            val excess = halves.sumOf { it.getOrNull(h)?.hours ?: 0 } - sum
            if (excess <= 0) continue
            val lone = rows.indices.filter { i ->
                val row = rows[i]
                val cell = halves[i].getOrNull(h) ?: return@filter false
                if (row.subject == "?" || row.subject in allFourHalves || cell.inferred == true || cell.hours != excess) return@filter false
                val versions = sheets.mapNotNull { sheet -> sheet.rows.firstOrNull { it.subject == row.subject }?.halves?.getOrNull(h) }
                versions.count { !it.unreadable && it.inferred != true && it.hours == cell.hours } == 1 && versions.any { it.unreadable }
            }
            val i = lone.singleOrNull() ?: continue
            halves[i][h] = Kurswahl.Cell.MISSING
        }
        return rows.mapIndexed { i, row -> if (halves[i] == row.halves) row else row.copy(halves = halves[i]) }
    }

    /**
     * The x of the first Halbjahr column when the bracketed course numbers are densest one or two columns
     * left of [columns] (where a missed first sum puts them); null when they are where the columns say.
     */
    private fun firstColumnFromBrackets(words: List<TextBox>, columns: List<Double>, columnX: Double): Double? {
        val spacing = columns.adjacentDifferences().median() ?: return null
        val xs = words.filter { it.midX > columnX + 0.05 && parseCell(it.text).parallel != null }.map { it.midX }
        val at = densestX(xs, spacing * 0.3) ?: return null
        if (xs.count { abs(it - at) < spacing * 0.3 } < 3) return null
        val shift = ((at - columns[0]) / spacing).roundToInt()
        return at.takeIf { shift in -2..-1 && abs(at - (columns[0] + shift * spacing)) < spacing * 0.3 }
    }

    /**
     * The x of the first Halbjahr column, one column left of [columns], when too few brackets were read for
     * [firstColumnFromBrackets]: a plain "2.p" / "2.s" (a course of two later Halbjahre) sits in the column
     * taken for the first Halbjahr, no bracketed value does, and the column to its left has one.
     */
    private fun firstColumnFromSuffixes(words: List<TextBox>, columns: List<Double>, columnX: Double): Double? {
        val spacing = columns.adjacentDifferences().median() ?: return null
        fun cells(x: Double) = words.filter { it.midX > columnX + 0.05 && abs(it.midX - x) < spacing * 0.3 }.map { parseCell(it.text) }
        val first = cells(columns[0])
        if (first.any { it.parallel != null } || first.none { it.suffix != null && it.parallel == null }) return null
        val left = columns[0] - spacing
        return left.takeIf { cells(it).any { c -> c.parallel != null } }
    }

    /** "5(3)" → 5 hours, course 3; "2(3).p" → 2, course 3; "2.s" → 2; "-" → not taken. */
    fun parseCell(text: String): Kurswahl.Cell {
        var t = normalisedText(text).replace(" ", "")
        for ((from, to) in listOf("[" to "(", "{" to "(", "]" to ")", "}" to ")", "," to ".", "S." to "5.")) t = t.replace(from, to)
        if (t.isEmpty()) return Kurswahl.Cell.MISSING
        if (t.all { it in "-–—_." }) return Kurswahl.Cell(raw = text, unreadable = false)
        CELL.matchEntire(t)?.let { m ->
            return Kurswahl.Cell(raw = text, hours = m.groupValues[1].toInt(), parallel = m.groups[2]?.value?.toInt(),
                unreadable = false, suffix = m.groups[3]?.value?.lowercase())
        }
        // a digit read as a look-alike letter ("S(3)", "5(l)"): only in the bracketed form, where the shape is unmistakable
        LOOSE_CELL.matchEntire(t)?.let { m ->
            val hours = digitValue(m.groupValues[1])
            val parallel = digitValue(m.groupValues[2])
            if (hours in 1..5 && parallel in 1..9) {
                return Kurswahl.Cell(raw = text, hours = hours, parallel = parallel, unreadable = false, suffix = m.groups[3]?.value?.lowercase())
            }
        }
        // specks and table lines read as "..E" are not a value; only text with a digit is worth asking about
        val unreadable = t.any { it.isDigit() }
        return Kurswahl.Cell(raw = if (unreadable) text else null, unreadable = unreadable)
    }

    /**
     * A Halbjahr cell that was not read, taken over when the other three agree on plain hours (a
     * subject taken all four Halbjahre) and "pro Kurs" does not contradict. For a subject required
     * every Halbjahr ([allHalves]) a gap or dash is a misread, and "pro Kurs" gives the hours.
     */
    fun inferMissing(halves: List<Kurswahl.Cell>, perCourse: String?, allHalves: Boolean = false, possibleHours: Set<Int>? = null): List<Kurswahl.Cell> {
        if (halves.size != 4) return halves
        val result = halves.toMutableList()
        for (i in halves.indices) {
            if (halves[i].taken) continue
            if (allHalves) {
                // [possibleHours]: a "pro Kurs" the subject cannot have belongs to a neighbouring row
                fun possible(hours: Int?) = hours?.takeIf { possibleHours == null || it in possibleHours }
                val hours = possible(perCourse?.toIntOrNull()) ?: possible(mostCommon(halves.map { it.hours }))
                if (hours != null) {
                    result[i] = Kurswahl.Cell(hours = hours, unreadable = false, inferred = true)
                } else if (!halves[i].unreadable) {
                    // a "-" is a misread here (the neighbouring row's): not a reading that could outvote another photo
                    result[i] = Kurswahl.Cell.MISSING
                }
                continue
            }
            if (!halves[i].unreadable) continue
            val others = halves.filterIndexed { idx, _ -> idx != i }
            if (!others.all { it.taken && it.suffix == null }) continue
            val hours = others.first().hours ?: continue
            if (!others.all { it.hours == hours }) continue
            if (perCourse?.toIntOrNull()?.let { it != hours } == true) continue
            result[i] = Kurswahl.Cell(hours = hours, unreadable = false, inferred = true)
        }
        return result
    }

    /**
     * Cells still missing after reading, completed from the "Summen" row: every Halbjahr column adds up
     * to its sum. A row taken in the other Halbjahre with the same hours whose cell alone is missing
     * in a column gets its hours when the column's remainder is exactly that. A subject required in all
     * four Halbjahre ([allFourHalves]) with nothing read at all gets the remainder when it is one of
     * the subject's possible hours, or the hours its Fachart implies. Cells that were read never change.
     */
    fun completeFromSums(rows: List<Kurswahl.Row>, sums: List<Int?>): List<Kurswahl.Row> {
        val halves = rows.map { it.halves.toMutableList() }
        fun remainder(h: Int, except: Int): Int? {
            val sum = sums.getOrNull(h) ?: return null
            return sum - halves.indices.filter { it != except }.sumOf { halves[it].getOrNull(h)?.hours ?: 0 }
        }
        fun fillSingleGaps() {
            for (h in 0 until 4) {
                if (sums.getOrNull(h) == null) continue
                // a required subject not read in this column is a gap too, so the remainder is not handed to another row
                val gaps = rows.indices.filter { i ->
                    val cell = halves[i].getOrNull(h)
                    cell != null && !cell.taken &&
                        (rows[i].subject in allFourHalves || (cell.unreadable && hoursElsewhere(halves[i], rows[i].perCourse, h) != null))
                }
                val i = gaps.singleOrNull() ?: continue
                val hours = hoursElsewhere(halves[i], rows[i].perCourse, h) ?: continue
                if (remainder(h, i) == hours) halves[i][h] = Kurswahl.Cell(hours = hours, unreadable = false, inferred = true)
            }
        }
        fillSingleGaps()
        for ((i, row) in rows.withIndex()) {
            val basis = BASIS_HOURS[row.subject] ?: continue
            if (halves[i].size != 4 || halves[i].any { it.taken }) continue
            val possible = setOf(basis, 5)
            val votes = (0 until 4).mapNotNull { h -> remainder(h, i)?.takeIf { it in possible } }
            val byFachart = when (row.fachart) {
                "L" -> 5
                "B", "m" -> basis
                else -> null
            }
            val hours = when {
                votes.isEmpty() -> byFachart
                votes.distinct().size == 1 && (votes.size >= 2 || byFachart == votes[0]) -> votes[0]
                byFachart != null && byFachart in votes -> byFachart
                else -> null
            } ?: continue
            for (h in 0 until 4) halves[i][h] = Kurswahl.Cell(hours = hours, unreadable = false, inferred = true)
        }
        fillSingleGaps()
        return rows.mapIndexed { i, row -> if (halves[i] == row.halves) row else row.copy(halves = halves[i]) }
    }

    /** The hours a row has in the Halbjahre other than [half], when those read agree (and "pro Kurs" does not contradict). */
    private fun hoursElsewhere(halves: List<Kurswahl.Cell>, perCourse: String?, half: Int): Int? {
        // a "-" read in another Halbjahr: the subject is not taken throughout, so it says nothing about this one
        if (halves.withIndex().any { (i, c) -> i != half && !c.taken && !c.unreadable }) return null
        val others = halves.filterIndexed { i, c -> i != half && c.taken && c.inferred != true }
        if (others.isEmpty() || others.any { it.suffix != null }) return null
        // a course of two Halbjahre ("2.p", "2.s", the suffix not read) must not pass for one taken throughout:
        // the other readings have to be three, or lie on both sides of this Halbjahr, or not be neighbours
        val at = halves.indices.filter { it != half && halves[it].taken && halves[it].inferred != true }
        if (at.size < 3 && !(at.first() < half && half < at.last()) && !(at.size == 2 && at[1] - at[0] > 1)) return null
        val hours = others[0].hours ?: return null
        if (others.any { it.hours != hours }) return null
        if (perCourse?.toIntOrNull()?.let { it != hours } == true) return null
        return hours
    }

    /** "5" and "(3)" recognised as two boxes side by side: also offered as the one value "5(3)". */
    private fun withJoinedCells(words: List<TextBox>): List<TextBox> {
        val brackets = words.filter { BRACKET_PART.matches(it.text) }
        if (brackets.isEmpty()) return words
        val joined = mutableListOf<TextBox>()
        for (digit in words) {
            if (!SINGLE_DIGIT.matches(digit.text)) continue
            val bracket = brackets.filter { b ->
                abs(b.midY - digit.midY) < maxOf(digit.height, b.height) * 0.6 && b.x - digit.maxX in -digit.width..(digit.width * 2 + 0.004)
            }.minByOrNull { abs(it.x - digit.maxX) } ?: continue
            val y = minOf(digit.y, bracket.y)
            joined += TextBox(digit.text + bracket.text, digit.x, y, bracket.maxX - digit.x, maxOf(digit.maxY, bracket.maxY) - y,
                minOf(digit.confidence ?: 0.5, bracket.confidence ?: 0.5))
        }
        return words + joined
    }

    private fun digitValue(text: String): Int = text[0].digitToIntOrNull() ?: DIGIT_LOOK_ALIKES[text[0]] ?: -1

    private fun isValue(text: String, column: Int): Boolean = when (column) {
        0 -> text in setOf("L", "B", "m", "L/B", "LB", "UB", "L/8")
        1 -> PER_COURSE.matches(text)
        else -> parseCell(text).let { it.hours != null || it.raw != null }
    }

    private fun fachartValue(text: String): String = if (text in setOf("LB", "UB", "L/8")) "L/B" else text

    /** How much a token looks like the value its column holds, 0…~1.2; distance is taken off separately. */
    private fun plausibility(token: TextBox, column: Int): Double {
        val confidence = (token.confidence ?: 0.5) * 0.1
        if (column < 2) return 1 + confidence
        val cell = parseCell(token.text)
        return (if (cell.unreadable) 0.3 else 1.0) + (if (cell.parallel != null) 0.15 else 0.0) + confidence
    }

    /**
     * The four Halbjahr sums: two-digit numbers on one straight line through [anchor] ("Summen", or
     * the first sum). The photo may be tilted, so the line is searched, not assumed horizontal.
     */
    private fun sumRow(words: List<TextBox>, anchor: TextBox, columnX: Double, pitch: Double): Pair<List<TextBox>, Double>? {
        val numbers = words.filter { w ->
            w.midX > columnX + 0.12 && abs(w.midY - anchor.midY) < pitch * 4 && TWO_DIGITS.matches(w.text)
        }
        var best: Pair<List<TextBox>, Double>? = null
        for (candidate in numbers) {
            val dx = candidate.midX - anchor.midX
            if (dx <= 0.05) continue
            val slope = (candidate.midY - anchor.midY) / dx
            if (abs(slope) >= 0.2) continue
            val onLine = numbers
                .filter { it.midX >= anchor.midX - 0.01 && abs(it.midY - (anchor.midY + slope * (it.midX - anchor.midX))) < pitch * 0.5 }
                .sortedBy { it.midX }
                .fold(mutableListOf<TextBox>()) { acc, w ->
                    if (acc.isEmpty() || abs(acc.last().midX - w.midX) >= 0.02) acc += w
                    acc
                }
            if (onLine.size >= 4 && onLine.size > (best?.first?.size ?: 0)) {
                val first4 = onLine.take(4)
                val reference = if (first4[0].midX - anchor.midX > 0.05) anchor else first4[0]
                val fitted = (first4[3].midY - reference.midY) / (first4[3].midX - reference.midX)
                best = first4 to fitted
            }
        }
        return best
    }

    /** Tilt from bracketed values ("5(3)") and the subject on their row. */
    private fun bracketSlope(words: List<TextBox>, rows: List<TextBox>, pitch: Double): Double {
        val slopes = mutableListOf<Double>()
        for (w in words) {
            if (parseCell(w.text).parallel == null) continue
            val row = rows.minByOrNull { abs(it.midY - w.midY) } ?: continue
            if (abs(row.midY - w.midY) >= pitch * 0.9 || w.midX - row.midX <= 0.1) continue
            slopes += (w.midY - row.midY) / (w.midX - row.midX)
        }
        return slopes.median() ?: 0.0
    }

    private fun <T> mostCommon(values: List<T?>): T? {
        val present = values.filterNotNull()
        val counts = present.groupingBy { it }.eachCount()
        return present.maxByOrNull { counts.getValue(it) }
    }

    private fun subjectKey(text: String): String? = SchoolReference.subjects.firstOrNull { it.key.equals(text, ignoreCase = true) }?.key

    /** Common recognition slips in the subject column of a row that was not recognised at first. */
    private fun misreadSubject(text: String): String? {
        subjectKey(text.replace("0", "o"))?.let { return it }
        return when (text) {
            "O", "0", "Ö", "Q", "o", "DI", "D.", "D," -> "D"
            "Mü", "Mo" -> "Mu"
            "Sp.", "5p" -> "Sp"
            else -> null
        }
    }

    private val LOOK_ALIKES = mapOf(
        'Р' to 'P', 'р' to 'p', 'С' to 'C', 'с' to 'c', 'О' to 'O', 'о' to 'o', 'Е' to 'E', 'е' to 'e',
        'Ѕ' to 'S', 'ѕ' to 's', 'Н' to 'H', 'К' to 'K', 'М' to 'M', 'Т' to 'T', 'В' to 'B', 'А' to 'A', 'а' to 'a',
        'Ο' to 'O', 'ο' to 'o', 'Ι' to 'I', 'Β' to 'B',
    )

    /** Text recognition reads some Latin letters as look-alike Cyrillic or Greek ones. */
    private fun normalisedText(text: String): String = text.map { LOOK_ALIKES[it] ?: it }.joinToString("").trim()

    private fun normalised(box: TextBox): TextBox = box.copy(text = normalisedText(box.text))

    /** The centre of the densest cluster of x values. */
    private fun densestX(xs: List<Double>, window: Double): Double? {
        if (xs.isEmpty()) return null
        val best = xs.maxBy { a -> xs.count { abs(it - a) < window } }
        return xs.filter { abs(it - best) < window }.median()
    }
}
