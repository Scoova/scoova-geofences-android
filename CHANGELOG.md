# Changelog

All notable changes to `info.scoo-va:geofences` are documented here.

## 1.0.0 — 2026-05-25

Initial release.

- `GeofencesClient` — `list`, `get`, `create`, `delete` (aliased as `remove`), `check`
- `GeofencesException` with `status` + structured gateway `code`
- Locale support via `?locale=` and `Accept-Language`, both client-default and per-call
- API key from constructor, falling back to `SCOOVA_API_KEY` env, falling back to `'demo'`
- OkHttp transport with `withContext(Dispatchers.IO)` coroutine bridging
- kotlinx.serialization for typed `Geofence` / `GeofenceCheckResult` decoding
- JVM 17 target
