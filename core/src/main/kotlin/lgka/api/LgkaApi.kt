package lgka.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The stored credentials are wrong or the school rotated the password. */
class UnauthorizedException : IOException("unauthorized")

/** Any non-success status other than 401. */
class ApiStatusException(val status: Int, url: String) : IOException("HTTP $status for $url")

data class Login(val user: String, val password: String) {
    val header: String get() = Credentials.basic(user, password, Charsets.UTF_8)
}

/** Which mirrored PDFs the server inlines (base64) into a payload. The app asks for all of them: one request, everything offline. */
enum class Embed(val query: String?) {
    /** No inline PDFs; fetch them via `/v1/files/<sha>.pdf` on demand. */
    None(null),
    /** Only the two substitution plans. */
    SubstitutionsPdf("substitutions.pdf"),
    /** Every PDF the response references (default). */
    AllPdf("pdf"),
}

/** The school website: the only host the in-app browser may answer a Basic Auth challenge for. */
const val SCHOOL_HOST = "lessing-gymnasium-karlsruhe.de"

/** True only for the school host itself or one of its subdomains — never for anything else. */
fun isSchoolHost(host: String?): Boolean =
    host != null && (host.equals(SCHOOL_HOST, ignoreCase = true) || host.endsWith(".$SCHOOL_HOST", ignoreCase = true))

/**
 * HTTP client for api.lgka.app. One `sync` call per launch/resume carries the
 * hashes the device already has; the server answers per resource with
 * `fresh` (no payload), `updated` (payload inline) or `unavailable`.
 *
 * Privacy: Basic Auth with the school's shared credentials, nothing else —
 * no identifiers, no cookies. OkHttp adds `Accept-Encoding: gzip` itself.
 */
class LgkaApi(
    userAgent: String,
    baseUrl: String = DEFAULT_BASE_URL,
    client: OkHttpClient? = null,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.lgka.app"
    }

    private val base: HttpUrl = baseUrl.toHttpUrl()
    private val http: OkHttpClient = (client ?: OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build())
        .newBuilder()
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
        }
        .build()

    /**
     * Onboarding gate: true for 2xx, false ONLY for 401. A 403 (WAF, rate
     * limit), 429 or 5xx is a transient [ApiStatusException], not a wrong password.
     */
    suspend fun checkCredentials(login: Login): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(base.resolve("/v1/auth/check")!!).header("Authorization", login.header).build()
        http.newCall(req).execute().use { res ->
            when (res.code) {
                in 200..299 -> true
                401 -> false
                else -> throw ApiStatusException(res.code, req.url.toString())
            }
        }
    }

    /**
     * A data route answered 401. Before treating that as a rotated password,
     * confirm with one `/v1/auth/check`: true only when the check also says 401.
     * A 204 or any error (network, 403, 5xx) means "transient" → keep the session.
     */
    suspend fun confirmUnauthorized(login: Login): Boolean = try {
        !checkCredentials(login)
    } catch (e: IOException) {
        false
    }

    /**
     * `GET /v1/sync?<resource>=<hash>&…[&only=a,b][&embed=pdf]`
     * @param hashes the hash per resource the device holds (null/empty → always receive data)
     */
    suspend fun sync(
        login: Login,
        hashes: Map<Resource, String?>,
        only: Set<Resource>? = null,
        embed: Embed = Embed.AllPdf,
    ): SyncResponse = withContext(Dispatchers.IO) {
        val url = base.resolve("/v1/sync")!!.newBuilder().apply {
            for (r in Resource.entries) addQueryParameter(r.key, hashes[r] ?: "")
            if (only != null) addQueryParameter("only", only.joinToString(",") { it.key })
            embed.query?.let { addQueryParameter("embed", it) }
        }.build()
        val body = getString(url, login)
        ApiJson.decodeFromString(SyncResponse.serializer(), body)
    }

    /** A mirrored PDF by its API path (`/v1/files/<sha>.pdf`) — fallback when a payload had no inline bytes. */
    suspend fun file(login: Login, path: String): ByteArray = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(base.resolve(path)!!).header("Authorization", login.header).build()
        http.newCall(req).execute().use { res ->
            check(res, req)
            res.body.bytes()
        }
    }

    private fun getString(url: HttpUrl, login: Login): String {
        val req = Request.Builder().url(url).header("Authorization", login.header).header("Accept", "application/json").build()
        return http.newCall(req).execute().use { res ->
            check(res, req)
            res.body.string()
        }
    }

    private fun check(res: okhttp3.Response, req: Request) {
        if (res.code == 401) throw UnauthorizedException()
        if (!res.isSuccessful) throw ApiStatusException(res.code, req.url.toString())
    }
}
