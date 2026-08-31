package battletech.tactical.model.unit

import battletech.tactical.model.HexDirection

/** The source fields displayed by `--list-units` for one registered unit. */
public data class UnitListing(
    public val id: String,
    public val player: Int,
    public val variant: String,
    public val gunnery: Int,
    public val piloting: Int,
    public val col: Int,
    public val row: Int,
    public val facing: HexDirection,
)

/** One registered unit collection paired with the complete rows displayed by `--list-units`. */
public data class UnitCollectionListing(
    public val name: String,
    public val external: Boolean,
    public val units: List<UnitListing>,
)
