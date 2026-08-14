package battletech.tactical.io

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Loads a [T] that lives either as an existing filesystem path or as a packaged classpath
 * resource named `<resourceDir>/<name>.json`. Generalizes the "resolve a spec, load JSON, build a
 * domain object" pattern shared by e.g. `battletech.tactical.model.map.GameMapLoader` and
 * `battletech.tui.screen.ThemeLoader`: every failure mode (missing file, missing resource,
 * unreadable, malformed JSON) becomes one [exception] instance with a message shaped from [label]
 * (e.g. `"Map"`/`"Theme"`) — callers never assemble their own error text.
 *
 * [build] receives the file's raw text and a context `name` (the path, for a disk file; the
 * resource's file stem, for a packaged one — some consumers need this to name the object they
 * build, others ignore it) and returns the finished [T]; it should decode via [json] internally
 * and may let [SerializationException] propagate — this loader catches it and wraps it in
 * [exception].
 *
 * A missing packaged resource's message is enriched with [builtInNames] when `<resourceDir>/index.json`
 * exists on the classpath — see that function's KDoc for why a missing index degrades silently
 * instead of throwing.
 */
public class ResourceOrFileLoader<T>(
    private val resourceDir: String,
    private val label: String,
    private val json: Json,
    private val build: (text: String, name: String) -> T,
    private val exception: (message: String, cause: Throwable?) -> Exception,
) {
    private val lowerLabel = label.lowercase()

    /**
     * Resolves [spec]: an existing filesystem path is authoritative and loaded via [load].
     * Otherwise [spec] is treated as an extensionless packaged name and
     * `<resourceDir>/<spec>.json` is loaded from the classpath via [loadResource]. An existing
     * path's failure never falls back to a packaged resource.
     */
    public fun resolve(spec: String): T {
        val path = Path(spec)
        return if (path.exists()) load(path) else loadResource("$resourceDir/$spec.json")
    }

    /** Reads and parses the file at [path], throwing [exception] on any failure. */
    public fun load(path: Path): T {
        val text = try {
            path.readText()
        } catch (e: NoSuchFileException) {
            throw exception("$label file not found: $path", e)
        } catch (e: IOException) {
            throw exception("Failed to read $lowerLabel file: $path", e)
        }

        return decode(text, path.toString(), "Malformed $lowerLabel file: $path")
    }

    /** Reads and parses the classpath resource at [resourcePath], throwing [exception] on any failure. */
    public fun loadResource(resourcePath: String): T {
        val text = try {
            val stream = ResourceOrFileLoader::class.java.getResourceAsStream("/$resourcePath")
                ?: throw exception(notFoundMessage(resourcePath), null)
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            throw exception("Failed to read $lowerLabel resource: $resourcePath", e)
        }

        val name = resourcePath.substringAfterLast('/').removeSuffix(".json")
        return decode(text, name, "Malformed $lowerLabel resource: $resourcePath")
    }

    /**
     * Names from the packaged `<resourceDir>/index.json` manifest — the built-in [T]s shipped in
     * the jar, used to enrich a not-found error since a jar cannot otherwise list a resource
     * directory. Empty (rather than throwing) if the manifest is missing or unreadable: this is
     * used to enrich an unrelated error message, and a missing index must never mask the real
     * failure.
     */
    public fun builtInNames(): List<String> = try {
        val stream = ResourceOrFileLoader::class.java.getResourceAsStream("/$resourceDir/index.json")
        if (stream == null) {
            emptyList()
        } else {
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            json.decodeFromString<ResourceIndex>(text).names
        }
    } catch (e: IOException) {
        emptyList()
    } catch (e: SerializationException) {
        emptyList()
    }

    private fun notFoundMessage(resourcePath: String): String {
        val builtIns = builtInNames()
        val base = "$label resource not found: $resourcePath"
        return if (builtIns.isEmpty()) base else "$base\nBuilt-in ${lowerLabel}s: ${builtIns.joinToString(", ")}"
    }

    private fun decode(text: String, name: String, malformedMessage: String): T = try {
        build(text, name)
    } catch (e: SerializationException) {
        throw exception(malformedMessage, e)
    }
}

/** On-disk shape of a `<resourceDir>/index.json` manifest: the packaged names in that directory. */
@Serializable
internal data class ResourceIndex(val names: List<String> = emptyList())
