package battletech.tactical.model.mech

import battletech.tactical.unit.MechModel
import java.nio.file.Path

/**
 * Immutable registry of every mech variant available to one game-loading operation.
 * Packaged and external collections are loaded eagerly so all content errors fail at startup.
 */
public class MechModelCatalog private constructor(
    private val registry: Map<String, MechModel>,
) {
    public operator fun get(variant: String): MechModel =
        registry[variant] ?: error("Unknown mech variant: $variant")

    public fun find(variant: String): MechModel? = registry[variant]

    public val variants: Set<String> get() = registry.keys

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

            for (name in loader.builtInCollectionNames()) {
                addModels(registry, sources, loader.loadBuiltIn(name), "mech/$name.json")
            }
            for (path in externalPaths) {
                addModels(registry, sources, loader.load(path), path.toString())
            }

            return MechModelCatalog(registry)
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
