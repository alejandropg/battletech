package battletech.tactical.unit

import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.content.AssetRegistry
import battletech.tactical.model.content.MatchPlan

public class DeploymentException(message: String) : Exception(message)

/**
 * Turns a [MatchPlan] into a validated starting [GameState] with no human placement step: each
 * player's units fill their own half of the board, back row first. There is no rule in
 * `docs/rules/` for which hexes a 'Mech may deploy into, and the movement code
 * ([battletech.tactical.movement.ReachabilityCalculator]) applies no terrain-based passability
 * check either — every hex that exists on the board is a legal move destination there. [AutoDeploy]
 * follows the same rule: a hex is a legal deployment hex iff it exists in [GameMap.hexes].
 */
public object AutoDeploy {

    /** How many units [player] can legally be given on [map] — the size of its deployment half. */
    public fun capacity(map: GameMap, player: PlayerId): Int = deploymentOrder(map, player).size

    /** Assembles [plan] against [registry] into a validated starting [GameState]. */
    public fun deploy(plan: MatchPlan, registry: AssetRegistry): GameState {
        val mapName = plan.mapName ?: throw DeploymentException("No map selected")
        val map = registry.map(mapName) ?: throw DeploymentException("Unknown map: '$mapName'")

        val allVariants = PlayerId.entries.flatMap { (plan.rosters[it] ?: emptyMap()).keys }.distinct()
        val prefixes = idPrefixes(allVariants)
        // Shared across both players so a variant fielded by both still gets globally-unique ids.
        val runningIndex = mutableMapOf<String, Int>()

        val units = PlayerId.entries.flatMap { player -> deployPlayer(player, plan, registry, map, prefixes, runningIndex) }
        return GameState(UnitRoster(units), map)
    }

    private fun deployPlayer(
        player: PlayerId,
        plan: MatchPlan,
        registry: AssetRegistry,
        map: GameMap,
        prefixes: Map<String, String>,
        runningIndex: MutableMap<String, Int>,
    ): List<CombatUnit> {
        val roster = plan.rosters[player] ?: emptyMap()
        val total = roster.values.sum()
        if (total == 0) throw DeploymentException("Player $player has no units")

        val hexes = deploymentOrder(map, player)
        if (total > hexes.size) {
            throw DeploymentException(
                "Player $player has $total units, but map '${map.name}' has room for only ${hexes.size}",
            )
        }

        val facing = if (player == PlayerId.PLAYER_1) HexDirection.N else HexDirection.S
        var hexCursor = 0
        val units = mutableListOf<CombatUnit>()
        for (variant in roster.keys.sorted()) {
            val model = registry.mech(variant) ?: throw DeploymentException("Unknown mech variant: '$variant'")
            val prefix = prefixes.getValue(variant)
            repeat(roster.getValue(variant)) {
                val index = (runningIndex[variant] ?: 0) + 1
                runningIndex[variant] = index
                units += model.createUnit(
                    id = UnitId("$prefix$index"),
                    owner = player,
                    position = hexes[hexCursor++],
                    facing = facing,
                )
            }
        }
        return units
    }

    /**
     * Legal deployment hexes for [player], in fill order: player 2 walks its half's rows ascending
     * from the top, player 1 walks its half's rows descending from the bottom, columns ascending
     * within a row. With an odd [GameMap] height the middle row belongs to neither player.
     */
    private fun deploymentOrder(map: GameMap, player: PlayerId): List<HexCoordinates> {
        val height = (map.hexes.keys.maxOfOrNull { it.row } ?: -1) + 1
        val rows = when (player) {
            PlayerId.PLAYER_2 -> (0 until height / 2)
            PlayerId.PLAYER_1 -> ((height - height / 2) until height).reversed()
        }
        val rowsOfHexes = map.hexes.keys.groupBy { it.row }
        return rows.flatMap { row -> rowsOfHexes[row].orEmpty().sortedBy { it.col } }
    }

    /**
     * A unique-per-variant id prefix for every entry in [variants]: the shortest common length,
     * taken from each variant's token (the part before its first `-`), at which every variant's
     * prefix is distinct from every other's.
     *
     * Two variants of the SAME chassis (`WVR-6R` and `WVR-6M`) share their whole token, so no
     * prefix of it can ever separate them — the loop below finds no length that works. Those
     * variants fall back to the full variant string, which is unique by construction because
     * [variants] is distinct; a variant whose token is unambiguous keeps its short prefix. Without
     * that fallback both `WVR-*` units are called `WVR1`, and since [UnitRoster] indexes by id
     * with a last-one-wins map, one of them becomes unreachable through
     * [UnitRoster.byId] — silent state corruption rather than a loud failure.
     */
    private fun idPrefixes(variants: List<String>): Map<String, String> {
        val tokens = variants.associateWith { it.substringBefore('-') }
        val maxLength = tokens.values.maxOfOrNull { it.length } ?: 0
        for (length in 1..maxLength) {
            val candidates = tokens.mapValues { (_, token) -> token.take(length) }
            if (candidates.values.toSet().size == candidates.size) return candidates
        }

        val ambiguousTokens = tokens.values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val prefixes = tokens.mapValues { (variant, token) -> if (token in ambiguousTokens) variant else token }
        check(prefixes.values.toSet().size == prefixes.size) {
            "Deployment id prefixes are not unique across $variants: ${prefixes.values}"
        }
        return prefixes
    }
}
