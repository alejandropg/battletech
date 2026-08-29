package battletech.tactical.model.map

import battletech.tactical.model.GameMap
import java.nio.file.Path

/**
 * Maps available to a game definition: every packaged map plus external files registered at
 * startup. External maps are loaded eagerly so a bad registration fails before a match starts;
 * packaged maps remain lazy and are loaded only when a game selects one.
 */
public class GameMapCatalog private constructor(
    private val loader: GameMapLoader,
    private val builtInNames: Set<String>,
    private val externalMaps: Map<String, GameMap>,
) {

    /** Resolves [name] to its registered map, throwing [MapLoadException] when it is unknown. */
    public fun resolve(name: String): GameMap {
        externalMaps[name]?.let { return it }
        if (name !in builtInNames) {
            val available = (builtInNames + externalMaps.keys).sorted()
            val suffix = if (available.isEmpty()) "" else "\nAvailable maps: ${available.joinToString(", ")}"
            throw MapLoadException("Map not found in catalog: $name$suffix")
        }
        return loader.resolve(name)
    }

    public companion object {

        /**
         * Builds a catalog and registers every [externalPath]. A file's catalog name is its base
         * filename with one trailing `.json` removed. Names are exact and case-sensitive; neither
         * a built-in nor an earlier external registration may be replaced.
         */
        public fun load(
            externalPaths: List<Path> = emptyList(),
            loader: GameMapLoader = GameMapLoader(),
        ): GameMapCatalog {
            val builtIns = loader.builtInNames().toSet()
            val external = linkedMapOf<String, GameMap>()

            for (path in externalPaths) {
                val filename = path.fileName?.toString().orEmpty()
                val name = filename.removeSuffix(".json")
                if (name.isBlank()) {
                    throw MapLoadException("External map filename must provide a catalog name: $path")
                }
                if (name in builtIns) {
                    throw MapLoadException("External map '$path' conflicts with built-in map '$name'")
                }
                if (name in external) {
                    throw MapLoadException("External map name '$name' is registered more than once")
                }

                external[name] = loader.load(path).copy(name = name)
            }

            return GameMapCatalog(loader, builtIns, external)
        }
    }
}
