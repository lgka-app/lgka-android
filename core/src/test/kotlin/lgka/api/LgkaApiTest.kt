package lgka.api

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LgkaApiTest {
    @StartStop
    private val server = MockWebServer()

    private val login = Login("vertretungsplan", "secret")
    private fun api() = LgkaApi(userAgent = "LGKA+/test", baseUrl = server.url("/").toString())

    @Test
    fun authCheckMapsStatusCodes() = runTest {
        server.enqueue(MockResponse(code = 204))
        server.enqueue(MockResponse(code = 401))
        assertTrue(api().checkCredentials(login))
        assertFalse(api().checkCredentials(login))
        val req = server.takeRequest()
        assertEquals("/v1/auth/check", req.url.encodedPath)
        assertEquals("Basic dmVydHJldHVuZ3NwbGFuOnNlY3JldA==", req.headers["Authorization"])
        assertEquals("LGKA+/test", req.headers["User-Agent"])
    }

    @Test
    fun authCheckTreatsOnly401AsWrongPassword() = runTest {
        // 403 = WAF / rate limit, 429, 5xx: the service, not the password
        for (code in listOf(403, 429, 503)) {
            server.enqueue(MockResponse(code = code))
            val e = assertFailsWith<ApiStatusException> { api().checkCredentials(login) }
            assertEquals(code, e.status)
        }
    }

    @Test
    fun a401OnSyncIsConfirmedBeforeItCounts() = runTest {
        // sync 401, auth/check 204 → transient, keep the session
        server.enqueue(MockResponse(code = 401))
        server.enqueue(MockResponse(code = 204))
        val api = api()
        assertFailsWith<UnauthorizedException> { api.sync(login, emptyMap()) }
        assertFalse(api.confirmUnauthorized(login))
        server.takeRequest()
        assertEquals("/v1/auth/check", server.takeRequest().url.encodedPath)

        // sync 401, auth/check 401 → really rotated
        server.enqueue(MockResponse(code = 401))
        server.enqueue(MockResponse(code = 401))
        assertFailsWith<UnauthorizedException> { api.sync(login, emptyMap()) }
        assertTrue(api.confirmUnauthorized(login))

        // sync 401, auth/check 403 (edge) → not a credential verdict
        server.enqueue(MockResponse(code = 401))
        server.enqueue(MockResponse(code = 403))
        assertFailsWith<UnauthorizedException> { api.sync(login, emptyMap()) }
        assertFalse(api.confirmUnauthorized(login))
    }

    @Test
    fun onlyTheSchoolHostGetsTheBasicAuthAnswer() {
        assertTrue(isSchoolHost("lessing-gymnasium-karlsruhe.de"))
        assertTrue(isSchoolHost("www.lessing-gymnasium-karlsruhe.de"))
        assertTrue(isSchoolHost("LESSING-GYMNASIUM-KARLSRUHE.DE"))
        assertFalse(isSchoolHost("lessing-gymnasium-karlsruhe.de.evil.example"))
        assertFalse(isSchoolHost("notlessing-gymnasium-karlsruhe.de"))
        assertFalse(isSchoolHost("api.lgka.app"))
        assertFalse(isSchoolHost(null))
    }

    @Test
    fun syncSendsHashesOnlyAndEmbed() = runTest {
        server.enqueue(MockResponse(code = 200, body = Fixtures.text("sync_fresh.json")))
        val response = api().sync(
            login,
            hashes = mapOf(Resource.News to "abc", Resource.Weather to null),
            only = setOf(Resource.News, Resource.Weather),
        )
        assertEquals(5, response.resources.size)
        val url = server.takeRequest().url
        assertEquals("/v1/sync", url.encodedPath)
        assertEquals("abc", url.queryParameter("news"))
        assertEquals("", url.queryParameter("weather"))
        assertEquals("", url.queryParameter("substitutions"))
        assertEquals("news,weather", url.queryParameter("only"))
        assertEquals("pdf", url.queryParameter("embed"))
    }

    @Test
    fun embedModesMapToTheQuery() = runTest {
        server.enqueue(MockResponse(code = 200, body = Fixtures.text("sync_fresh.json")))
        server.enqueue(MockResponse(code = 200, body = Fixtures.text("sync_fresh.json")))
        api().sync(login, emptyMap(), embed = Embed.None)
        assertNull(server.takeRequest().url.queryParameter("embed"))
        api().sync(login, emptyMap(), embed = Embed.SubstitutionsPdf)
        assertEquals("substitutions.pdf", server.takeRequest().url.queryParameter("embed"))
    }

    @Test
    fun unauthorizedOnDataRouteIsTyped() = runTest {
        server.enqueue(MockResponse(code = 401, body = """{"error":"unauthorized"}"""))
        assertFailsWith<UnauthorizedException> { api().sync(login, emptyMap()) }
    }

    @Test
    fun otherErrorsCarryTheStatus() = runTest {
        server.enqueue(MockResponse(code = 503, body = """{"error":"unavailable"}"""))
        val e = assertFailsWith<ApiStatusException> { api().sync(login, emptyMap()) }
        assertEquals(503, e.status)
    }

    @Test
    fun fileDownloadReturnsBytes() = runTest {
        server.enqueue(MockResponse(code = 200, body = "%PDF-1.4 bytes"))
        val bytes = api().file(login, "/v1/files/${"a".repeat(64)}.pdf")
        assertEquals("%PDF-1.4 bytes", String(bytes))
        assertNotNull(server.takeRequest().headers["Authorization"])
    }
}
