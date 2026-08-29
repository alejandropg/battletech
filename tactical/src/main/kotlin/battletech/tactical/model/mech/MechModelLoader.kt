package battletech.tactical.model.mech

import battletech.tactical.io.ResourceOrFileLoader
import battletech.tactical.unit.MechModel
import kotlinx.serialization.json.Json
import java.nio.file.Path

/** Default JSON configuration for mech collections: strict about unknown keys. */
private val mechJson: Json = Json {
    ignoreUnknownKeys = false
    prettyPrint = true
}

/** File/resource adapter hidden behind [MechModelCatalog]'s interface. */
internal class MechModelLoader(json: Json = mechJson) {
    private val loader = ResourceOrFileLoader(
        resourceDir = "mech",
        label = "Mech",
        json = json,
        build = { text, source -> MechFile.decode(json, text).toModels(source) },
        exception = ::MechLoadException,
    )

    internal fun load(path: Path): List<MechModel> = loader.load(path)

    internal fun loadBuiltIn(name: String): List<MechModel> = loader.loadResource("mech/$name.json")

    internal fun builtInCollectionNames(): List<String> = loader.builtInNames()
}
