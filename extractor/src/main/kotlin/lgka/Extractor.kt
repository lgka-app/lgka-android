package lgka

/**
 * Substitution-plan extractor — Kotlin port of the reference implementation
 * (verification repo, lib/extractor_v2.dart). Must reproduce the `expected`
 * objects of the "goldens/substitution" v2 goldens exactly.
 *
 * Port note vs the Dart reference: Syncfusion emits one TextLine per drawn
 * text block, so the three header blocks (school / "SJ ..." / "Untis ...")
 * arrive as separate lines. Char-level clustering merges same-height blocks
 * into one visual line, so the pre-title meta zone is re-split into segments
 * on x-gaps > [SEGMENT_GAP] before classification. Everything below the
 * title is anchored per visual line exactly like the reference.
 */
object Extractor {
    private val WEEKDAYS = listOf(
        "Montag", "Dienstag", "Mittwoch", "Donnerstag",
        "Freitag", "Samstag", "Sonntag",
    )

    private val COLUMN_NAMES = listOf(
        "type", "period", "classes", "substitute", "subject", "room",
        "originalSubject", "originalTeacher", "originalRoom", "note",
    )

    private const val SEGMENT_GAP = 15.0

    /** "6ab" -> [6a, 6b]; "5a, 7c" -> [5a, 7c]; "J11" -> [J11]. */
    private fun expandClasses(cell: String): List<String> {
        val out = mutableListOf<String>()
        for (part in cell.split(',')) {
            val p = part.trim()
            if (p.isEmpty()) continue
            val m = Regex("^(\\d{1,2})([a-e]{2,})$").find(p)
            if (m != null) {
                for (letter in m.groupValues[2]) out.add("${m.groupValues[1]}$letter")
            } else {
                out.add(p)
            }
        }
        return out
    }

    fun extract(lines: List<Line>): LinkedHashMap<String, Any?> {
        val plan = linkedMapOf<String, Any?>(
            "school" to null, "address" to null, "schoolYear" to null,
            "untisVersion" to null, "generatedAt" to null, "planDate" to null,
            "weekday" to null, "isEmpty" to false,
            "announcements" to mutableListOf<String>(),
            "absentTeachers" to listOf<String>(),
            "absentClasses" to listOf<String>(),
            "entries" to mutableListOf<LinkedHashMap<String, Any?>>(),
            "footer" to linkedMapOf<String, Any?>(),
        )

        if (lines.sumOf { it.text.trim().length } < 50) {
            plan["isEmpty"] = true
            return plan
        }

        // ---- classify anchor lines ---------------------------------------
        var titleIdx: Int? = null
        var teachersIdx: Int? = null
        var classesIdx: Int? = null
        var headerIdx: Int? = null
        var footerIdx: Int? = null
        val footerAnchor = Regex("\\d{1,2}\\.\\d{1,2}\\.\\d{4}\\s*\\(\\d+\\)\\s*SJ\\s")
        for ((i, line) in lines.withIndex()) {
            val t = line.text.trim()
            if (titleIdx == null && t.contains("Klassen") && t.contains("/") &&
                WEEKDAYS.any { t.contains(it) }
            ) {
                titleIdx = i
            } else if (t.startsWith("Abwesende Lehrer")) {
                teachersIdx = i
            } else if (t.startsWith("Abwesende Klassen")) {
                classesIdx = i
            } else if (headerIdx == null && t.startsWith("Art") && t.contains("Stunde")) {
                headerIdx = i
            } else if (footerAnchor.containsMatchIn(t)) {
                footerIdx = i
            }
        }

        // ---- meta zone: segment same-height blocks, classify each --------
        for (i in 0 until (titleIdx ?: lines.size)) {
            for (segment in segments(lines[i])) {
                val t = segment.trim()
                when {
                    Regex("^SJ \\d{4}-\\d{4}$").matches(t) -> plan["schoolYear"] = t
                    t.startsWith("Untis ") -> plan["untisVersion"] = t
                    Regex("^\\d{1,2}\\.\\d{1,2}\\.\\d{4}\\s+\\d{1,2}:\\d{2}$").matches(t) ->
                        plan["generatedAt"] = t.replace(Regex("\\s+"), " ")
                    plan["school"] == null -> plan["school"] = t
                    plan["address"] == null -> plan["address"] = t
                }
            }
        }

        // ---- footer -------------------------------------------------------
        var footerYear: String? = null
        if (footerIdx != null) {
            val m = Regex(
                "(?:Periode\\s+(\\d+)\\s+)?(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})\\s+\\((\\d+)\\)\\s+SJ\\s+(\\S+)"
            ).find(lines[footerIdx].text.replace(Regex("\\s+"), " "))
            if (m != null) {
                footerYear = m.groupValues[4]
                val dd = m.groupValues[2].padStart(2, '0')
                val mm = m.groupValues[3].padStart(2, '0')
                plan["footer"] = linkedMapOf<String, Any?>(
                    "untisPeriod" to m.groupValues[1].ifEmpty { null }?.toInt(),
                    "date" to "$dd.$mm.${m.groupValues[4]}",
                    "calendarWeek" to m.groupValues[5].toInt(),
                    "schoolYearShort" to "SJ ${m.groupValues[6]}",
                )
            }
        }

        // ---- title --------------------------------------------------------
        if (titleIdx != null) {
            val m = Regex("(\\d{1,2})\\.(\\d{1,2})\\.\\s*/\\s*(\\w+)")
                .find(lines[titleIdx].text)
            if (m != null) {
                plan["weekday"] = m.groupValues[3]
                if (footerYear != null) {
                    val dd = m.groupValues[1].padStart(2, '0')
                    val mm = m.groupValues[2].padStart(2, '0')
                    plan["planDate"] = "$dd.$mm.$footerYear"
                }
            }
        }

        // ---- announcements ------------------------------------------------
        val annEnd = listOfNotNull(teachersIdx, classesIdx, headerIdx, footerIdx, lines.size).min()
        if (titleIdx != null) {
            @Suppress("UNCHECKED_CAST")
            val announcements = plan["announcements"] as MutableList<String>
            for (i in titleIdx + 1 until annEnd) {
                val t = lines[i].text.trim().replace(Regex("\\s+"), " ")
                if (t.isNotEmpty()) announcements.add(t)
            }
        }

        // ---- absences -----------------------------------------------------
        fun valuesAfterColon(line: Line): List<String> {
            val colon = line.text.indexOf(':')
            if (colon < 0) return emptyList()
            return line.text.substring(colon + 1).split(',')
                .map { it.trim() }.filter { it.isNotEmpty() }
        }
        teachersIdx?.let { plan["absentTeachers"] = valuesAfterColon(lines[it]) }
        classesIdx?.let { plan["absentClasses"] = valuesAfterColon(lines[it]) }

        // ---- table --------------------------------------------------------
        if (headerIdx != null) {
            val xs = mutableListOf<Double>()
            var lastRight: Double? = null
            for (w in lines[headerIdx].words) {
                if (w.text.isBlank()) continue
                if (xs.isEmpty() || w.left - lastRight!! > 3) xs.add(w.left)
                lastRight = w.right
            }
            check(xs.size == COLUMN_NAMES.size) {
                "expected ${COLUMN_NAMES.size} columns, found ${xs.size}: $xs"
            }

            fun columnOf(left: Double): Int {
                for (c in xs.indices.reversed()) {
                    if (left >= xs[c] - 3) return c
                }
                return 0
            }

            @Suppress("UNCHECKED_CAST")
            val entries = plan["entries"] as MutableList<LinkedHashMap<String, Any?>>
            var current: LinkedHashMap<String, Any?>? = null
            val tableEnd = footerIdx ?: lines.size
            for (i in headerIdx + 1 until tableEnd) {
                val cells = MutableList(COLUMN_NAMES.size) { "" }
                var prevCol: Int? = null
                var prevRight: Double? = null
                for (w in lines[i].words) {
                    val t = w.text.trim()
                    if (t.isEmpty()) continue
                    val c = columnOf(w.left)
                    cells[c] = when {
                        cells[c].isEmpty() -> t
                        c == prevCol && w.left - prevRight!! <= 3 -> "${cells[c]}$t"
                        else -> "${cells[c]} $t"
                    }
                    prevCol = c
                    prevRight = w.right
                }
                if (cells.all { it.isEmpty() }) continue

                if (cells[0].isNotEmpty() || cells[1].isNotEmpty()) {
                    val entry = linkedMapOf<String, Any?>()
                    for (c in COLUMN_NAMES.indices) {
                        entry[COLUMN_NAMES[c]] = cells[c].ifEmpty { null }
                    }
                    entry["classesRaw"] = cells[2].ifEmpty { null }
                    entry["classes"] = expandClasses(cells[2])
                    entries.add(entry)
                    current = entry
                } else if (current != null) {
                    for (c in COLUMN_NAMES.indices) {
                        if (cells[c].isEmpty() || COLUMN_NAMES[c] == "classes") continue
                        val prev = current[COLUMN_NAMES[c]] as String?
                        current[COLUMN_NAMES[c]] =
                            if (prev == null) cells[c] else "$prev ${cells[c]}"
                    }
                }
            }
        }

        return plan
    }

    /** Splits a visual line into text segments on x-gaps > [SEGMENT_GAP]. */
    private fun segments(line: Line): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var prevRight: Double? = null
        for (w in line.words) {
            if (sb.isNotEmpty() && w.left - prevRight!! > SEGMENT_GAP) {
                out.add(sb.toString())
                sb.clear()
            }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(w.text)
            prevRight = w.right
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }
}
