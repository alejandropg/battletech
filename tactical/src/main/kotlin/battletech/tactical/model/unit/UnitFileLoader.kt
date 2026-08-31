package battletech.tactical.model.unit

import battletech.tactical.io.ResourceOrFileLoader
import kotlinx.serialization.json.Json
import java.nio.file.Path

/** Default [Json] configuration for unit-collection files: strict about unknown keys. */
private val unitJson: Json = Json {
    ignoreUnknownKeys = false
    prettyPrint = true
}

/** File/resource adapter hidden behind [UnitCatalog]'s interface. */
internal class UnitFileLoader(json: Json = unitJson) {

    private val loader = ResourceOrFileLoader(
        resourceDir = "unit",
        label = "Unit",
        json = json,
        build = { text, _ -> UnitFile.decode(json, text) },
        exception = ::UnitLoadException,
    )

    /** Resolves [spec] as an existing file path or an extensionless packaged collection name. */
    internal fun resolve(spec: String): UnitFile = loader.resolve(spec)

    /** Reads and parses the unit file at [path], throwing [UnitLoadException] on failure. */
    internal fun load(path: Path): UnitFile = loader.load(path)

    /** Packaged unit-collection names from `unit/index.json`. */
    internal fun builtInNames(): List<String> = loader.builtInNames()
}
