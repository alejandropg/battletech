package battletech.tactical.model.content

import battletech.tactical.io.CatalogEntry
import battletech.tactical.io.NestedCatalogEntry
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.map.DEFAULT_MAP_NAME
import battletech.tactical.model.map.GameMapCatalog
import battletech.tactical.model.map.LocalMapMatch
import battletech.tactical.model.map.MapLoadException
import battletech.tactical.model.map.compareWithLocalMap
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
     * A client's join-time local-drift comparator, backed by this catalog's registered maps —
     * see [compareWithLocalMap]. Exposed as a closure rather than the [GameMapCatalog] itself so
     * a caller across module boundaries (`network`'s `ClientGameSession`) needs no dependency on
     * this catalog's internals, only the one function shape it already expects.
     */
    public fun mapMatcher(): (GameMap) -> LocalMapMatch = { hostMap -> compareWithLocalMap(hostMap, maps) }

    /** A client's join-time local mech lookup, backed by this catalog's registered mechs. */
    public fun mechFinder(): (String) -> MechModel? = mechs::find

    public companion object {

        /** Builds a catalog from every built-in map/mech/unit collection plus the given external files. */
        public fun load(
            mapPaths: List<Path> = emptyList(),
            mechPaths: List<Path> = emptyList(),
            unitPaths: List<Path> = emptyList(),
        ): ContentCatalog = ContentCatalog(
            GameMapCatalog.load(mapPaths),
            MechModelCatalog.load(mechPaths),
            UnitCatalog.load(unitPaths),
        )
    }
}
