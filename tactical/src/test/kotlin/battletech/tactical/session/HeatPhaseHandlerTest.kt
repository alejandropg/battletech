package battletech.tactical.session

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.MechLocation
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.query.mediumLaser
import battletech.tactical.unit.AmmoType
import battletech.tactical.unit.HeatSink
import battletech.tactical.unit.HeatSinkType
import battletech.tactical.unit.WeaponModels
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

        assertTrue(outcome.state.units.all[0].isShutdown)
        assertThat(outcome.events).anyMatch { it is UnitShutdown.Automatic }
    }

    @Test
    fun `fails shutdown avoidance and powers down`() {
        // 24 - 10 = 14 -> shutdown roll target 4; roll 2 fails
        val unit = aUnit(currentHeat = 24, heatSink = HeatSink(HeatSinkType.STS, 10), weapons = listOf(mediumLaser()))

        val outcome = runHeatPhase(unit, DiceRoller.deterministic(1, 1))

        assertTrue(outcome.state.units.all[0].isShutdown)
        assertThat(outcome.events).anyMatch { it is UnitShutdown.AvoidFailed }
    }

    @Test
    fun `passes shutdown avoidance and stays online`() {
        val unit = aUnit(currentHeat = 24, heatSink = HeatSink(HeatSinkType.STS, 10), weapons = listOf(mediumLaser()))

        val outcome = runHeatPhase(unit, DiceRoller.deterministic(3, 2))

        assertFalse(outcome.state.units.all[0].isShutdown)
        assertThat(outcome.events).noneMatch { it is UnitShutdown }
    }

    @Test
    fun `auto restart when heat falls below the threshold`() {
        // shutdown unit cools to 0 -> auto restart, no dice
        val unit = aUnit(currentHeat = 10, heatSink = HeatSink(HeatSinkType.STS, 10), weapons = listOf(mediumLaser()))
            .copy(isShutdown = true)

        val outcome = runHeatPhase(unit, DiceRoller.deterministic())

        assertFalse(outcome.state.units.all[0].isShutdown)
        assertThat(outcome.events).anyMatch { it is UnitRestarted }
    }

    @Test
    fun `ammo explodes on a failed avoidance roll`() {
        // 25 - 10 = 15 -> shutdown target 4 (avoided), ammo target 4 (failed)
        val build = mechLayout {
            place(MechLocation.RIGHT_TORSO, WeaponModels.ac20)
            ammo(MechLocation.RIGHT_TORSO, AmmoType.AC20, 1)
        }
        val unit = aUnit(
            currentHeat = 25,
            heatSink = HeatSink(HeatSinkType.STS, 10),
            weapons = build.weapons,
            criticalLayout = build.layout,
        )

        // shutdown avoidance (6+6 → 12 ≥ 4, avoided); ammo avoidance (1+1 → 2 < 4, explodes);
        // pilot hit 1 consciousness 2d6 (3,3 → 6 ≥ target 3); pilot hit 2 consciousness 2d6 (3,3 → 6 ≥ target 5).
        val outcome = runHeatPhase(unit, DiceRoller.deterministic(6, 6, 1, 1, 3, 3, 3, 3))

        val exploded = outcome.events.filterIsInstance<AmmoExploded.Detailed>().single()
        assertEquals(AmmoType.AC20, exploded.ammoType)
        assertEquals(100, exploded.damage) // 5 shots * 20 damage per shot
        val remainingBin = outcome.state.units.all[0].criticalLayout.ammoBins().single()
        assertEquals(0, remainingBin.third.shots)
    }

    @Test
    fun `1 engine crit adds 5 heat during the heat fold`() {
        // CENTER_TORSO framework: Engine at indices 0,1,2 and 7,8,9.
        // No dissipation (0 heat sinks) so the +5 engine heat is visible undamped.
        val unit = aUnit(
            currentHeat = 0,
            heatSink = HeatSink(HeatSinkType.STS, 0),
            weapons = listOf(mediumLaser()),
        ).copy(criticalHits = mapOf(MechLocation.CENTER_TORSO to setOf(0)))

        val outcome = runHeatPhase(unit, DiceRoller.deterministic())

        assertEquals(5, outcome.state.units.all[0].currentHeat)
    }

    @Test
    fun `2 engine crits add 10 heat during the heat fold`() {
        val unit = aUnit(
            currentHeat = 0,
            heatSink = HeatSink(HeatSinkType.STS, 0),
            weapons = listOf(mediumLaser()),
        ).copy(criticalHits = mapOf(MechLocation.CENTER_TORSO to setOf(0, 1)))

        val outcome = runHeatPhase(unit, DiceRoller.deterministic())

        assertEquals(10, outcome.state.units.all[0].currentHeat)
    }

    @Test
    fun `engine heat can push a unit into a shutdown roll`() {
        // 2 engine crits = +10 heat; 14 base - 10 dissipation + 10 engine = 14 -> shutdown
        // roll target 4 (HeatScale.shutdownAvoidTarget(14) == 4); roll 1+1 fails -> shutdown.
        val unit = aUnit(
            currentHeat = 14,
            heatSink = HeatSink(HeatSinkType.STS, 10),
            weapons = listOf(mediumLaser()),
        ).copy(criticalHits = mapOf(MechLocation.CENTER_TORSO to setOf(0, 1)))

        val outcome = runHeatPhase(unit, DiceRoller.deterministic(1, 1))

        assertEquals(14, outcome.state.units.all[0].currentHeat)
        assertTrue(outcome.state.units.all[0].isShutdown)
        assertThat(outcome.events).anyMatch { it is UnitShutdown.AvoidFailed }
    }

    // -------------------------------------------------------------------------
    // Stage 7: life-support pilot damage
    // -------------------------------------------------------------------------

    @Test
    fun `1st life support crit with heat 15 or higher gives the pilot 1 hit`() {
        // HEAD framework: LifeSupport at indices 0 and 5. currentHeat 15, no
        // heat sinks so it stays at 15 after the fold; no engine crits so no
        // extra heat. Heat 15 also requires a shutdown-avoidance roll
        // (HeatScale.shutdownAvoidTarget(15) == 4); roll (3,3)=6 avoids it.
        // Pilot hit #1 -> consciousness target 3; roll (3,3)=6 passes. Heat 15
        // also requires an ammo-explosion-avoidance roll
        // (HeatScale.ammoExplosionAvoidTarget(15) == 4, rolled even with no
        // ammo-using weapons aboard); roll (3,3)=6 avoids it (resolved AFTER
        // the pilot hit, per the documented dice order).
        val unit = aUnit(
            currentHeat = 15,
            heatSink = HeatSink(HeatSinkType.STS, 0),
            weapons = listOf(mediumLaser()),
        ).copy(criticalHits = mapOf(MechLocation.HEAD to setOf(0)))

        val outcome = runHeatPhase(unit, DiceRoller.deterministic(3, 3, 3, 3, 3, 3))

        assertEquals(1, outcome.state.units.all[0].pilotHits)
        assertThat(outcome.events).anyMatch { it is PilotHit.Checked && it.pilotHits == 1 }
    }

    @Test
    fun `1st life support crit with heat below 15 gives no pilot hit`() {
        val unit = aUnit(
            currentHeat = 10,
            heatSink = HeatSink(HeatSinkType.STS, 0),
            weapons = listOf(mediumLaser()),
        ).copy(criticalHits = mapOf(MechLocation.HEAD to setOf(0)))

        val outcome = runHeatPhase(unit, DiceRoller.deterministic())

        assertEquals(0, outcome.state.units.all[0].pilotHits)
        assertThat(outcome.events).noneMatch { it is PilotHit }
    }

    @Test
    fun `2nd life support crit gives the pilot 1 hit regardless of heat`() {
        // HEAD framework: LifeSupport at indices 0 and 5.
        val unit = aUnit(
            currentHeat = 0,
            heatSink = HeatSink(HeatSinkType.STS, 0),
            weapons = listOf(mediumLaser()),
        ).copy(criticalHits = mapOf(MechLocation.HEAD to setOf(0, 5)))

        // Pilot hit #1 -> consciousness target 3; roll (3,3)=6 passes.
        val outcome = runHeatPhase(unit, DiceRoller.deterministic(3, 3))

        assertEquals(1, outcome.state.units.all[0].pilotHits)
        assertThat(outcome.events).anyMatch { it is PilotHit.Checked && it.pilotHits == 1 }
    }

    // -------------------------------------------------------------------------
    // Stage 7: consciousness recovery
    // -------------------------------------------------------------------------

    @Test
    fun `an unconscious alive pilot with a scripted successful roll wakes up`() {
        // pilotHits = 1 -> recovery target 3; roll (3,3)=6 passes. No LS crits,
        // 0 heat, no engine crits -> no other dice consumed.
        val unit = aUnit(
            currentHeat = 0,
            heatSink = HeatSink(HeatSinkType.STS, 0),
            weapons = listOf(mediumLaser()),
            pilotHits = 1,
            isPilotConscious = false,
        )

        val outcome = runHeatPhase(unit, DiceRoller.deterministic(3, 3))

        assertTrue(outcome.state.units.all[0].isPilotConscious)
        assertThat(outcome.events).anyMatch { it is PilotRecoveredConsciousness }
    }

    @Test
    fun `an unconscious alive pilot with a scripted failed roll stays unconscious`() {
        // pilotHits = 1 -> recovery target 3; roll (1,1)=2 fails.
        val unit = aUnit(
            currentHeat = 0,
            heatSink = HeatSink(HeatSinkType.STS, 0),
            weapons = listOf(mediumLaser()),
            pilotHits = 1,
            isPilotConscious = false,
        )

        val outcome = runHeatPhase(unit, DiceRoller.deterministic(1, 1))

        assertFalse(outcome.state.units.all[0].isPilotConscious)
        assertThat(outcome.events).noneMatch { it is PilotRecoveredConsciousness }
    }

    @Test
    fun `a conscious unit with no pilot hits and no LS crits rolls no pilot dice`() {
        // Regression guard: untouched fixtures must not consume extra dice for
        // the new Stage 7 steps.
        val unit = aUnit(
            currentHeat = 0,
            heatSink = HeatSink(HeatSinkType.STS, 0),
            weapons = listOf(mediumLaser()),
        )

        val outcome = runHeatPhase(unit, DiceRoller.deterministic())

        assertThat(outcome.events).noneMatch {
            it is PilotHit || it is PilotKnockedUnconscious || it is PilotRecoveredConsciousness
        }
    }
}
