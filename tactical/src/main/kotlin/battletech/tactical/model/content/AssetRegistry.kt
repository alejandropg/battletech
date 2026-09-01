package battletech.tactical.model.content

import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.unit.MechModel
import kotlinx.serialization.Serializable

/** Which kind of registered asset an id names. Units are deliberately absent: a unit collection is
 * a *selection*, not content a match references by id — the resolved roster already crosses the
 * wire inside the match snapshot. */
@Serializable
public enum class AssetKind { MAP, MECH }

/** One asset's identity: its [kind] plus the id unique within that kind ([GameMap.name] / [MechModel.variant]). */
@Serializable
public data class AssetRef(public val kind: AssetKind, public val id: String)

/** One party's contribution to a shared [AssetRegistry]: everything it has registered. */
@Serializable
public data class AssetBundle(
    public val maps: List<GameMap> = emptyList(),
    public val mechs: List<MechModel> = emptyList(),
) {

    /**
     * The first id this bundle repeats within one [AssetKind], or `null` when it is well-formed.
     * Well-formedness is a property of the bundle alone, so it is asked here rather than of a
     * merge: a receiver validates an inbound bundle before deciding whether to accept its sender
     * at all (`network`'s `GameServer` answers a repeat with
     * `JoinRejectionReason.INVALID_CONTENT`), which is a different question from what merging it
     * into a particular registry would produce. Blank ids are ignored — [AssetRegistry.merge]
     * skips them entirely, so repeating one is not a collision.
     */
    public fun duplicateId(): AssetRef? =
        duplicateWithin(maps.map(GameMap::name))?.let { AssetRef(AssetKind.MAP, it) }
            ?: duplicateWithin(mechs.map(MechModel::variant))?.let { AssetRef(AssetKind.MECH, it) }

    public companion object {
        public val EMPTY: AssetBundle = AssetBundle()
    }
}

/** What merging one [AssetBundle] produced: see [AssetRegistry.merge]. */
public data class MergeResult(
    public val registry: AssetRegistry,
    public val conflicts: List<AssetRef>,
)

/**
 * A match-scoped registry of shared MAP and MECH content: every party contributes its entire
 * registered content ([ContentCatalog.contribution]) via [merge], first-registrant-wins on a
 * colliding id. Immutable — a caller accumulates a match's registry by repeatedly reassigning to
 * the [MergeResult.registry] returned by successive [merge] calls, and simply stops calling
 * [merge] once the match is frozen.
 */
@Serializable
public data class AssetRegistry(
    public val maps: Map<String, GameMap> = emptyMap(),
    public val mechs: Map<String, MechModel> = emptyMap(),
) {

    /**
     * Merges [bundle] into this registry, kind by kind. Each asset is keyed by its own id and,
     * when that id is already present, compared against the existing entry with the id field
     * normalized out of both sides (so a stored entry whose own id field happens to disagree with
     * the key it's registered under still compares correctly) — identical normalized content is a
     * silent no-op, differing content keeps the EXISTING entry and reports the id as a conflict.
     * A new id is added with no finding. A blank id is skipped entirely.
     *
     * Total: every bundle merges. A bundle that repeats an id within one kind merges as if two
     * parties had contributed it (first occurrence registered, second compared against it) —
     * callers that must not accept such a bundle at all reject it up front via
     * [AssetBundle.duplicateId].
     */
    public fun merge(bundle: AssetBundle): MergeResult {
        val mergedMaps = mergeKind(AssetKind.MAP, maps, bundle.maps, GameMap::name) { map, id -> map.copy(name = id) }
        val mergedMechs =
            mergeKind(AssetKind.MECH, mechs, bundle.mechs, MechModel::variant) { mech, id -> mech.copy(variant = id) }

        return MergeResult(
            registry = AssetRegistry(mergedMaps.assets, mergedMechs.assets),
            conflicts = (mergedMaps.conflicts + mergedMechs.conflicts).sortedWith(compareBy({ it.kind }, { it.id })),
        )
    }

    public fun mech(variant: String): MechModel? = mechs[variant]
    public fun map(name: String): GameMap? = maps[name]

    public companion object {
        public val EMPTY: AssetRegistry = AssetRegistry()

        /** The match's own board (skipped when [GameMap.name] is blank) plus the roster's distinct models. */
        public fun forMatch(state: GameState): AssetRegistry {
            val maps = if (state.map.name.isBlank()) emptyMap() else mapOf(state.map.name to state.map)
            return AssetRegistry(maps = maps, mechs = distinctMatchModels(state))
        }
    }
}

/** One kind's share of a [AssetRegistry.merge]: see [mergeKind]. */
private class KindMerge<T>(val assets: Map<String, T>, val conflicts: List<AssetRef>)

/**
 * The merge rules stated once, for any asset kind: [idOf] reads an asset's id and [normalize]
 * rewrites a stored asset's id field to the key it is being compared under. Kept generic so a
 * third kind cannot acquire a third, subtly different copy of "add, no-op, or conflict".
 */
private fun <T> mergeKind(
    kind: AssetKind,
    existing: Map<String, T>,
    incoming: List<T>,
    idOf: (T) -> String,
    normalize: (T, String) -> T,
): KindMerge<T> {
    val assets = existing.toMutableMap()
    val conflicts = mutableListOf<AssetRef>()
    for (asset in incoming) {
        val id = idOf(asset)
        if (id.isBlank()) continue
        val current = assets[id]
        when {
            current == null -> assets[id] = asset
            normalize(current, id) != asset -> conflicts += AssetRef(kind, id)
        }
    }
    return KindMerge(assets, conflicts)
}

private fun duplicateWithin(ids: List<String>): String? =
    ids.filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.key

/** Every distinct mech model referenced by [state]'s roster, keyed by variant. */
private fun distinctMatchModels(state: GameState): Map<String, MechModel> {
    val byVariant = linkedMapOf<String, MechModel>()
    for (unit in state.units) {
        val previous = byVariant[unit.variant]
        check(previous == null || previous == unit.model) {
            "Match contains conflicting definitions for mech variant '${unit.variant}'"
        }
        byVariant[unit.variant] = unit.model
    }
    return byVariant
}
