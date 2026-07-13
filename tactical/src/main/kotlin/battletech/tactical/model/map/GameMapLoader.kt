package battletech.tactical.model.map

import battletech.tactical.model.GameMap
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.readText

/** Default [Json] configuration for reading map files: strict about unknown keys. */
private val mapJson: Json = Json {
    ignoreUnknownKeys = false
    prettyPrint = true
}

/** Loads a [GameMap] from a compact JSON [MapFile] on disk. */
public class GameMapLoader(private val json: Json = mapJson) {

    /** Reads and parses the map file at [path], throwing [MapLoadException] on any failure. */
    public fun load(path: Path): GameMap {
        val text = try {
            path.readText()
        } catch (e: NoSuchFileException) {
            throw MapLoadException("Map file not found: $path", e)
        } catch (e: IOException) {
            throw MapLoadException("Failed to read map file: $path", e)
        }

        val mapFile = try {
            json.decodeFromString<MapFile>(text)
        } catch (e: SerializationException) {
            throw MapLoadException("Malformed map file: $path", e)
        }

        return mapFile.toGameMap()
    }
}

/** Raised when a map file cannot be read or parsed into a valid [GameMap]. */
public class MapLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
