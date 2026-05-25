# Scoova Geofences — Android / JVM

Kotlin client for the Scoova geofences API at
`https://api.scoo-va.info/v1/geofences/*`.

Stored named polygons plus point-in-polygon containment checks. Useful for
service-area gating, no-parking zones, depot perimeters, school zones,
congestion zones — anything where a location needs to be tested against a
set of regions.

## Install

### GitHub Packages / Maven Central

```kotlin
dependencies {
    implementation("info.scoo-va:scoova-geofences-android:1.0.3")
}
```

## Quick start

```kotlin
import com.scoova.geofences.GeofencesClient
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import kotlinx.coroutines.runBlocking

val client = GeofencesClient(apiKey = System.getenv("SCOOVA_API_KEY"))

runBlocking {
    // Save a polygon
    val polygon = buildJsonObject {
        put("type", "Polygon")
        put("coordinates", buildJsonArray {
            add(buildJsonArray {
                listOf(
                    -74.020 to 40.700, -73.910 to 40.700,
                    -73.910 to 40.880, -74.020 to 40.880,
                    -74.020 to 40.700,
                ).forEach { (lon, lat) ->
                    add(buildJsonArray { add(lon); add(lat) })
                }
            })
        })
    }
    val created = client.create(name = "Manhattan service area", geometry = polygon)

    // List
    val all = client.list()

    // Point-in-polygon
    val result = client.check(40.748, -73.985)
    println(result.inside.map { it.name }) // [Manhattan service area]

    // Delete
    client.delete(created.id)
}
```

## Methods

| Method | Description |
| --- | --- |
| `list()` | Every geofence on the account. |
| `get(id)` | One geofence by id, with full geometry. |
| `create(name, geometry)` | Save a new geofence. Returns the full record. |
| `delete(id)` (alias: `remove(id)`) | Remove one. |
| `check(lat, lon)` | Returns every geofence whose polygon contains the point. |

Every method accepts an optional `locale` parameter to override the
client-default locale for that call.

## Locale

```kotlin
val client = GeofencesClient(apiKey = "…", locale = "fr")
```

Sent as `?locale=fr` and `Accept-Language: fr`. Per-call `locale` overrides
the client default.

Accepted codes: `en`, `en-US`, `en-GB`, `en-CA`, `fr`, `es`, `de`, `it`,
`pt-BR`, `nl`, plus their regional variants.

## Errors

```kotlin
try {
    client.get("does-not-exist")
} catch (e: GeofencesException) {
    println(e.status)  // 404
    println(e.code)    // NOT_FOUND
}
```

## License

Apache-2.0. Copyright 2026 Scoova.
