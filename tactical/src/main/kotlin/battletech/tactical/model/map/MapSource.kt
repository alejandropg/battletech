package battletech.tactical.model.map

import battletech.tactical.model.GameMap

/** Built-in map used when a caller does not specify a map source. */
public const val DEFAULT_MAP_NAME: String = "battletech-classic"

/**
 * Resolves a map [spec] to a [GameMap]. If [spec] identifies an existing filesystem path, that
 * source is authoritative and is loaded via [loader]. Otherwise, [spec] is treated as an
 * extensionless packaged map name and `map/<spec>.json` is loaded from the classpath.
 *
 * Throws [MapLoadException] when the selected path cannot be read or parsed, or when the packaged
 * resource is missing, malformed, or semantically invalid. An existing path's failure never falls
 * back to a packaged resource.
 */
public fun resolveMap(spec: String, loader: GameMapLoader = GameMapLoader()): GameMap = loader.resolve(spec)

/** Outcome of [compareWithLocalMap]: how a host-supplied [GameMap] compares to a local map source of the same name. */
public enum class LocalMapMatch { MATCHES, DIFFERS, UNAVAILABLE }

/**
 * Compares [hostMap] against a local map source named [hostMap.name][GameMap.name], resolved via
 * [loader]. The host's map is authoritative regardless of the outcome — this is purely informational,
 * for a client to warn its player when its own copy of a built-in or file-based map has drifted from
 * the host's. [LocalMapMatch.UNAVAILABLE] covers the ordinary case of a remote joiner with no local
 * map of that name at all, not just a genuinely broken one.
 */
public fun compareWithLocalMap(hostMap: GameMap, loader: GameMapLoader = GameMapLoader()): LocalMapMatch =
    try {
        if (loader.resolve(hostMap.name).hexes == hostMap.hexes) LocalMapMatch.MATCHES else LocalMapMatch.DIFFERS
    } catch (e: MapLoadException) {
        LocalMapMatch.UNAVAILABLE
    }
