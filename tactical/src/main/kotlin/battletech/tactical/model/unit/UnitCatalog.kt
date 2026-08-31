package battletech.tactical.model.unit

import battletech.tactical.io.NestedCatalogEntry
import battletech.tactical.io.externalResourceName
import java.nio.file.Path

/**
 * Unit collections available to assemble a game: every packaged collection plus external files
 * registered at startup. Mirrors [battletech.tactical.model.map.GameMapCatalog]'s shape rather
 * than [battletech.tactical.model.mech.MechModelCatalog]'s: `--unit <name>` selects one whole
 * registered file as the roster, it does not compose units from several files the way mechs
 * compose variants, so there is no cross-file id merge or uniqueness check here. External
 * collections are loaded eagerly so a bad registration fails before a match starts; packaged
 * collections remain lazy and are loaded only when a launcher selects one.
 */
public class UnitCatalog private constructor(
    private val loader: UnitFileLoader,
    private val builtInNames: Set<String>,
    private val externalUnits: Map<String, UnitFile>,
) {

    /** Resolves [name] to its registered unit collection, throwing [UnitLoadException] when unknown. */
    internal fun resolve(name: String): UnitFile {
        externalUnits[name]?.let { return it }
        if (name !in builtInNames) {
            val available = (builtInNames + externalUnits.keys).sorted()
            val suffix = if (available.isEmpty()) "" else "\nAvailable unit collections: ${available.joinToString(", ")}"
            throw UnitLoadException("Unit collection not found in catalog: $name$suffix")
        }
        return loader.resolve(name)
    }

    /**
     * Every registered collection, packaged in `unit/index.json` order first then externals in
     * registration order, each paired with its unit ids — the shape `--list-units` renders.
     */
    public fun entries(): List<NestedCatalogEntry> =
        builtInNames.map { name -> NestedCatalogEntry(name, external = false, items = loader.resolve(name).unitIds()) } +
            externalUnits.map { (name, file) -> NestedCatalogEntry(name, external = true, items = file.unitIds()) }

    public companion object {

        /**
         * Builds a catalog and registers every [externalPath]. A file's catalog name is its base
         * filename with one trailing `.json` removed. Names are exact and case-sensitive; neither
         * a built-in nor an earlier external registration may be replaced.
         */
        public fun load(externalPaths: List<Path> = emptyList()): UnitCatalog = load(externalPaths, UnitFileLoader())

        internal fun load(externalPaths: List<Path>, loader: UnitFileLoader): UnitCatalog {
            val builtIns = loader.builtInNames().toSet()
            val external = linkedMapOf<String, UnitFile>()

            for (path in externalPaths) {
                val name = externalResourceName(path)
                if (name.isBlank()) {
                    throw UnitLoadException("External unit filename must provide a catalog name: $path")
                }
                if (name in builtIns) {
                    throw UnitLoadException("External unit file '$path' conflicts with built-in unit collection '$name'")
                }
                if (name in external) {
                    throw UnitLoadException("External unit collection name '$name' is registered more than once")
                }

                external[name] = loader.load(path)
            }

            return UnitCatalog(loader, builtIns, external)
        }
    }
}
