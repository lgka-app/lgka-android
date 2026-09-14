package lgka.plan

/** A part of a photo, 0…1 of the image, origin top-left. */
data class Region(val left: Double, val top: Double, val right: Double, val bottom: Double)

/** Column spacing of the printed Halbjahr columns, as a share of the image width, when too few columns were read. */
private const val TYPICAL_COLUMN_SPACING = 0.06

/** Bands read again per photo at most (each is recognised twice). */
private const val MAX_REGIONS = 6

/**
 * Where a second, enlarged reading is worth it after [KurswahlParser.parseDetailed]: the sums row when
 * a sum is missing, the row of every subject required in all four Halbjahre whose cells were not all
 * read (Sport's plain "2" is the value recognition misses most), rows with a value that was found but
 * not readable, and Halbjahr columns with few readings. Row bands run from the subject to past the
 * fourth Halbjahr column and follow the tilt of the sums row.
 */
fun KurswahlParser.recheckRegions(detail: KurswahlParser.Detail): List<Region> {
    val subjects = detail.subjectBoxes.sortedBy { it.midY }
    if (subjects.size < 2) return emptyList()
    val diffs = subjects.map { it.midY }.adjacentDifferences()
    val pitch = diffs.median()?.let { m -> diffs.filter { it < m * 1.5 }.median() ?: m } ?: return emptyList()
    val subjectX = subjects.map { it.midX }.median() ?: return emptyList()

    val columns: List<Double>
    val slope: Double
    if (detail.sumBoxes.size == 4) {
        columns = detail.sumBoxes.map { it.midX }
        val first = detail.sumBoxes.first()
        val last = detail.sumBoxes.last()
        slope = (last.midY - first.midY) / (last.midX - first.midX)
    } else {
        val read = detail.cellBoxes.mapIndexedNotNull { h, boxes -> boxes.map { it.midX }.median()?.let { h to it } }
        if (read.isEmpty()) return emptyList()
        val spacing = read.zipWithNext { a, b -> (b.second - a.second) / (b.first - a.first) }.median() ?: TYPICAL_COLUMN_SPACING
        val (h0, x0) = read.first()
        columns = (0 until 4).map { x0 + (it - h0) * spacing }
        slope = 0.0
    }
    val spacing = columns.adjacentDifferences().median() ?: TYPICAL_COLUMN_SPACING
    val right = minOf(1.0, columns.last() + spacing * 0.6)
    fun y(atY: Double, atX: Double, x: Double) = atY + slope * (x - atX)
    fun clamp(r: Region) = Region(maxOf(0.0, r.left), maxOf(0.0, r.top), minOf(1.0, r.right), minOf(1.0, r.bottom))

    val regions = mutableListOf<Region>()
    val lastRow = subjects.last()
    if (detail.kurswahl.sums.any { it == null }) {
        val top = minOf(y(lastRow.midY, lastRow.midX, columns.first()), y(lastRow.midY, lastRow.midX, columns.last())) + pitch * 0.5
        regions += clamp(Region(columns.first() - spacing * 1.5, top, right, top + pitch * 7))
    }

    val rowBands = mutableListOf<Region>()
    for (row in detail.kurswahl.rows) {
        val required = row.subject in KurswahlParser.allFourHalves && row.halves.any { !it.taken || it.inferred == true }
        val unreadable = row.halves.any { it.unreadable && it.raw != null }
        if (!required && !unreadable) continue
        val box = subjects.firstOrNull { it.text.equals(row.subject, ignoreCase = true) } ?: continue
        val left = box.x - 0.01
        val yLeft = y(box.midY, box.midX, left)
        val yRight = y(box.midY, box.midX, right)
        val band = Region(left, minOf(yLeft, yRight) - pitch * 1.1, right, maxOf(yLeft, yRight) + pitch * 1.1)
        // required subjects first: that is where a gap costs the most
        if (required) rowBands.add(0, band) else rowBands += band
    }
    regions += rowBands.map(::clamp)

    val firstRow = subjects.first()
    for ((h, x) in columns.withIndex()) {
        if (detail.readCells.getOrElse(h) { 0 } >= detail.tableRows * 0.8) continue
        val top = minOf(y(firstRow.midY, firstRow.midX, x), y(firstRow.midY, subjectX, x)) - pitch
        val bottom = y(lastRow.midY, lastRow.midX, x) + pitch
        regions += clamp(Region(x - spacing * 0.55, top, x + spacing * 0.55, bottom))
    }
    return regions.filter { it.right - it.left > 0.01 && it.bottom - it.top > 0.005 }
        .distinctBy { listOf(it.left, it.top, it.right, it.bottom).map { v -> (v * 200).toInt() } }
        .take(MAX_REGIONS)
}

/**
 * [base] with the cells it did not read taken from [extra], a reading of the same photo with more
 * text (enlarged bands): a cell read in [base] never changes. Values taken over from the rest of the
 * sheet are worked out again afterwards, so they agree with what was newly read.
 */
fun KurswahlParser.fillGaps(base: Kurswahl, extra: Kurswahl): Kurswahl {
    fun read(cell: Kurswahl.Cell) = !cell.unreadable && cell.inferred != true
    val sums = base.sums.mapIndexed { h, sum -> sum ?: extra.sums.getOrNull(h) }
    val rows = base.rows.map { row ->
        val other = extra.rows.firstOrNull { it.subject == row.subject && row.subject != "?" } ?: return@map row
        if (row.halves.size != 4 || other.halves.size != 4) return@map row
        val halves = row.halves.mapIndexed { h, cell ->
            when {
                read(cell) -> cell
                read(other.halves[h]) -> other.halves[h]
                cell.inferred == true -> Kurswahl.Cell.MISSING
                else -> cell
            }
        }
        if (halves.withIndex().none { (h, c) -> c != row.halves[h] && read(c) }) return@map row
        val perCourse = row.perCourse ?: other.perCourse
        row.copy(fachart = row.fachart ?: other.fachart, perCourse = perCourse,
            halves = inferMissing(halves, perCourse, allHalves = row.subject in allFourHalves, possibleHours = possibleHours(row.subject)))
    }
    return base.copy(rows = completeFromSums(rows, sums), sums = sums)
}

/**
 * Whether a burst's merged reading is worth reading again in enlarged bands: a sum missing, a Halbjahr column
 * that does not add up to its sum, a value taken over instead of read, a value found but unreadable, or a row
 * with values whose subject was not recognised. A complete reading is not read again (that costs seconds).
 */
fun KurswahlParser.needsRereads(kurswahl: Kurswahl): Boolean {
    if (kurswahl.sums.size < 4 || kurswahl.sums.any { it == null }) return true
    for (h in 0 until 4) {
        if (kurswahl.rows.sumOf { it.halves.getOrNull(h)?.hours ?: 0 } != kurswahl.sums[h]) return true
    }
    return kurswahl.rows.any { row ->
        row.halves.any { it.inferred == true || (it.unreadable && it.raw != null) } || (row.subject == "?" && row.halves.any { it.taken })
    }
}
