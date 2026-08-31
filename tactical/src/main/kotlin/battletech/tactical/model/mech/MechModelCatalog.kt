package battletech.tactical.model.mech

import battletech.tactical.io.NestedCatalogEntry
import battletech.tactical.io.externalResourceName
import battletech.tactical.unit.MechModel
import java.nio.file.Path

/**
 * Immutable registry of every mech variant available to one game-loading operation.
 * Packaged and external collections are loaded eagerly so all content errors fail at startup.
 */
public class MechModelCatalog private constructor(
    private val registry: Map<String, MechModel>,
    private val collections: List<NestedCatalogEntry>,
) {
    public operator fun get(variant: String): MechModel =
        registry[variant] ?: error("Unknown mech variant: $variant")

    public fun find(variant: String): MechModel? = registry[variant]

    public val variants: Set<String> get() = registry.keys

    /**
     * Every registered collection (packaged first, in `mech/index.json` order, then externals in
     * registration order) paired with the variants it contributes — the shape `--list-mechs`
     * renders. Computed in the same [load] pass that builds [registry], so a listing and an
     * actual launch can never disagree about what a collection contains or whether it is valid.
     */
    public fun collectionEntries(): List<NestedCatalogEntry> = collections

    public companion object {
        /**
         * Loads every collection named by `mech/index.json`, then every [externalPath] in order.
         * A variant may appear exactly once across the combined catalog; no source can override another.
         */
        public fun load(
            externalPaths: List<Path> = emptyList(),
        ): MechModelCatalog = load(externalPaths, MechModelLoader())

        internal fun load(externalPaths: List<Path>, loader: MechModelLoader): MechModelCatalog {
            val registry = linkedMapOf<String, MechModel>()
            val sources = mutableMapOf<String, String>()
            val collections = mutableListOf<NestedCatalogEntry>()

            for (name in loader.builtInCollectionNames()) {
                val models = loader.loadBuiltIn(name)
                addModels(registry, sources, models, "mech/$name.json")
                collections += NestedCatalogEntry(name, external = false, items = models.map { it.variant })
            }
            for (path in externalPaths) {
                val models = loader.load(path)
                addModels(registry, sources, models, path.toString())
                collections += NestedCatalogEntry(externalResourceName(path), external = true, items = models.map { it.variant })
            }

            return MechModelCatalog(registry, collections)
        }

        private fun addModels(
            registry: MutableMap<String, MechModel>,
            sources: MutableMap<String, String>,
            models: List<MechModel>,
            source: String,
        ) {
            for (model in models) {
                val previousSource = sources[model.variant]
                if (previousSource != null) {
                    throw MechLoadException(
                        "Mech variant '${model.variant}' is repeated in $source; first defined in $previousSource",
                    )
                }
                registry[model.variant] = model
                sources[model.variant] = source
            }
        }
    }
}

/** Packaged mech-collection-file names from `mech/index.json`. */
public fun builtInMechCollectionNames(): List<String> = MechModelLoader().builtInCollectionNames()

/** Variant identifiers contained in the packaged collection named [collectionName]. */
public fun mechCollectionVariants(collectionName: String): List<String> =
    MechModelLoader().loadBuiltIn(collectionName).map { it.variant }

/** Variant identifiers contained in the external collection file at [collectionPath]. */
public fun mechCollectionVariants(collectionPath: Path): List<String> =
    MechModelLoader().load(collectionPath).map { it.variant }
