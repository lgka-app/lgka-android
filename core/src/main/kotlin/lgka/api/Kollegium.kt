package lgka.api

import kotlinx.serialization.Serializable

/** `/v1/kollegium`: the staff list of the school website, refreshed by the API once a day. */
@Serializable
data class Kollegium(
    /** When the list last changed. */
    val updatedAt: String? = null,
    val source: String? = null,
    /** "2024/2025" as stated on the page; null when it states none. */
    val schoolYear: String? = null,
    val staff: List<Staff> = emptyList(),
) {
    @Serializable
    data class Staff(
        /** Untis code as printed in the timetables ("Ro"). */
        val code: String,
        val lastName: String,
        val firstName: String? = null,
        /** "Dr." */
        val title: String? = null,
        /** "Dr. Daniel Roth" */
        val displayName: String,
        val subjects: List<String> = emptyList(),
        /** schulleitung, stellvertretendeSchulleitung, abteilungsleitung, lehrkraft, referendar, sonstige */
        val role: String = "sonstige",
        /** The page's heading, "Abteilungsleiter". */
        val roleLabel: String? = null,
        /** Every heading the person is listed under. */
        val roleLabels: List<String> = emptyList(),
    )
}

/**
 * The teachers the app knows by their Untis code, filled from the synced staff list. A code the list
 * doesn't contain (a new teacher before the next daily refresh) is shown as the code itself.
 */
object TeacherDirectory {
    @Volatile
    private var byCode: Map<String, Kollegium.Staff> = emptyMap()

    fun update(staff: List<Kollegium.Staff>) {
        // the first entry wins for a code listed twice
        byCode = staff.reversed().associateBy { it.code }
    }

    fun staff(code: String): Kollegium.Staff? = byCode[code]

    /** "Dr. Daniel Roth", or null for an unknown code. */
    fun name(code: String): String? = staff(code)?.displayName

    /** "Roth", for cells naming several teachers; the code for an unknown one. */
    fun lastName(code: String): String = staff(code)?.lastName ?: code
}
