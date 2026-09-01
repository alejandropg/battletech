package battletech.tactical.model.content

import battletech.tactical.model.PlayerId
import kotlinx.serialization.Serializable

/**
 * What a match is going to be, before it exists: the board and how many of each mech model each
 * player fields. Deliberately id-only — it names registered content rather than carrying it, so the
 * same value serves the setup screen, the lobby wire mirror, and [battletech.tactical.unit.AutoDeploy]'s input.
 *
 * [mapName] is null until a board has been chosen; [battletech.tactical.unit.AutoDeploy] rejects that,
 * the setup screen refuses to commit before then.
 */
@Serializable
public data class MatchPlan(
    public val mapName: String? = null,
    public val rosters: Map<PlayerId, Map<String, Int>> = emptyMap(),
) {
    public fun count(player: PlayerId, variant: String): Int = rosters[player]?.get(variant) ?: 0

    public fun totalUnits(player: PlayerId): Int = rosters[player]?.values?.sum() ?: 0

    /** [count] set to [value]; a zero (or negative) value removes the entry entirely. */
    public fun withCount(player: PlayerId, variant: String, value: Int): MatchPlan {
        val roster = rosters[player] ?: emptyMap()
        val updated = if (value <= 0) roster - variant else roster + (variant to value)
        val updatedRosters = if (updated.isEmpty()) rosters - player else rosters + (player to updated)
        return copy(rosters = updatedRosters)
    }

    public fun withMap(name: String?): MatchPlan = copy(mapName = name)
}
