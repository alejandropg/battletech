package battletech.tactical.model.map

import battletech.tactical.model.GameMap
import kotlin.io.path.Path

/**
 * Resolves a `--map` spec to a [GameMap]: a built-in id (see [MapCatalog.ids]) wins, otherwise
 * [spec] is treated as a file path and loaded via [loader]. Throws [MapLoadException] on a bad path/file.
 */
public fun resolveMap(spec: String, loader: GameMapLoader = GameMapLoader()): GameMap =
    MapCatalog[spec] ?: loader.load(Path(spec))
