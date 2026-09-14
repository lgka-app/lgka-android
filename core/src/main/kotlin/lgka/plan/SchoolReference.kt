package lgka.plan

import kotlinx.serialization.Serializable

/**
 * Fixed facts of the Lessing-Gymnasium the custom plan needs: Läuteordnung, subjects of the
 * Kurswahlprotokoll with their Untis course stems, and the teacher codes of the Kollegium.
 */
object SchoolReference {
    // ── Läuteordnung (Schulordnung 2025, valid from SJ 2025/26) ──────────────────

    @Serializable
    data class Period(val number: Int, val start: String, val end: String)

    /** The break follows period [after]. */
    @Serializable
    data class Break(val after: Int, val start: String, val end: String)

    /** The school website still shows an older grid; this is the valid one. */
    val periods = listOf(
        Period(1, "7:45", "8:30"), Period(2, "8:35", "9:20"),
        Period(3, "9:35", "10:20"), Period(4, "10:25", "11:10"),
        Period(5, "11:30", "12:15"), Period(6, "12:20", "13:05"),
        Period(7, "13:10", "13:55"), Period(8, "14:00", "14:45"),
        Period(9, "14:50", "15:35"), Period(10, "15:50", "16:35"),
        Period(11, "16:35", "17:20"),
    )

    val breaks = listOf(Break(2, "9:20", "9:35"), Break(4, "11:10", "11:30"), Break(9, "15:35", "15:50"))

    /** Double-period blocks 1–2, 3–4, 5–6, 10–11; 7, 8, 9 are single periods. */
    val blockStarts = setOf(1, 3, 5, 7, 8, 9, 10)

    const val LAEUTEORDNUNG_SOURCE = "Läuteordnung ab SJ 2025/26 (Schulordnung, Stand 2025)"

    // ── Subjects ─────────────────────────────────────────────────────────────────

    /**
     * [key] is the abbreviation in the Kurswahlprotokoll's "Fächer" column ("D", "Gk", "Sport");
     * [stems] the lower-cased letter part of the Untis course codes ("m" matches "M3" and "m1").
     */
    data class Subject(val key: String, val name: String, val stems: List<String>)

    val subjects = listOf(
        Subject("D", "Deutsch", listOf("d")),
        Subject("E", "Englisch", listOf("e")),
        Subject("F", "Französisch", listOf("f")),
        Subject("Sp", "Spanisch", listOf("esp", "sp")),
        Subject("L", "Latein", listOf("l")),
        Subject("I", "Italienisch", listOf("i")),
        Subject("BK", "Bildende Kunst", listOf("bk")),
        Subject("Mu", "Musik", listOf("mus", "mu")),
        Subject("G", "Geschichte", listOf("g")),
        Subject("Gk", "Gemeinschaftskunde", listOf("gk")),
        Subject("Geo", "Geographie", listOf("geo")),
        Subject("WBS", "Wirtschaft", listOf("wbs", "wi")),
        Subject("Rel", "Religion", listOf("kr", "er")),
        Subject("Eth", "Ethik", listOf("eth")),
        Subject("Phil", "Philosophie", listOf("phil")),
        Subject("M", "Mathematik", listOf("m")),
        Subject("Bio", "Biologie", listOf("bio")),
        Subject("Ph", "Physik", listOf("ph")),
        Subject("Ch", "Chemie", listOf("ch")),
        Subject("NwT", "NwT", listOf("nwt")),
        Subject("Inf", "Informatik", listOf("inf")),
        Subject("Sport", "Sport", listOf("s", "spo")),
        Subject("Psy", "Psychologie", listOf("psy")),
        Subject("Ast", "Astronomie", listOf("ast")),
        Subject("LTh", "Literatur und Theater", listOf("lth")),
    )

    fun subject(key: String): Subject? = subjects.firstOrNull { it.key.equals(key, ignoreCase = true) }

    /** Religion is printed as "kath. Religion" / "ev. Religion" depending on the course stem. */
    fun subjectName(key: String, code: String): String {
        if (key == "Rel") {
            when (CourseCode.of(code)?.stem) {
                "kr" -> return "kath. Religion"
                "er" -> return "ev. Religion"
            }
        }
        return subject(key)?.name ?: key
    }

    // ── Kollegium (school website, list 2024/25 plus the 2026/27 newcomers) ──────

    /** Untis teacher code → "Vorname Nachname". Codes not listed stay as they are. */
    val teachers: Map<String, String> = mapOf(
        "Sez" to "Ulrike Seitz", "Kle" to "Carsten Klering", "Nm" to "Ursula Neumann", "Ro" to "Dr. Daniel Roth",
        "Shn" to "Michael Schneider", "Bch" to "Thorid Bachmor", "Baz" to "Frank Balzer", "Btl" to "Kristina Bartl",
        "Bas" to "Sarha Basler", "Bau" to "Annemarie Bauer", "Bm" to "Katja Baumer", "Bet" to "Christiane Bernet",
        "Bie" to "Patricia Bieringer", "Blm" to "Judith Blum", "Bre" to "Dr. Birgit Breiding", "Brr" to "Simone Breier",
        "Brn" to "Richard Brenner", "Brd" to "Patricia Bruder", "Blb" to "Dr. Andrea Brucher-Lembach",
        "Bur" to "Johannes Burger", "Del" to "Julien Debailleul", "Dit" to "Georg Dittes", "Dom" to "Evamaria Domin",
        "Fei" to "Julia Feißt", "Ger" to "Annika Gerwien", "Gei" to "Selina Geist", "Glz" to "Juliane Glinz",
        "Hei" to "Matthias Heinz", "Hed" to "Dr. Marcus Held", "Hel" to "Marius Helfrich", "Hir" to "Patricia Hirt",
        "Hoe" to "Katja Hoeffer", "Hof" to "Katja Hoeffer", "Hld" to "Barbara Hold", "Hu" to "Andrea Hummel",
        "Hum" to "Andrea Hummel", "Jak" to "Antje Jakobi", "Kau" to "Corinna Kauth", "Kob" to "Andrea Koob",
        "Kp" to "Michael Kopp", "Ku" to "Marco Kubacki", "Kub" to "Marco Kubacki", "Len" to "Dr. Franziska Lenz",
        "Lev" to "Astrid Leven", "Lm" to "Katja Lohmann", "Loi" to "Gabriele Loida-Sengpiel", "Man" to "Jenny Manaia",
        "Mai" to "Simone Maier", "Meh" to "Sophie Mehne", "Mit" to "Christine Mittnacht", "Now" to "Laura Nowicki",
        "Oes" to "Isabel Oestreich", "Pie" to "Mareike Pietzsch", "Rhe" to "Hye-Rin Rhee-Dantscher",
        "Sa" to "Silke Sander", "Sdt" to "Anna-Benita Scheidt", "Scd" to "Heidi Schmid", "Shs" to "Michael Schnaus",
        "Shö" to "Christian Schröder", "Smi" to "Anja Smikale", "Stb" to "Peter Staub", "Ste" to "Simon Stein",
        "Stm" to "Maysun Stemler", "Stz" to "Helen Strotz", "Stü" to "Frank Stürmer", "Ung" to "Kai-Arwed Unger",
        "Vog" to "Katrin Vogel", "Vot" to "Sabine Vogt", "Web" to "Nathalie Weber", "Wes" to "Sarah Wenzel",
        "Zep" to "Ralph Zepfel", "Zil" to "Katrin Zilly", "Blu" to "Pia Blau", "Brkr" to "Laura Brenker",
        "OrJ" to "Anna Ormann-Jeserski", "Sir" to "Helen Schirdewahn", "Sloy" to "Luca Slotty",
        "HH-Es" to "Helmholtz-Gymnasium", "HH-es" to "Helmholtz-Gymnasium", "HH-BK" to "Helmholtz-Gymnasium",
        "HH-f" to "Helmholtz-Gymnasium",
    )

    fun teacherName(code: String): String? = teachers[code]

    /** "Dr. Birgit Breiding" → "Breiding", for cells naming several teachers. */
    fun lastName(code: String): String = teachers[code]?.split(" ")?.lastOrNull() ?: code
}

/** An Untis course code split into its parts: "M3" → stem "m", number 3, leistungsfach. */
data class CourseCode(
    val code: String,
    /** Lower-cased letters: "m", "kr", "esp". */
    val stem: String,
    val number: Int?,
    /** Untis writes Leistungsfach courses with a capital first letter ("M3", "Esp", "S1"). */
    val capitalised: Boolean,
) {
    companion object {
        private val PATTERN = Regex("([A-Za-zÄÖÜäöü]+)(\\d*)")

        fun of(code: String): CourseCode? {
            val m = PATTERN.matchEntire(code) ?: return null
            val letters = m.groupValues[1]
            return CourseCode(code, letters.lowercase(), m.groupValues[2].toIntOrNull(), letters.first().isUpperCase())
        }
    }
}
