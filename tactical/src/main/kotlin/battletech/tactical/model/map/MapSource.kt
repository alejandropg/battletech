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
