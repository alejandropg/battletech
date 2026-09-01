package battletech.tactical.unit

import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
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
    fun `capacity is the size of each player's half`() {
        assertThat(AutoDeploy.capacity(map, PlayerId.PLAYER_1)).isEqualTo(15 * 8)
        assertThat(AutoDeploy.capacity(map, PlayerId.PLAYER_2)).isEqualTo(15 * 8)
    }

    @Test
    fun `player 1 lands in the bottom rows facing north, player 2 in the top rows facing south`() {
        val plan = MatchPlan(mapName = DEFAULT_MAP_NAME)
            .withCount(PlayerId.PLAYER_1, "WHM-6R", 2)
            .withCount(PlayerId.PLAYER_2, "WVR-6R", 2)

        val state = AutoDeploy.deploy(plan, registry)

        val p1 = state.units.of(PlayerId.PLAYER_1).all
        val p2 = state.units.of(PlayerId.PLAYER_2).all
        assertThat(p1).allSatisfy { assertThat(it.position.row).isGreaterThanOrEqualTo(9) }
        assertThat(p1).allSatisfy { assertThat(it.facing).isEqualTo(HexDirection.N) }
        assertThat(p2).allSatisfy { assertThat(it.position.row).isLessThan(8) }
        assertThat(p2).allSatisfy { assertThat(it.facing).isEqualTo(HexDirection.S) }
    }

    @Test
    fun `fill order is row-then-column, starting at the edge row`() {
        val plan = MatchPlan(mapName = DEFAULT_MAP_NAME)
            .withCount(PlayerId.PLAYER_1, "WHM-6R", 3)
            .withCount(PlayerId.PLAYER_2, "WVR-6R", 1)

        val state = AutoDeploy.deploy(plan, registry)

        val positions = positionsInFillOrder(state, PlayerId.PLAYER_1)
        assertThat(positions).containsExactly(HexCoordinates(0, 16), HexCoordinates(1, 16), HexCoordinates(2, 16))
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

    @Test
    fun `deploying the same plan twice is deterministic`() {
        val plan = MatchPlan(mapName = DEFAULT_MAP_NAME)
            .withCount(PlayerId.PLAYER_1, "WHM-6R", 3)
            .withCount(PlayerId.PLAYER_2, "WVR-6R", 3)

        val first = AutoDeploy.deploy(plan, registry)
        val second = AutoDeploy.deploy(plan, registry)

        assertThat(first).isEqualTo(second)
    }

    // Reconstructs fill order from the deterministic id suffix (1, 2, 3, ...).
    private fun positionsInFillOrder(state: GameState, player: PlayerId): List<HexCoordinates> =
        state.units.of(player).all
            .sortedBy { it.id.value.filter(Char::isDigit).toInt() }
            .map { it.position }
}
