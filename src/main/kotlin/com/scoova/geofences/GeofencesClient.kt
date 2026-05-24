package com.scoova.geofences

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Standalone client for the Scoova geofences API at
 * `https://api.scoo-va.info/api/v1/geofences`.
 *
 * Stored named polygons plus point-in-polygon containment checks. Useful for
 * service-area gating, no-parking zones, depot perimeters, school zones,
 * congestion zones — anything where a location needs to be tested against a
 * set of regions.
 *
 *     val client = GeofencesClient(apiKey = System.getenv("SCOOVA_API_KEY"))
 *     val all = client.list()
 *     val result = client.check(40.748, -73.985)
 *
 * All methods are `suspend` and throw [GeofencesException] on non-2xx with
 * the gateway's structured `code` (e.g. `NOT_FOUND`, `KEY_RESTRICTED`).
 */
class GeofencesClient(
    apiKey: String? = null,
    baseUrl: String = DEFAULT_BASE,
    /** Lock the SDK to a specific Android package (matches the `android:` key restriction). */
    private val androidPackage: String? = null,
    /**
     * Default locale (BCP-47, e.g. `en`, `fr`, `pt-BR`). Sent as `?locale=`
     * on every request and `Accept-Language` on the headers. Per-call `locale`
     * overrides this default.
     */
    private val locale: String? = null,
    /** Provide your own client for proxies, logging, custom timeouts. */
    httpClient: OkHttpClient? = null,
) {
    companion object {
        const val DEFAULT_BASE: String = "https://api.scoo-va.info/api/v1"
    }

    private val apiKey: String = run {
        val explicit = apiKey?.takeIf { it.isNotBlank() }
        val env = System.getenv("SCOOVA_API_KEY")?.takeIf { it.isNotBlank() }
        explicit ?: env ?: "demo"
    }

    private val baseUrl: String = baseUrl.trimEnd('/')

    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = false
    }
    private val jsonMedia = "application/json".toMediaType()
    private val http: OkHttpClient = httpClient ?: OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    // ─── Public API ──────────────────────────────────────────────────────

    /** Every geofence stored on the account. */
    suspend fun list(locale: String? = null): List<Geofence> {
        val raw = getJson("/geofences", locale)
        val env: Envelope<List<Geofence>> = json.decodeFromString(
            Envelope.serializer(ListSerializer(Geofence.serializer())),
            raw,
        )
        return env.data ?: emptyList()
    }

    /**
     * Look up one by id. Throws [GeofencesException] with
     * status=404, code=`NOT_FOUND` if the id doesn't exist.
     */
    suspend fun get(id: String, locale: String? = null): Geofence {
        require(id.isNotBlank()) { "GeofencesClient.get: id is required" }
        val raw = getJson("/geofences/$id", locale)
        val env: Envelope<Geofence> = json.decodeFromString(
            Envelope.serializer(Geofence.serializer()),
            raw,
        )
        return env.data ?: throw GeofencesException(404, "NOT_FOUND", "Geofence $id not found")
    }

    /**
     * Store a new geofence. `geometry` must be a GeoJSON `Polygon` or
     * `MultiPolygon`. The server returns `{id, name, createdAt}` only —
     * this method re-fetches the full record so callers always get a
     * complete [Geofence] back.
     */
    suspend fun create(name: String, geometry: JsonObject, locale: String? = null): Geofence {
        require(name.isNotBlank()) { "GeofencesClient.create: name is required" }
        val body = json.encodeToString(CreateRequest.serializer(), CreateRequest(name, geometry))
        val raw = postJson("/geofences", body, locale)
        val env: Envelope<GeofenceCreated> = json.decodeFromString(
            Envelope.serializer(GeofenceCreated.serializer()),
            raw,
        )
        val created = env.data
            ?: throw GeofencesException(500, null, "create response missing data")
        return get(created.id, locale)
    }

    /** Remove a geofence. No-op success on the server if it already didn't exist. */
    suspend fun delete(id: String, locale: String? = null) {
        require(id.isNotBlank()) { "GeofencesClient.delete: id is required" }
        deleteJson("/geofences/$id", locale)
    }

    /** Alias of [delete] — matches the Flutter SDK's `remove()` naming. */
    suspend fun remove(id: String, locale: String? = null) = delete(id, locale)

    /**
     * Returns every geofence on this account whose polygon contains the
     * supplied point. `inside` is empty if none match.
     */
    suspend fun check(lat: Double, lon: Double, locale: String? = null): GeofenceCheckResult {
        val body = json.encodeToString(CheckRequest.serializer(), CheckRequest(lat, lon))
        val raw = postJson("/geofences/check", body, locale)
        val env: Envelope<GeofenceCheckResult> = json.decodeFromString(
            Envelope.serializer(GeofenceCheckResult.serializer()),
            raw,
        )
        return env.data ?: GeofenceCheckResult(LatLon(lat, lon), emptyList())
    }

    // ─── HTTP plumbing ───────────────────────────────────────────────────

    private fun url(path: String, perCallLocale: String?): okhttp3.HttpUrl {
        val full = if (path.startsWith("/")) "$baseUrl$path" else "$baseUrl/$path"
        val b = full.toHttpUrl().newBuilder()
        val loc = perCallLocale ?: locale
        if (loc != null) b.addQueryParameter("locale", loc)
        return b.build()
    }

    private fun buildRequest(
        url: okhttp3.HttpUrl,
        perCallLocale: String?,
        configure: Request.Builder.() -> Unit,
    ): Request {
        val builder = Request.Builder().url(url)
            .header("X-API-Key", apiKey)
        androidPackage?.let { builder.header("X-Android-Package", it) }
        val loc = perCallLocale ?: locale
        loc?.let { builder.header("Accept-Language", it) }
        // Caller configures method + body LAST so it isn't shadowed.
        configure(builder)
        return builder.build()
    }

    private suspend fun getJson(path: String, perCallLocale: String?): String =
        execute(buildRequest(url(path, perCallLocale), perCallLocale) { get() })

    private suspend fun postJson(path: String, body: String, perCallLocale: String?): String =
        execute(buildRequest(url(path, perCallLocale), perCallLocale) {
            post(body.toRequestBody(jsonMedia))
        })

    private suspend fun deleteJson(path: String, perCallLocale: String?) {
        execute(buildRequest(url(path, perCallLocale), perCallLocale) { delete() })
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) raiseError(r.code, text, r.message)
            text
        }
    }

    private fun raiseError(status: Int, body: String, fallback: String): Nothing {
        val (code, message) = try {
            val el = json.parseToJsonElement(body)
            if (el is JsonObject) {
                el["code"]?.jsonPrimitive?.contentOrNull to el["error"]?.jsonPrimitive?.contentOrNull
            } else null to null
        } catch (_: Exception) {
            null to null
        }
        throw GeofencesException(status, code, message ?: fallback)
    }
}
