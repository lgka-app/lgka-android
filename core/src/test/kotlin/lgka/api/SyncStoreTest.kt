package lgka.api

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncStoreTest {
    private fun embed() = ApiJson.decodeFromString(SyncResponse.serializer(), Fixtures.text("sync_embed.json"))
    private fun fresh() = ApiJson.decodeFromString(SyncResponse.serializer(), Fixtures.text("sync_fresh.json"))

    @Test
    fun firstLaunchPersistsEverythingAndDecodesPdfs() {
        val store = SyncStore(Fixtures.tempDir())
        assertEquals(Resource.entries.associateWith { null }, store.hashes())

        val statuses = store.apply(embed())
        assertEquals(Resource.entries.associateWith { SyncStatus.Updated }, statuses)

        val subs = assertNotNull(store.load<Substitutions>(Resource.Substitutions))
        assertEquals(embed().resources["substitutions"]!!.hash, subs.hash)
        val today = assertNotNull(subs.data.today)
        // inline bytes were written to disk and stripped from the stored JSON
        val pdf = assertNotNull(store.pdfFile(today.pdf.sha256))
        assertTrue(pdf.readText().startsWith("%PDF-"))
        assertNull(today.pdf.base64)
        assertFalse(store.dir.resolve("substitutions.json").readText().contains("\"base64\""))

        val schedules = assertNotNull(store.load<Schedules>(Resource.Schedules))
        schedules.data.items.forEach { item -> assertNotNull(store.pdfFile(item.pdf!!.sha256), item.title) }
        assertNotNull(store.load<News>(Resource.News))
        assertNotNull(store.load<Events>(Resource.Events))
        assertNotNull(store.load<Weather>(Resource.Weather))
    }

    @Test
    fun freshKeepsLocalCopyAndHashes() {
        val store = SyncStore(Fixtures.tempDir())
        store.apply(embed())
        val before = store.hashes()
        val statuses = store.apply(fresh())
        assertEquals(Resource.entries.associateWith { SyncStatus.Fresh }, statuses)
        assertEquals(before, store.hashes())
        assertNotNull(store.load<News>(Resource.News))
    }

    @Test
    fun unavailableAndPartialResponsesLeaveStoreUntouched() {
        val store = SyncStore(Fixtures.tempDir())
        store.apply(embed())
        val newsHash = store.hash(Resource.News)
        val partial = SyncResponse(
            generatedAt = "now",
            resources = mapOf(
                "news" to SyncEntry(status = "unavailable"),
                "weather" to SyncEntry(status = "updated", hash = "deadbeef"), // no data → treated as unavailable
                "bogus" to SyncEntry(status = "updated", hash = "x"),
            ),
        )
        val statuses = store.apply(partial)
        assertEquals(mapOf(Resource.News to SyncStatus.Unavailable, Resource.Weather to SyncStatus.Unavailable), statuses)
        assertEquals(newsHash, store.hash(Resource.News))
        assertNotNull(store.load<Weather>(Resource.Weather))
    }

    @Test
    fun updatedResourceReplacesHashAndGarbageCollectsOldPdfs() {
        val store = SyncStore(Fixtures.tempDir())
        store.apply(embed())
        val old = store.load<Substitutions>(Resource.Substitutions)!!
        val oldSha = old.data.today!!.pdf.sha256
        assertNotNull(store.pdfFile(oldSha))

        // A new plan: same shape, new hash, new PDF hash, no inline bytes.
        val json = ApiJson.parseToJsonElement(Fixtures.text("substitutions.json")) as JsonObject
        val data = json["data"] as JsonObject
        val today = data["today"] as JsonObject
        val pdf = today["pdf"] as JsonObject
        val newSha = "f".repeat(64)
        val newData = JsonObject(data + ("today" to JsonObject(today + ("pdf" to JsonObject(pdf + ("sha256" to JsonPrimitive(newSha)))))))
        store.apply(SyncResponse("now", mapOf("substitutions" to SyncEntry("updated", "0123456789abcdef", "now", null, newData))))

        val now = store.load<Substitutions>(Resource.Substitutions)!!
        assertEquals("0123456789abcdef", now.hash)
        assertEquals(newSha, now.data.today!!.pdf.sha256)
        assertNull(store.pdfFile(newSha)) // not inline → fetched lazily by the app
        assertNull(store.pdfFile(oldSha)) // unreferenced → removed
        store.putPdf(newSha, "%PDF-1.4 lazily fetched".toByteArray())
        assertNotNull(store.pdfFile(newSha))
    }

    @Test
    fun pdfWritesGoThroughATempFileAndLeaveNoneBehind() {
        val store = SyncStore(Fixtures.tempDir())
        val sha = "e".repeat(64)
        // a write interrupted before its rename: the temp file must never be served as the PDF
        store.dir.resolve("$sha.pdf.tmp").writeText("%PDF-1.4 trunc")
        assertNull(store.pdfFile(sha))

        store.putPdf(sha, "%PDF-1.4 complete".toByteArray())
        assertEquals("%PDF-1.4 complete", store.pdfFile(sha)!!.readText())
        assertFalse(store.dir.resolve("$sha.pdf.tmp").exists())

        store.apply(embed())
        assertTrue(store.dir.listFiles()!!.none { it.name.endsWith(".tmp") })
        assertTrue(store.dir.resolve("pdf").listFiles()!!.all { it.name.endsWith(".pdf") })
    }

    @Test
    fun corruptRecordBehavesLikeNothingStored() {
        val store = SyncStore(Fixtures.tempDir())
        store.dir.resolve("events.json").writeText("{not json")
        assertNull(store.hash(Resource.Events))
        assertNull(store.load<Events>(Resource.Events))
    }

    @Test
    fun clearRemovesEverything() {
        val store = SyncStore(Fixtures.tempDir())
        store.apply(embed())
        store.clear()
        assertEquals(Resource.entries.associateWith { null }, store.hashes())
        assertTrue(store.dir.resolve("pdf").listFiles().isNullOrEmpty())
    }
}
