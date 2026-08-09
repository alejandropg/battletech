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
 *   intervening obstacle or by the target standing in depth-1 water. Both sources
 *   produce the same effect (`docs/rules/line-of-sight.md` §3).
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
 * (the per-viewer query path, where the target is a [battletech.tactical.unit.ForeignUnit])
 * run the *identical* check the authoritative resolver runs — one implementation, no drift,
 * and no need for the target's private state.
 *
 * Woods accumulation, elevation blocking and partial cover are all owned by
 * `docs/rules/line-of-sight.md` §1–3. Two modelling details it does not pin down:
 * woods accumulate over intervening hexes only (the target's own hex adds to
 * [LineOfSightResult.woodsModifier] but not to the blocking threshold), and elevation
 * for each position is looked up from [map] with missing hexes defaulting to 0.
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

        // Accumulate woods levels for the blocking threshold. ROUGH (and CLEAR/WATER) fall
        // through to 0 deliberately — rough terrain never blocks LOS or adds a to-hit modifier
        // (docs/rules/line-of-sight.md §1).
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
    // ROUGH falls through to 0 deliberately, same as above.
    val targetHexWoods = when (map.hexes[targetPosition]?.terrain) {
        Terrain.LIGHT_WOODS -> 1
        Terrain.HEAVY_WOODS -> 2
        else -> 0
    }

    // Depth-1 water gives the same partial cover as an intervening obstacle
    // (`docs/rules/water.md` §1). ASSUMPTION: the docs do not state that the two
    // sources are interchangeable; this treats them as one flag.
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
