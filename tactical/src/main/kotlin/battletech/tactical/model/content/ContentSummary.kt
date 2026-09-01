package battletech.tactical.model.content

import kotlinx.serialization.Serializable

/** The id-only view of an [AssetRegistry] that a chooser needs: what may be picked, nothing more. */
@Serializable
public data class ContentSummary(
    public val maps: List<String> = emptyList(),
    public val mechs: List<String> = emptyList(),
)

/** This registry's ids, maps then mechs, each in the registry's own iteration order. */
public fun AssetRegistry.summarize(): ContentSummary =
    ContentSummary(maps = maps.keys.toList(), mechs = mechs.keys.toList())
