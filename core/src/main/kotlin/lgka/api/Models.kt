package lgka.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Wire models of api.lgka.app (see github.com/lgka-app/api). Every field the
 * server may omit or send as null is nullable or defaulted, so a payload
 * change never crashes the app — unknown keys are ignored.
 */
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
    coerceInputValues = true
}

/** The five synced resources, keyed exactly as the API names them. */
enum class Resource(val key: String) {
    Substitutions("substitutions"),
    Schedules("schedules"),
    News("news"),
    Events("events"),
    Weather("weather");

    companion object {
        fun of(key: String): Resource? = entries.firstOrNull { it.key == key }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> serializer(): KSerializer<T> = when (this) {
        Substitutions -> lgka.api.Substitutions.serializer()
        Schedules -> lgka.api.Schedules.serializer()
        News -> lgka.api.News.serializer()
        Events -> lgka.api.Events.serializer()
        Weather -> lgka.api.Weather.serializer()
    } as KSerializer<T>
}

// ── envelopes ────────────────────────────────────────────────────────────────

/** `GET /v1/<resource>` */
@Serializable
data class ResourceEnvelope(
    val name: String,
    val hash: String,
    val updatedAt: String,
    val sourceUpdatedAt: String? = null,
    val data: JsonElement,
)

/** `GET /v1/sync` */
@Serializable
data class SyncResponse(val generatedAt: String, val resources: Map<String, SyncEntry>)

@Serializable
data class SyncEntry(
    /** "fresh" | "updated" | "unavailable" */
    val status: String,
    val hash: String? = null,
    val updatedAt: String? = null,
    val sourceUpdatedAt: String? = null,
    val data: JsonElement? = null,
)

// ── files ────────────────────────────────────────────────────────────────────

@Serializable
data class PdfRef(
    /** `/v1/files/<sha256>.pdf` */
    val url: String,
    val sha256: String,
    val bytes: Long = 0,
    val pageCount: Int = 0,
    val sourceLastModified: String? = null,
    /** Present with `?embed=pdf`; stripped before the payload is persisted. */
    val base64: String? = null,
)

// ── substitutions ────────────────────────────────────────────────────────────

@Serializable
data class SubstitutionMeta(val weekday: String = "", val date: String = "", val lastUpdated: String = "")

@Serializable
data class SubstitutionEntry(
    val type: String? = null,
    val period: String? = null,
    val classes: List<String> = emptyList(),
    val classesRaw: String? = null,
    val substitute: String? = null,
    val subject: String? = null,
    val room: String? = null,
    val originalSubject: String? = null,
    val originalTeacher: String? = null,
    val originalRoom: String? = null,
    val note: String? = null,
    /** 0-based page of the PDF the entry starts on. */
    val page: Int = 0,
)

@Serializable
data class SubstitutionFooter(
    val untisPeriod: Int? = null,
    val date: String? = null,
    val calendarWeek: Int? = null,
    val schoolYearShort: String? = null,
)

@Serializable
data class SubstitutionPlan(
    val school: String? = null,
    val address: String? = null,
    val schoolYear: String? = null,
    val untisVersion: String? = null,
    val generatedAt: String? = null,
    val planDate: String? = null,
    val weekday: String? = null,
    val isEmpty: Boolean = false,
    val announcements: List<String> = emptyList(),
    val absentTeachers: List<String> = emptyList(),
    val absentClasses: List<String> = emptyList(),
    val blockedRooms: List<String> = emptyList(),
    val entries: List<SubstitutionEntry> = emptyList(),
    val footer: SubstitutionFooter? = null,
    val pageCount: Int = 0,
) {
    /** Entries affecting [className] ("6a" matches cells "6a", "6ab", "5c, 6a"). */
    fun entriesFor(className: String): List<SubstitutionEntry> {
        val lc = className.lowercase()
        return entries.filter { e -> e.classes.any { it.lowercase() == lc } }
    }
}

@Serializable
data class DayPlan(
    val source: String = "",
    val pdf: PdfRef,
    val sourceLastModified: String? = null,
    val meta: SubstitutionMeta = SubstitutionMeta(),
    val plan: SubstitutionPlan = SubstitutionPlan(),
    val pages: List<String> = emptyList(),
) {
    /** A plan worth opening: not the empty weekend export and dated. */
    val canDisplay: Boolean
        get() = !plan.isEmpty && meta.weekday.isNotEmpty() && meta.weekday != "weekend" && meta.date.isNotEmpty()
}

@Serializable
data class Substitutions(val today: DayPlan? = null, val tomorrow: DayPlan? = null)

// ── schedules ────────────────────────────────────────────────────────────────

@Serializable
data class ScheduleItem(
    val title: String,
    val url: String = "",
    val fullUrl: String = "",
    val halbjahr: String = "",
    /** "Klassen 5-10" | "J11" | "J12" | "J11/J12" | "Unbekannt" */
    val gradeLevel: String = "",
    val available: Boolean = false,
    val pdf: PdfRef? = null,
    /** class → real 1-based PDF page ("5a".."10e", "j11", "j12"). */
    val classIndex: Map<String, Int> = emptyMap(),
    /** Plain text per page, for search. */
    val pages: List<String> = emptyList(),
)

@Serializable
data class Schedules(val items: List<ScheduleItem> = emptyList())

// ── news ─────────────────────────────────────────────────────────────────────

@Serializable data class NewsLink(val text: String, val url: String)

@Serializable data class NewsImage(val url: String, val thumbnailUrl: String? = null, val alt: String? = null)

@Serializable data class NewsDownload(val title: String, val url: String, val fileType: String = "document", val size: String? = null)

@Serializable
data class NewsArticle(
    val id: Int? = null,
    val title: String,
    val author: String = "",
    val description: String = "",
    val createdDate: String = "",
    /** ISO-8601 when the server could parse the date. */
    val publishedAt: String? = null,
    val views: Int = 0,
    val url: String,
    val tags: List<String> = emptyList(),
    val content: String? = null,
    val htmlContent: String? = null,
    val links: List<NewsLink> = emptyList(),
    val standaloneLinks: List<NewsLink> = emptyList(),
    val images: List<NewsImage> = emptyList(),
    val downloads: List<NewsDownload> = emptyList(),
)

@Serializable
data class News(val articles: List<NewsArticle> = emptyList())

// ── events ───────────────────────────────────────────────────────────────────

@Serializable
data class SchoolEvent(
    /** YYYY-MM-DD */
    val date: String,
    /** HH:MM or null for all-day events. */
    val time: String? = null,
    val title: String,
)

@Serializable
data class Events(val events: List<SchoolEvent> = emptyList(), val horizonWeeks: Int = 3)
