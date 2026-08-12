package battletech.tactical.model.map

import battletech.tactical.model.GameMap
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.readText

/** Default [Json] configuration for reading JSON map sources: strict about unknown keys. */
private val mapJson: Json = Json {
    ignoreUnknownKeys = false
    prettyPrint = true
}

/** Loads a [GameMap] from a compact JSON [MapFile] source. */
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

        return decode(text, "Malformed map file: $path")
    }

    /** Reads and parses the classpath resource at [resourcePath]. */
    internal fun loadResource(resourcePath: String): GameMap {
        val text = try {
            val stream = GameMapLoader::class.java.getResourceAsStream("/$resourcePath")
                ?: throw MapLoadException("Map resource not found: $resourcePath")
            stream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
        } catch (e: IOException) {
            throw MapLoadException("Failed to read map resource: $resourcePath", e)
        }

        return decode(text, "Malformed map resource: $resourcePath")
    }

    private fun decode(text: String, malformedMessage: String): GameMap {
        val mapFile = try {
            json.decodeFromString<MapFile>(text)
        } catch (e: SerializationException) {
            throw MapLoadException(malformedMessage, e)
        }

        return mapFile.toGameMap()
    }
}

/** Raised when a JSON map source cannot be read or parsed into a valid [GameMap]. */
public class MapLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
