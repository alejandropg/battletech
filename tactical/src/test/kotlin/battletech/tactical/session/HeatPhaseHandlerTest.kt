package battletech.tactical.session

import battletech.tactical.dice.DiceRoller
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.query.aWeapon
import battletech.tactical.query.mediumLaser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class HeatPhaseHandlerTest {

    private val handler = HeatPhaseHandler()

    private fun runHeatPhase(unit: battletech.tactical.unit.CombatUnit, roller: DiceRoller) =
        handler.onEntry(aGameState(units = listOf(unit)), TurnState.NULL, roller)

    @Test
    fun `auto shutdown at heat thirty without a shutdown roll`() {
        // 40 - 10 dissipation = 30 -> auto shutdown; ammo roll still happens (no ammo weapon)
        val unit = aUnit(currentHeat = 40, heatSinkCapacity = 10, weapons = listOf(mediumLaser()))

        val outcome = runHeatPhase(unit, DiceRoller.deterministic(6, 6))

        assertTrue(outcome.state.units[0].isShutdown)
        assertThat(outcome.events).anyMatch { it is UnitShutdown && it.auto }
    }

    @Test
    fun `fails shutdown avoidance and powers down`() {
        // 24 - 10 = 14 -> shutdown roll target 4; roll 2 fails
        val unit = aUnit(currentHeat = 24, heatSinkCapacity = 10, weapons = listOf(mediumLaser()))

        val outcome = runHeatPhase(unit, DiceRoller.deterministic(1, 1))

        assertTrue(outcome.state.units[0].isShutdown)
        assertThat(outcome.events).anyMatch { it is UnitShutdown && !it.auto }
    }

    @Test
    fun `passes shutdown avoidance and stays online`() {
        val unit = aUnit(currentHeat = 24, heatSinkCapacity = 10, weapons = listOf(mediumLaser()))

        val outcome = runHeatPhase(unit, DiceRoller.deterministic(3, 2))

        assertFalse(outcome.state.units[0].isShutdown)
        assertThat(outcome.events).noneMatch { it is UnitShutdown }
    }

    @Test
    fun `auto restart when heat falls below the threshold`() {
        // shutdown unit cools to 0 -> auto restart, no dice
        val unit = aUnit(currentHeat = 10, heatSinkCapacity = 10, weapons = listOf(mediumLaser()))
            .copy(isShutdown = true)

        val outcome = runHeatPhase(unit, DiceRoller.deterministic())

        assertFalse(outcome.state.units[0].isShutdown)
        assertThat(outcome.events).anyMatch { it is UnitRestarted }
    }

    @Test
    fun `ammo explodes on a failed avoidance roll`() {
        // 25 - 10 = 15 -> shutdown target 4 (avoided), ammo target 4 (failed)
        val ammoWeapon = aWeapon(name = "AC/20", damage = 2, ammo = 5)
        val unit = aUnit(currentHeat = 25, heatSinkCapacity = 10, weapons = listOf(ammoWeapon))

        val outcome = runHeatPhase(unit, DiceRoller.deterministic(6, 6, 1, 1))

        val exploded = outcome.events.filterIsInstance<AmmoExploded>().single()
        assertEquals("AC/20", exploded.weaponName)
        assertEquals(10, exploded.damage) // 5 rounds * 2 damage
        assertEquals(0, outcome.state.units[0].weapons[0].ammo) // ammo spent
    }
}
