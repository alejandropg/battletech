package battletech.tactical.unit

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.content.AssetBundle
import battletech.tactical.model.content.AssetRegistry
import battletech.tactical.model.content.MatchPlan
import battletech.tactical.model.map.DEFAULT_MAP_NAME
import battletech.tactical.model.map.GameMapCatalog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Covers [AutoDeploy] against the packaged default map (`battletech-classic`, 15 wide x 17 tall). */
internal class AutoDeployTest {

    private val map: GameMap = GameMapCatalog.load().resolve(DEFAULT_MAP_NAME)
    private val registry: AssetRegistry = AssetRegistry.EMPTY.merge(
        AssetBundle(
            maps = listOf(map),
            mechs = listOf(MechModels["WHM-6R"], MechModels["WVR-6R"], MechModels["AS7-D"]),
        ),
    ).registry

    @Test
    fun `capacity is the size of each player's two-row deployment zone`() {
        assertThat(AutoDeploy.capacity(map, PlayerId.PLAYER_1)).isEqualTo(15 * 2)
        assertThat(AutoDeploy.capacity(map, PlayerId.PLAYER_2)).isEqualTo(15 * 2)
    }

    @Test
    fun `player 1 lands in the bottom two rows facing north, player 2 in the top two rows facing south`() {
        val plan = MatchPlan(mapName = DEFAULT_MAP_NAME)
            .withCount(PlayerId.PLAYER_1, "WHM-6R", 2)
            .withCount(PlayerId.PLAYER_2, "WVR-6R", 2)

        val state = AutoDeploy.deploy(plan, registry)

        val p1 = state.units.of(PlayerId.PLAYER_1).all
        val p2 = state.units.of(PlayerId.PLAYER_2).all
        assertThat(p1).allSatisfy { assertThat(it.position.row).isIn(15, 16) }
        assertThat(p1).allSatisfy { assertThat(it.facing).isEqualTo(HexDirection.N) }
        assertThat(p2).allSatisfy { assertThat(it.position.row).isIn(0, 1) }
        assertThat(p2).allSatisfy { assertThat(it.facing).isEqualTo(HexDirection.S) }
    }

    @Test
    fun `a full-capacity roster spreads across both rows and multiple columns`() {
        val plan = MatchPlan(mapName = DEFAULT_MAP_NAME)
            .withCount(PlayerId.PLAYER_1, "WHM-6R", AutoDeploy.capacity(map, PlayerId.PLAYER_1))
            .withCount(PlayerId.PLAYER_2, "WVR-6R", 1)

        val state = AutoDeploy.deploy(plan, registry)

        val positions = state.units.of(PlayerId.PLAYER_1).all.map { it.position }
        assertThat(positions.map { it.row }.toSet()).containsExactlyInAnyOrder(15, 16)
        assertThat(positions.map { it.col }.toSet()).hasSizeGreaterThan(1)
    }

    @Test
    fun `placement is not simply column 1 onward`() {
        val plan = MatchPlan(mapName = DEFAULT_MAP_NAME)
            .withCount(PlayerId.PLAYER_1, "WHM-6R", 3)
            .withCount(PlayerId.PLAYER_2, "WVR-6R", 1)

        val state = AutoDeploy.deploy(plan, registry, roller = DiceRoller.seeded(1))

        val positions = positionsInFillOrder(state, PlayerId.PLAYER_1)
        assertThat(positions).allSatisfy { assertThat(it.row).isIn(15, 16) }
        assertThat(positions).doesNotContainSequence(HexCoordinates(0, 16), HexCoordinates(1, 16), HexCoordinates(2, 16))
    }

    @Test
    fun `same plan and seed deploys identically`() {
        val plan = MatchPlan(mapName = DEFAULT_MAP_NAME)
            .withCount(PlayerId.PLAYER_1, "WHM-6R", 3)
            .withCount(PlayerId.PLAYER_2, "WVR-6R", 3)

        val first = AutoDeploy.deploy(plan, registry, roller = DiceRoller.seeded(42))
        val second = AutoDeploy.deploy(plan, registry, roller = DiceRoller.seeded(42))

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `different seeds deploy differently`() {
        val plan = MatchPlan(mapName = DEFAULT_MAP_NAME)
            .withCount(PlayerId.PLAYER_1, "WHM-6R", 3)
            .withCount(PlayerId.PLAYER_2, "WVR-6R", 3)

        val first = AutoDeploy.deploy(plan, registry, roller = DiceRoller.seeded(1))
        val second = AutoDeploy.deploy(plan, registry, roller = DiceRoller.seeded(2))

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `a board too short to give each player two full rows still keeps the zones apart`() {
        val shortMap = GameMap(
            hexes = (0 until 3).flatMap { row -> (0 until 4).map { col -> HexCoordinates(col, row) } }
                .associateWith { Hex(it) },
            name = "short",
        )
        val shortRegistry = AssetRegistry.EMPTY.merge(
            AssetBundle(maps = listOf(shortMap), mechs = listOf(MechModels["WHM-6R"], MechModels["WVR-6R"])),
        ).registry
        val plan = MatchPlan(mapName = "short")
            .withCount(PlayerId.PLAYER_1, "WHM-6R", AutoDeploy.capacity(shortMap, PlayerId.PLAYER_1))
            .withCount(PlayerId.PLAYER_2, "WVR-6R", AutoDeploy.capacity(shortMap, PlayerId.PLAYER_2))

        val state = AutoDeploy.deploy(plan, shortRegistry, roller = DiceRoller.seeded(7))

        val p1Rows = state.units.of(PlayerId.PLAYER_1).all.map { it.position.row }.toSet()
        val p2Rows = state.units.of(PlayerId.PLAYER_2).all.map { it.position.row }.toSet()
        assertThat(p1Rows).containsExactly(2)
        assertThat(p2Rows).containsExactly(0)
        val positions = state.units.all.map { it.position }
        assertThat(positions.toSet()).hasSize(positions.size)
    }

    @Test
    fun `no two units share a hex`() {
        val plan = MatchPlan(mapName = DEFAULT_MAP_NAME)
            .withCount(PlayerId.PLAYER_1, "WHM-6R", 5)
            .withCount(PlayerId.PLAYER_2, "WVR-6R", 5)

        val state = AutoDeploy.deploy(plan, registry)

        val positions = state.units.all.map { it.position }
        assertThat(positions.toSet()).hasSize(positions.size)
    }

    @Test
    fun `exceeding capacity throws`() {
        val plan = MatchPlan(mapName = DEFAULT_MAP_NAME)
            .withCount(PlayerId.PLAYER_1, "WHM-6R", AutoDeploy.capacity(map, PlayerId.PLAYER_1) + 1)
            .withCount(PlayerId.PLAYER_2, "WVR-6R", 1)

        assertThrows<DeploymentException> { AutoDeploy.deploy(plan, registry) }
    }

    @Test
    fun `unknown map throws`() {
        val plan = MatchPlan(mapName = "nowhere")
            .withCount(PlayerId.PLAYER_1, "WHM-6R", 1)
            .withCount(PlayerId.PLAYER_2, "WVR-6R", 1)

        assertThrows<DeploymentException> { AutoDeploy.deploy(plan, registry) }
    }

    @Test
    fun `no map selected throws`() {
        val plan = MatchPlan()
            .withCount(PlayerId.PLAYER_1, "WHM-6R", 1)
            .withCount(PlayerId.PLAYER_2, "WVR-6R", 1)

        assertThrows<DeploymentException> { AutoDeploy.deploy(plan, registry) }
    }

    @Test
    fun `unknown variant throws`() {
        val plan = MatchPlan(mapName = DEFAULT_MAP_NAME)
            .withCount(PlayerId.PLAYER_1, "NOPE-1", 1)
            .withCount(PlayerId.PLAYER_2, "WVR-6R", 1)

        assertThrows<DeploymentException> { AutoDeploy.deploy(plan, registry) }
    }

    @Test
    fun `empty player throws`() {
        val plan = MatchPlan(mapName = DEFAULT_MAP_NAME).withCount(PlayerId.PLAYER_1, "WHM-6R", 1)

        assertThrows<DeploymentException> { AutoDeploy.deploy(plan, registry) }
    }

    @Test
    fun `id derivation widens the prefix on token collision`() {
        val plan = MatchPlan(mapName = DEFAULT_MAP_NAME)
            .withCount(PlayerId.PLAYER_1, "WHM-6R", 1)
            .withCount(PlayerId.PLAYER_2, "WVR-6R", 1)

        val state = AutoDeploy.deploy(plan, registry)

        assertThat(state.units.all.map { it.id.value }).containsExactlyInAnyOrder("WH1", "WV1")
    }

    @Test
    fun `id derivation falls back to the full variant when two variants share a chassis token`() {
        // WVR-6R and WVR-6M share the whole token "WVR", so no prefix of it can separate them.
        val wolverine = MechModels["WVR-6R"]
        val twoWolverines = AssetRegistry.EMPTY.merge(
            AssetBundle(maps = listOf(map), mechs = listOf(wolverine, wolverine.copy(variant = "WVR-6M"), MechModels["AS7-D"])),
        ).registry
        val plan = MatchPlan(mapName = DEFAULT_MAP_NAME)
            .withCount(PlayerId.PLAYER_1, "WVR-6R", 1)
            .withCount(PlayerId.PLAYER_1, "WVR-6M", 1)
            .withCount(PlayerId.PLAYER_2, "AS7-D", 1)

        val state = AutoDeploy.deploy(plan, twoWolverines)

        // The two Wolverines fall back to their full variant; AS7-D's token is unambiguous, so it
        // keeps the token itself (no shorter prefix can be proven distinct on this path).
        val ids = state.units.all.map { it.id.value }
        assertThat(ids).containsExactlyInAnyOrder("WVR-6R1", "WVR-6M1", "AS71")
        assertThat(ids).doesNotHaveDuplicates()
    }

    @Test
    fun `id derivation keeps a single-letter prefix when there is no collision`() {
        val plan = MatchPlan(mapName = DEFAULT_MAP_NAME).withCount(PlayerId.PLAYER_1, "AS7-D", 2).withCount(
            PlayerId.PLAYER_2,
            "WVR-6R",
            1,
        )

        val state = AutoDeploy.deploy(plan, registry)

        assertThat(state.units.of(PlayerId.PLAYER_1).all.map { it.id.value }).containsExactly("A1", "A2")
    }

    @Test
    fun `a variant fielded by both players still gets globally unique ids`() {
        val plan = MatchPlan(mapName = DEFAULT_MAP_NAME)
            .withCount(PlayerId.PLAYER_1, "WHM-6R", 1)
            .withCount(PlayerId.PLAYER_2, "WHM-6R", 1)

        val state = AutoDeploy.deploy(plan, registry)

        val ids = state.units.all.map { it.id.value }
        assertThat(ids.toSet()).hasSameSizeAs(ids)
        assertThat(ids).containsExactlyInAnyOrder("W1", "W2")
    }

    // Reconstructs fill order from the deterministic id suffix (1, 2, 3, ...).
    private fun positionsInFillOrder(state: GameState, player: PlayerId): List<HexCoordinates> =
        state.units.of(player).all
            .sortedBy { it.id.value.filter(Char::isDigit).toInt() }
            .map { it.position }
}
