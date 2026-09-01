package battletech.tactical.model.content

import battletech.tactical.io.CatalogEntry
import battletech.tactical.io.NestedCatalogEntry
import battletech.tactical.model.GameState
import battletech.tactical.model.map.DEFAULT_MAP_NAME
import battletech.tactical.model.map.GameMapCatalog
import battletech.tactical.model.map.MapLoadException
import battletech.tactical.model.mech.MechModelCatalog
import battletech.tactical.model.unit.DEFAULT_UNITS_NAME
import battletech.tactical.model.unit.UnitCatalog
import battletech.tactical.model.unit.UnitCollectionListing
import battletech.tactical.model.unit.UnitLoadException
import battletech.tactical.unit.MechModel
import java.nio.file.Path

/** Backing for `--list-maps`/`--list-mechs`/`--list-units`: see [ContentCatalog.listing]. */
public data class ContentListing(
    public val maps: List<CatalogEntry>,
    public val mechs: List<NestedCatalogEntry>,
    public val units: List<NestedCatalogEntry>,
)

/**
 * Everything one launch registers: every packaged map, mech collection, and unit collection,
 * plus whatever external files `--add-map`/`--add-mech`/`--add-unit` added. The one seam both
 * launching ([resolveGame]) and listing ([listing]) go through, so they can never disagree about
 * what is registered or whether it is valid — see `docs/unit-files.md` for the failure mode this
 * closes (a duplicate mech variant across two `--add-mech` files used to list fine and fail only
 * at launch, because listing re-opened external files itself instead of using the same catalog).
 */
public class ContentCatalog private constructor(
    private val maps: GameMapCatalog,
    private val mechs: MechModelCatalog,
    private val units: UnitCatalog,
    private val bundle: AssetBundle,
) {

    /**
     * Assembles the roster named [unitsName] onto the board named [mapName] into a fully
     * validated starting [GameState]. Throws [MapLoadException] for an unknown map,
     * [UnitLoadException] for an unknown unit collection or a roster that doesn't fit the chosen
     * map (unknown variant, out-of-bounds or overlapping positions, a player with no units).
     */
    public fun resolveGame(
        mapName: String = DEFAULT_MAP_NAME,
        unitsName: String = DEFAULT_UNITS_NAME,
    ): GameState {
        val gameMap = maps.resolve(mapName)
        val roster = units.resolve(unitsName).toRoster(gameMap, mapName, mechs)
        return GameState(roster, gameMap)
    }

    /** Every registered map, mech collection, and unit collection — see [ContentListing]. */
    public fun listing(): ContentListing = ContentListing(
        maps = maps.entries(),
        mechs = mechs.collectionEntries(),
        units = units.entries(),
    )

    /** Every registered unit collection paired with the complete rows displayed by `--list-units`. */
    public fun unitListings(): List<UnitCollectionListing> = units.collectionListings()

    /**
     * This launch's contribution to a shared [AssetRegistry]: every registered map and mech,
     * assembled once by [load] rather than per call — see there for why it is built eagerly.
     */
    public fun contribution(): AssetBundle = bundle

    /** The registered definition of [variant], or `null` when no registered collection contains it. */
    public fun mech(variant: String): MechModel? = mechs.find(variant)

    public companion object {

        /**
         * Builds a catalog from every built-in map/mech/unit collection plus the given external
         * files, including this launch's [AssetBundle] [contribution].
         *
         * Building the bundle here — rather than on demand — is what keeps every content error on
         * one path: [GameMapCatalog] resolves packaged maps lazily, so a malformed one used to
         * surface only if a game happened to select it, and assembling the bundle later would have
         * raised it from whichever call site asked (past a launcher's own error handling, and
         * repeatedly, since every packaged map is re-parsed per call). Externally registered
         * content is already eager for exactly this reason.
         */
        public fun load(
            mapPaths: List<Path> = emptyList(),
            mechPaths: List<Path> = emptyList(),
            unitPaths: List<Path> = emptyList(),
        ): ContentCatalog {
            val maps = GameMapCatalog.load(mapPaths)
            val mechs = MechModelCatalog.load(mechPaths)
            return ContentCatalog(
                maps = maps,
                mechs = mechs,
                units = UnitCatalog.load(unitPaths),
                bundle = AssetBundle(maps = maps.all().values.toList(), mechs = mechs.all().values.toList()),
            )
        }
    }
}
