package battletech.tactical.session

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.MechLocation
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.query.mediumLaser
import battletech.tactical.unit.AmmoType
import battletech.tactical.unit.HeatSink
import battletech.tactical.unit.HeatSinkType
import battletech.tactical.unit.Weapons
import battletech.tactical.unit.mechLayout
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
        val unit = aUnit(currentHeat = 40, heatSink = HeatSink(HeatSinkType.STS, 10), weapons = listOf(mediumLaser()))

        val outcome = runHeatPhase(unit, DiceRoller.deterministic(6, 6))

        assertTrue(outcome.state.units[0].isShutdown)
        assertThat(outcome.events).anyMatch { it is UnitShutdown && it.auto }
    }

    @Test
    fun `fails shutdown avoidance and powers down`() {
        // 24 - 10 = 14 -> shutdown roll target 4; roll 2 fails
        val unit = aUnit(currentHeat = 24, heatSink = HeatSink(HeatSinkType.STS, 10), weapons = listOf(mediumLaser()))

        val outcome = runHeatPhase(unit, DiceRoller.deterministic(1, 1))

        assertTrue(outcome.state.units[0].isShutdown)
        assertThat(outcome.events).anyMatch { it is UnitShutdown && !it.auto }
    }

    @Test
    fun `passes shutdown avoidance and stays online`() {
        val unit = aUnit(currentHeat = 24, heatSink = HeatSink(HeatSinkType.STS, 10), weapons = listOf(mediumLaser()))

        val outcome = runHeatPhase(unit, DiceRoller.deterministic(3, 2))

        assertFalse(outcome.state.units[0].isShutdown)
        assertThat(outcome.events).noneMatch { it is UnitShutdown }
    }

    @Test
    fun `auto restart when heat falls below the threshold`() {
        // shutdown unit cools to 0 -> auto restart, no dice
        val unit = aUnit(currentHeat = 10, heatSink = HeatSink(HeatSinkType.STS, 10), weapons = listOf(mediumLaser()))
            .copy(isShutdown = true)

        val outcome = runHeatPhase(unit, DiceRoller.deterministic())

        assertFalse(outcome.state.units[0].isShutdown)
        assertThat(outcome.events).anyMatch { it is UnitRestarted }
    }

    @Test
    fun `ammo explodes on a failed avoidance roll`() {
        // 25 - 10 = 15 -> shutdown target 4 (avoided), ammo target 4 (failed)
        val build = mechLayout {
            place(MechLocation.RIGHT_TORSO, Weapons::ac20)
            ammo(MechLocation.RIGHT_TORSO, AmmoType.AC20, 1)
        }
        val unit = aUnit(
            currentHeat = 25,
            heatSink = HeatSink(HeatSinkType.STS, 10),
            weapons = build.weapons,
            criticalLayout = build.layout,
        )

        val outcome = runHeatPhase(unit, DiceRoller.deterministic(6, 6, 1, 1))

        val exploded = outcome.events.filterIsInstance<AmmoExploded>().single()
        assertEquals(AmmoType.AC20, exploded.ammoType)
        assertEquals(100, exploded.damage) // 5 shots * 20 damage per shot
        val remainingBin = outcome.state.units[0].criticalLayout.ammoBins().single()
        assertEquals(0, remainingBin.third.shots)
    }
}
