package battletech.tactical.query

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.UnitId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class GameStateProjectionTest {

    private val ownUnit = aUnit(
        id = "own",
        owner = PlayerId.PLAYER_1,
        name = "Hunchback",
        position = HexCoordinates(1, 2),
        facing = HexDirection.NE,
        armor = anArmorLayout(head = 9, centerTorso = 47),
        walkingMP = 4,
        runningMP = 6,
        jumpMP = 0,
        weapons = listOf(aWeapon(name = "AC/20"), aWeapon(name = "Medium Laser")),
    )
    private val enemyUnit = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, name = "Atlas")
    private val state = aGameState(units = listOf(ownUnit, enemyUnit))

    @Test
    fun `own units project as OwnUnit carrying the full CombatUnit`() {
        val projected = state.projectFor(viewer = PlayerId.PLAYER_1)

        val visible = projected.unitById(UnitId("own"))

        assertThat(visible).isInstanceOf(OwnUnit::class.java)
        assertEquals(ownUnit, (visible as OwnUnit).unit)
    }

    @Test
    fun `enemy units project as ForeignUnit`() {
        val projected = state.projectFor(viewer = PlayerId.PLAYER_1)

        val visible = projected.unitById(UnitId("enemy"))

        assertThat(visible).isInstanceOf(ForeignUnit::class.java)
    }

    @Test
    fun `unknown viewer fails closed to ForeignUnit for every unit, including what would be its own`() {
        val projected = state.projectFor(viewer = null)

        assertThat(projected.units).allSatisfy { assertThat(it).isInstanceOf(ForeignUnit::class.java) }
    }

    @Test
    fun `revealAll makes every unit an OwnUnit regardless of owner`() {
        val projected = state.projectFor(viewer = PlayerId.PLAYER_1, revealAll = true)

        assertThat(projected.units).allSatisfy { assertThat(it).isInstanceOf(OwnUnit::class.java) }
        val revealedEnemy = projected.unitById(UnitId("enemy")) as OwnUnit
        assertEquals(enemyUnit, revealedEnemy.unit)
    }

    @Test
    fun `unitById throws for an unknown id`() {
        val projected = state.projectFor(viewer = PlayerId.PLAYER_1)

        assertThrows<IllegalStateException> { projected.unitById(UnitId("unknown")) }
    }

    @Test
    fun `findUnit returns null for an unknown id`() {
        val projected = state.projectFor(viewer = PlayerId.PLAYER_1)

        assertThat(projected.findUnit(UnitId("unknown"))).isNull()
    }

    @Test
    fun `public fields survive the projection intact for a foreign unit`() {
        val projected = state.projectFor(viewer = PlayerId.PLAYER_2)

        val visible = projected.unitById(UnitId("own"))

        assertEquals(HexCoordinates(1, 2), visible.position)
        assertEquals(HexDirection.NE, visible.facing)
        assertEquals(4, visible.walkingMP)
        assertEquals(6, visible.runningMP)
        assertEquals(0, visible.jumpMP)
        assertEquals(9, visible.armor.head)
        assertEquals(47, visible.armor.centerTorso)
        assertEquals(listOf(PublicWeapon("AC/20"), PublicWeapon("Medium Laser")), visible.weapons)
    }

    @Test
    fun `unitsOf filters by owner across the projected mix of Own and Foreign units`() {
        val projected = state.projectFor(viewer = PlayerId.PLAYER_1)

        assertThat(projected.unitsOf(PlayerId.PLAYER_1).map { it.id }).containsExactly(UnitId("own"))
        assertThat(projected.unitsOf(PlayerId.PLAYER_2).map { it.id }).containsExactly(UnitId("enemy"))
    }

    @Test
    fun `activeUnitsOf excludes a shutdown or destroyed unit regardless of ownership`() {
        val shutdownOwn = aUnit(id = "shutdown", owner = PlayerId.PLAYER_1).copy(isShutdown = true)
        val destroyedEnemy = aUnit(id = "destroyed", owner = PlayerId.PLAYER_2, isDestroyed = true)
        val projected = aGameState(units = listOf(shutdownOwn, destroyedEnemy)).projectFor(viewer = PlayerId.PLAYER_1)

        assertThat(projected.activeUnitsOf(PlayerId.PLAYER_1)).isEmpty()
        assertThat(projected.activeUnitsOf(PlayerId.PLAYER_2)).isEmpty()
    }

    @Test
    fun `activeUnitsOf excludes an own unit with an unconscious pilot`() {
        val unconsciousOwn = aUnit(id = "unconscious", owner = PlayerId.PLAYER_1, isPilotConscious = false)
        val projected = aGameState(units = listOf(unconsciousOwn)).projectFor(viewer = PlayerId.PLAYER_1)

        assertThat(projected.activeUnitsOf(PlayerId.PLAYER_1)).isEmpty()
    }
}
