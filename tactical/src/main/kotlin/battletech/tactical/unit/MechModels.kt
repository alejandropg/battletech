package battletech.tactical.unit

import battletech.tactical.model.mech.MechModelCatalog

/** Built-in mech variants loaded from the packaged collections under `mech/`. */
public object MechModels {
    private val catalog: MechModelCatalog by lazy { MechModelCatalog.load() }

    public operator fun get(variant: String): MechModel = catalog[variant]

    /** Non-throwing lookup for callers that need to report an unknown variant. */
    public fun find(variant: String): MechModel? = catalog.find(variant)

    public val variants: Set<String> get() = catalog.variants
}
