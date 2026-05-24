package com.scoova.geofences

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** A point on the WGS84 ellipsoid. */
@Serializable
data class LatLon(val lat: Double, val lon: Double)

/**
 * A stored geofence on the account. `geometry` is a raw GeoJSON `Polygon` or
 * `MultiPolygon` — we keep it as a `JsonObject` so callers aren't forced
 * through a constrained type for legitimate exotic shapes.
 */
@Serializable
data class Geofence(
    val id: String,
    val name: String,
    val geometry: JsonObject,
    val createdAt: Long? = null,
)

/** Reference to a geofence (id + name) without the heavy geometry payload. */
@Serializable
data class GeofenceRef(val id: String, val name: String)

/** Result of a containment check. `inside` is empty if no fences match. */
@Serializable
data class GeofenceCheckResult(
    val point: LatLon,
    val inside: List<GeofenceRef>,
)

/** Server response shape on a create call. */
@Serializable
internal data class GeofenceCreated(
    val id: String,
    val name: String,
    val createdAt: Long,
)

/** Generic envelope for the `{success, data, error?, code?}` gateway shape. */
@Serializable
internal data class Envelope<T>(
    val success: Boolean = true,
    val data: T? = null,
    val error: String? = null,
    val code: String? = null,
)

@Serializable
internal data class CheckRequest(val lat: Double, val lon: Double)

@Serializable
internal data class CreateRequest(val name: String, val geometry: JsonObject)

/** Thrown on any non-2xx response from the gateway. */
class GeofencesException(
    val status: Int,
    @Suppress("MemberVisibilityCanBePrivate") val code: String?,
    message: String,
) : RuntimeException(message) {
    override fun toString(): String =
        "GeofencesException(status=$status, code=${code ?: "—"}, message=${message ?: ""})"
}
