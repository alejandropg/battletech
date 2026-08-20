package battletech.tactical.model.map

import battletech.tactical.io.ResourceOrFileLoader
import battletech.tactical.model.GameMap
import kotlinx.serialization.json.Json
import java.nio.file.Path

/** Default [Json] configuration for reading JSON map sources: strict about unknown keys. */
private val mapJson: Json = Json {
    ignoreUnknownKeys = false
    prettyPrint = true
}

/** Loads a [GameMap] from a compact JSON [MapFile] source. */
public class GameMapLoader(json: Json = mapJson) {

    private val loader = ResourceOrFileLoader(
        resourceDir = "map",
        label = "Map",
        json = json,
        build = { text, _ -> json.decodeFromString<MapFile>(text).toGameMap() },
        exception = ::MapLoadException,
    )

    /** Resolves [spec] as in [resolveMap] — see that function's KDoc. */
    internal fun resolve(spec: String): GameMap = loader.resolve(spec)

    /** Reads and parses the map file at [path], throwing [MapLoadException] on any failure. */
    public fun load(path: Path): GameMap = loader.load(path)

    /** Packaged map names from `map/index.json` — see [ResourceOrFileLoader.builtInNames]'s KDoc. */
    internal fun builtInNames(): List<String> = loader.builtInNames()
}

/** Raised when a JSON map source cannot be read or parsed into a valid [GameMap]. */
public class MapLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
