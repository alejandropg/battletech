package battletech.tactical.attack

import battletech.tactical.model.GameMap
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain

/**
 * The result of a line-of-sight check between an attacker and target.
 *
 * @param blocked true when intervening terrain or elevation prevents the attack.
 * @param woodsModifier total to-hit penalty from intervening and target-hex woods.
 * @param partialCover true when the target's lower body is masked — either by an
 *   intervening obstacle exactly one level above the target (and at or below the
 *   attacker), or by the target standing in depth-1 water (legs submerged). Both
 *   sources produce the same effect: +3 to-hit and leg hits deal no damage / no crit.
 * @param blockerHex the first hex responsible for blocking, or null when unblocked.
 * @param blockingTerrain the terrain at [blockerHex], or null when unblocked.
 */
public data class LineOfSightResult(
    val blocked: Boolean,
    val woodsModifier: Int,
    val partialCover: Boolean,
    val blockerHex: HexCoordinates? = null,
    val blockingTerrain: Terrain? = null,
)

/**
 * Traces a line of sight from [attackerPosition] to [targetPosition] on [map].
 *
 * Deliberately position-only: the check never needs any field of either unit beyond its
 * [HexCoordinates]. That is what lets a caller which only knows a unit's public position
 * (the per-viewer query path, where the target is a [battletech.tactical.query.ForeignUnit])
 * run the *identical* check the authoritative resolver runs — one implementation, no drift,
 * and no need for the target's private state.
 *
 * **Woods accumulation** (intervening hexes only, both endpoints excluded):
 *   LIGHT_WOODS = 1 level, HEAVY_WOODS = 2 levels.
 *   LOS is blocked when cumulative levels ≥ 3 across all intervening hexes.
 *   The target's own hex adds to [LineOfSightResult.woodsModifier] but does NOT
 *   count toward the blocking threshold.
 *
 * **Elevation blocking**: an intervening hex whose elevation exceeds both the
 *   attacker's elevation and the target's elevation blocks LOS entirely.
 *
 * **Partial cover**: an intervening hex exactly one elevation level above the target
 *   (while at or below the attacker) masks the target's lower body without blocking
 *   LOS. This adds +3 to-hit and makes leg hits no-effect.
 *
 * Elevation for each position is looked up from [map]; missing hexes default to 0.
 */
public fun lineOfSight(attackerPosition: HexCoordinates, targetPosition: HexCoordinates, map: GameMap): LineOfSightResult {
    val line = attackerPosition.lineTo(targetPosition)
    // Exclude both endpoints (attacker position and target position).
    val intervening = if (line.size <= 2) emptyList() else line.drop(1).dropLast(1)

    val attackerElev = map.hexes[attackerPosition]?.elevation ?: 0
    val targetElev = map.hexes[targetPosition]?.elevation ?: 0

    var interveningWoodsLevels = 0
    var woodsBlockedAt: HexCoordinates? = null
    var woodsBlockingTerrain: Terrain? = null
    var elevationBlockedAt: HexCoordinates? = null
    var elevationBlockingTerrain: Terrain? = null
    var partialCoverFound = false

    for (coord in intervening) {
        val hex = map.hexes[coord]
        val hexElev = hex?.elevation ?: 0

        // Elevation blocking: the intervening hex is taller than both endpoints.
        if (hexElev > attackerElev && hexElev > targetElev) {
            elevationBlockedAt = coord
            elevationBlockingTerrain = hex?.terrain
            break
        }

        // Partial cover: hex exactly one level above the target and at or below attacker.
        if (hexElev > targetElev && hexElev <= attackerElev) {
            partialCoverFound = true
        }

        // Accumulate woods levels for the blocking threshold.
        val hexWoodsLevels = when (hex?.terrain) {
            Terrain.LIGHT_WOODS -> 1
            Terrain.HEAVY_WOODS -> 2
            else -> 0
        }
        if (hexWoodsLevels > 0) {
            interveningWoodsLevels += hexWoodsLevels
            // Record the first hex that pushed levels to the blocking threshold.
            if (interveningWoodsLevels >= 3 && woodsBlockedAt == null) {
                woodsBlockedAt = coord
                woodsBlockingTerrain = hex?.terrain
            }
        }
    }

    val woodsBlocked = interveningWoodsLevels >= 3
    val elevationBlocked = elevationBlockedAt != null
    val blocked = woodsBlocked || elevationBlocked

    // Target's own hex woods add to the to-hit modifier but not to the blocking threshold.
    val targetHexWoods = when (map.hexes[targetPosition]?.terrain) {
        Terrain.LIGHT_WOODS -> 1
        Terrain.HEAVY_WOODS -> 2
        else -> 0
    }

    // Depth-1 water: the target's legs are submerged, giving the same partial-cover effect
    // as an intervening terrain obstacle — +3 to-hit and leg hits are no-effect.
    // (ASSUMPTION/standard BattleTech: shallow water provides lower-body cover.)
    val targetInShallowWater = (map.hexes[targetPosition]?.depth ?: 0) == 1

    return LineOfSightResult(
        blocked = blocked,
        woodsModifier = if (blocked) 0 else interveningWoodsLevels + targetHexWoods,
        partialCover = !blocked && (partialCoverFound || targetInShallowWater),
        blockerHex = if (blocked) elevationBlockedAt ?: woodsBlockedAt else null,
        blockingTerrain = when {
            !blocked -> null
            elevationBlocked -> elevationBlockingTerrain
            else -> woodsBlockingTerrain
        },
    )
}
