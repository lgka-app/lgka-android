package lgka.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.Base64

/** What the device holds for one resource. */
data class Stored<T>(val hash: String, val updatedAt: String, val sourceUpdatedAt: String?, val data: T)

enum class SyncStatus { Fresh, Updated, Unavailable }

/**
 * On-disk copy of every resource plus the mirrored PDFs, so the app renders
 * instantly and works offline. Layout inside [dir]:
 *
 *   <resource>.json   {hash, updatedAt, sourceUpdatedAt, data}  (PDF base64 stripped)
 *   pdf/<sha256>.pdf  decoded from `pdf.base64` of updated payloads
 *
 * Not thread-safe by itself; the app calls it from one coroutine at a time.
 */
class SyncStore(val dir: File) {
    private val pdfDir = File(dir, "pdf")

    init {
        pdfDir.mkdirs()
    }

    @Serializable
    private data class Record(val hash: String, val updatedAt: String, val sourceUpdatedAt: String? = null, val data: JsonElement)

    private fun file(r: Resource) = File(dir, "${r.key}.json")

    fun hash(r: Resource): String? = readRecord(r)?.hash

    /** Hashes for the sync query (null where nothing is stored). */
    fun hashes(): Map<Resource, String?> = Resource.entries.associateWith { hash(it) }

    fun <T> load(r: Resource): Stored<T>? {
        val rec = readRecord(r) ?: return null
        val data = try {
            ApiJson.decodeFromJsonElement(r.serializer<T>(), rec.data)
        } catch (_: Exception) {
            return null // stale/incompatible payload: behave like "nothing stored"
        }
        return Stored(rec.hash, rec.updatedAt, rec.sourceUpdatedAt, data)
    }

    /** The mirrored PDF for a content hash, or null when not on disk. */
    fun pdfFile(sha256: String): File? = File(pdfDir, "$sha256.pdf").takeIf { it.length() > 0 }

    /** Stores PDF bytes fetched separately (payload without inline bytes). */
    fun putPdf(sha256: String, bytes: ByteArray): File = File(pdfDir, "$sha256.pdf").also { it.writeBytes(bytes) }

    /**
     * Applies a sync response: `updated` payloads are persisted (PDFs decoded to
     * files), `fresh` and `unavailable` leave the stored copy untouched.
     */
    fun apply(response: SyncResponse): Map<Resource, SyncStatus> {
        val out = LinkedHashMap<Resource, SyncStatus>()
        for ((key, entry) in response.resources) {
            val r = Resource.of(key) ?: continue
            out[r] = when (entry.status) {
                "updated" -> {
                    val hash = entry.hash
                    val data = entry.data
                    if (hash != null && data != null) {
                        put(r, hash, entry.updatedAt ?: "", entry.sourceUpdatedAt, data)
                        SyncStatus.Updated
                    } else SyncStatus.Unavailable
                }
                "fresh" -> SyncStatus.Fresh
                else -> SyncStatus.Unavailable
            }
        }
        gcPdfs()
        return out
    }

    /** Persists one full resource envelope (`GET /v1/<resource>`). */
    fun apply(r: Resource, envelope: ResourceEnvelope) {
        put(r, envelope.hash, envelope.updatedAt, envelope.sourceUpdatedAt, envelope.data)
        gcPdfs()
    }

    fun clear() {
        dir.listFiles()?.forEach { it.deleteRecursively() }
        pdfDir.mkdirs()
    }

    // ── internals ────────────────────────────────────────────────────────────

    private fun put(r: Resource, hash: String, updatedAt: String, sourceUpdatedAt: String?, data: JsonElement) {
        val stripped = extractPdfs(data)
        val rec = Record(hash, updatedAt, sourceUpdatedAt, stripped)
        val target = file(r)
        val tmp = File(dir, "${r.key}.json.tmp")
        tmp.writeText(ApiJson.encodeToString(Record.serializer(), rec))
        if (!tmp.renameTo(target)) {
            target.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun readRecord(r: Resource): Record? {
        val f = file(r)
        if (!f.exists()) return null
        return try {
            ApiJson.decodeFromString(Record.serializer(), f.readText())
        } catch (_: Exception) {
            null
        }
    }

    /** Writes every `{sha256, base64}` PDF reference to disk and drops the base64 from the tree. */
    private fun extractPdfs(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> {
            val isPdfRef = el.containsKey("sha256") && el.containsKey("base64")
            if (isPdfRef) {
                val sha = el["sha256"]!!.jsonPrimitive.content
                val b64 = el["base64"]
                if (b64 is JsonPrimitive && b64.isString) {
                    runCatching { Base64.getDecoder().decode(b64.content) }.getOrNull()?.let { bytes ->
                        val f = File(pdfDir, "$sha.pdf")
                        if (f.length() != bytes.size.toLong()) f.writeBytes(bytes)
                    }
                }
            }
            JsonObject(el.entries.filter { !(isPdfRef && it.key == "base64") }.associate { it.key to extractPdfs(it.value) })
        }
        is JsonArray -> JsonArray(el.map { extractPdfs(it) })
        else -> el
    }

    /** Deletes PDFs no stored resource references any more. */
    private fun gcPdfs() {
        val referenced = HashSet<String>()
        for (r in Resource.entries) readRecord(r)?.data?.let { collectShas(it, referenced) }
        pdfDir.listFiles()?.forEach { f ->
            val sha = f.name.removeSuffix(".pdf")
            if (sha !in referenced) f.delete()
        }
    }

    private fun collectShas(el: JsonElement, into: MutableSet<String>) {
        when (el) {
            is JsonObject -> {
                if (el.containsKey("sha256") && el.containsKey("url")) {
                    runCatching { into.add(el["sha256"]!!.jsonPrimitive.content) }
                }
                el.values.forEach { collectShas(it, into) }
            }
            is JsonArray -> el.forEach { collectShas(it, into) }
            else -> {}
        }
    }
}

/** Convenience: the data object of a JSON envelope element, if any. */
fun JsonElement.dataObject(): JsonObject? = (this as? JsonObject)?.get("data")?.jsonObject
