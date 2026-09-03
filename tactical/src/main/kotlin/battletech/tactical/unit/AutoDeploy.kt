package battletech.tactical.unit

import battletech.tactical.dice.DiceRoller
import battletech.tactical.dice.RandomDiceRoller
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.content.AssetRegistry
import battletech.tactical.model.content.MatchPlan

/** Rows of [GameMap] nearest each player's home edge that units may be randomly deployed into. */
private const val DEPLOYMENT_ROWS = 2

public class DeploymentException(message: String) : Exception(message)

/**
 * Turns a [MatchPlan] into a validated starting [GameState] with no human placement step: each
 * player's units land at random hexes within the [DEPLOYMENT_ROWS] rows nearest its own home edge.
 * There is no rule in `docs/rules/` for which hexes a 'Mech may deploy into, and the movement code
 * ([battletech.tactical.movement.ReachabilityCalculator]) applies no terrain-based passability
 * check either — every hex that exists on the board is a legal move destination there. [AutoDeploy]
 * follows the same rule: a hex is a legal deployment hex iff it exists in [GameMap.hexes].
 */
public object AutoDeploy {

    /** How many units [player] can legally be given on [map] — the size of its deployment zone. */
    public fun capacity(map: GameMap, player: PlayerId): Int = deploymentZone(map, player).size

    /** Assembles [plan] against [registry] into a validated starting [GameState]. */
    public fun deploy(plan: MatchPlan, registry: AssetRegistry, roller: DiceRoller = RandomDiceRoller()): GameState {
        val mapName = plan.mapName ?: throw DeploymentException("No map selected")
        val map = registry.map(mapName) ?: throw DeploymentException("Unknown map: '$mapName'")

        val allVariants = PlayerId.entries.flatMap { (plan.rosters[it] ?: emptyMap()).keys }.distinct()
        val prefixes = idPrefixes(allVariants)
        // Shared across both players so a variant fielded by both still gets globally-unique ids.
        val runningIndex = mutableMapOf<String, Int>()

        val units =
            PlayerId.entries.flatMap { player -> deployPlayer(player, plan, registry, map, prefixes, runningIndex, roller) }
        return GameState(UnitRoster(units), map)
    }

    private fun deployPlayer(
        player: PlayerId,
        plan: MatchPlan,
        registry: AssetRegistry,
        map: GameMap,
        prefixes: Map<String, String>,
        runningIndex: MutableMap<String, Int>,
        roller: DiceRoller,
    ): List<CombatUnit> {
        val roster = plan.rosters[player] ?: emptyMap()
        val total = roster.values.sum()
        if (total == 0) throw DeploymentException("Player $player has no units")

        val hexes = deploymentZone(map, player).shuffled(roller)
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
     * Legal deployment hexes for [player]: every hex in the [DEPLOYMENT_ROWS] rows nearest its own
     * home edge — player 2's home edge is the top, player 1's the bottom — sorted by (row, col) so
     * a seeded [DiceRoller] shuffles a reproducible order. With an odd [GameMap] height, or a height
     * shorter than `2 * DEPLOYMENT_ROWS`, the two players' zones never overlap: each is clamped to
     * its own half first.
     */
    private fun deploymentZone(map: GameMap, player: PlayerId): List<HexCoordinates> {
        val height = (map.hexes.keys.maxOfOrNull { it.row } ?: -1) + 1
        val half = when (player) {
            PlayerId.PLAYER_2 -> (0 until height / 2)
            PlayerId.PLAYER_1 -> ((height - height / 2) until height).reversed()
        }
        val rows = half.take(DEPLOYMENT_ROWS).toSet()
        return map.hexes.keys.filter { it.row in rows }.sortedWith(compareBy({ it.row }, { it.col }))
    }

    /** Fisher–Yates shuffle drawing indices from [roller] instead of `kotlin.random.Random`. */
    private fun <T> List<T>.shuffled(roller: DiceRoller): List<T> {
        val result = toMutableList()
        for (i in result.lastIndex downTo 1) {
            val j = roller.uniformIndex(i + 1)
            result[i] = result[j].also { result[j] = result[i] }
        }
        return result
    }

    /**
     * A uniform random index in `0 until bound`, built from the fewest base-6 [DiceRoller.d6]
     * digits that cover [bound], rejecting draws past the last full multiple of [bound] so the
     * result stays uniform rather than biased toward low indices.
     */
    private fun DiceRoller.uniformIndex(bound: Int): Int {
        var digits = 1
        var range = 6
        while (range < bound) {
            digits++
            range *= 6
        }
        val limit = range - range % bound
        while (true) {
            var value = 0
            repeat(digits) { value = value * 6 + (d6() - 1) }
            if (value < limit) return value % bound
        }
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
