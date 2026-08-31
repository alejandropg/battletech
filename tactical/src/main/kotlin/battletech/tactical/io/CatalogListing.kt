package battletech.tactical.io

import java.nio.file.Path

/**
 * Derives the catalog name an externally registered file is registered under: its filename with
 * one trailing `.json` removed. The one rule every content catalog (`GameMapCatalog`,
 * `MechModelCatalog`, `UnitCatalog`) uses to name an external registration, so it lives here
 * rather than being re-derived by each caller.
 */
public fun externalResourceName(path: Path): String = path.fileName?.toString().orEmpty().removeSuffix(".json")

/** One registered catalog entry: its name, and whether it was externally registered. */
public data class CatalogEntry(public val name: String, public val external: Boolean)

/** One registered catalog entry that itself contains a named list of [items] (e.g. variants, unit ids). */
public data class NestedCatalogEntry(public val name: String, public val external: Boolean, public val items: List<String>)
