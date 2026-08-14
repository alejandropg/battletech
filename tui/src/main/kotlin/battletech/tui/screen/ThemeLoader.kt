package battletech.tui.screen

import battletech.tactical.io.ResourceOrFileLoader
import kotlinx.serialization.json.Json
import java.nio.file.Path

/** Default [Json] configuration for reading theme sources: strict about unknown keys. */
private val themeJson: Json = Json {
    ignoreUnknownKeys = false
    prettyPrint = true
}

/** Loads a [Theme] from a JSON [ThemeFile] source. */
public class ThemeLoader(json: Json = themeJson) {

    private val loader = ResourceOrFileLoader(
        resourceDir = "theme",
        label = "Theme",
        json = json,
        build = { text, name -> json.decodeFromString<ThemeFile>(text).toTheme(name) },
        exception = ::ThemeLoadException,
    )

    /** Resolves [spec] as in [resolveTheme] — see that function's KDoc. */
    internal fun resolve(spec: String): Theme = loader.resolve(spec)

    /** Reads and parses the theme file at [path], throwing [ThemeLoadException] on any failure. */
    public fun load(path: Path): Theme = loader.load(path)

    /** Reads and parses the classpath resource at [resourcePath]. */
    internal fun loadResource(resourcePath: String): Theme = loader.loadResource(resourcePath)

    /** Packaged theme names from `theme/index.json` — see [ResourceOrFileLoader.builtInNames]'s KDoc. */
    internal fun builtInNames(): List<String> = loader.builtInNames()
}

/** Raised when a JSON theme source cannot be read or parsed into a valid [Theme]. */
public class ThemeLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
