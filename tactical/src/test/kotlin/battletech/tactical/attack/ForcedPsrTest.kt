package battletech.tactical.attack

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.HexDirection
import battletech.tactical.query.aUnit
import battletech.tactical.session.PilotHit
import battletech.tactical.session.UnitFell
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for [forcedFall] and [forcePsrOrFall] — the centralized fall+pilot-hit helpers.
 */
internal class ForcedPsrTest {

    // ── forcedFall ────────────────────────────────────────────────────────────

    @Test
    fun `forcedFall makes the unit prone, applies fall damage, and emits UnitFell`() {
        val unit = aUnit(tonnage = 50, facing = HexDirection.N)
        // Fall: location 3+4=7 → CENTER_TORSO; facing d6=1 (no rotation).
        // Consciousness 2d6 (3,3 → 6 ≥ target 3 → conscious).
        val roller = DiceRoller.deterministic(3, 4, 1, 3, 3)

        val (fallen, events) = forcedFall(unit, roller)

        assertThat(fallen.isProne).isTrue()
        // Fall damage ceil(50/10)=5 to CENTER_TORSO.
        assertThat(fallen.armor.centerTorso).isEqualTo(unit.armor.centerTorso - 5)
        assertThat(events.filterIsInstance<UnitFell>()).hasSize(1)
        assertThat(events.filterIsInstance<UnitFell>().single().unitId).isEqualTo(unit.id)
    }

    @Test
    fun `forcedFall inflicts exactly one pilot hit and emits PilotHit`() {
        val unit = aUnit(pilotHits = 0)
        // Fall: location 3+4=7 → CENTER_TORSO; facing 1.
        // Hit 1: target 3; roll (3,3)=6 → conscious.
        val roller = DiceRoller.deterministic(3, 4, 1, 3, 3)

        val (fallen, events) = forcedFall(unit, roller)

        assertThat(fallen.pilotHits).isEqualTo(1)
        assertThat(events.filterIsInstance<PilotHit>()).hasSize(1)
        assertThat(events.filterIsInstance<PilotHit>().single().pilotHits).isEqualTo(1)
    }

    @Test
    fun `forcedFall knocks pilot unconscious when consciousness check fails`() {
        val unit = aUnit(pilotHits = 0)
        // Fall: location 3+4=7 → CENTER_TORSO; facing 1.
        // Hit 1: target 3; roll (1,1)=2 < 3 → unconscious.
        val roller = DiceRoller.deterministic(3, 4, 1, 1, 1)

        val (fallen, _) = forcedFall(unit, roller)

        assertThat(fallen.isPilotConscious).isFalse()
    }

    // ── forcePsrOrFall ────────────────────────────────────────────────────────

    @Test
    fun `forcePsrOrFall - passing PSR leaves unit standing and emits no events`() {
        val unit = aUnit(pilotingSkill = 5)
        // PSR TN = pilotingSkill + modifier = 5 + 0 = 5; roll (6,6)=12 ≥ 5 → pass.
        val roller = DiceRoller.deterministic(6, 6)

        val (updated, events) = forcePsrOrFall(unit, modifier = 0, roller = roller)

        assertThat(updated.isProne).isFalse()
        assertThat(events).isEmpty()
    }

    @Test
    fun `forcePsrOrFall - failing PSR causes fall and pilot hit`() {
        val unit = aUnit(pilotingSkill = 5)
        // PSR TN = 5 + 0 = 5; roll (1,1)=2 < 5 → fail.
        // Fall: location 3+4=7 → CENTER_TORSO; facing 1.
        // Consciousness 2d6 (3,3 → 6 ≥ target 3 → conscious).
        val roller = DiceRoller.deterministic(1, 1, 3, 4, 1, 3, 3)

        val (updated, events) = forcePsrOrFall(unit, modifier = 0, roller = roller)

        assertThat(updated.isProne).isTrue()
        assertThat(events.filterIsInstance<UnitFell>()).hasSize(1)
        assertThat(updated.pilotHits).isEqualTo(1)
    }

    @Test
    fun `forcePsrOrFall - modifier shifts the TN and can cause failure at a higher roll`() {
        val unit = aUnit(pilotingSkill = 5)
        // Modifier +3 → TN = 8. Roll (2,4)=6 < 8 → fail.
        // Fall: location 3+4=7 → CT; facing 1; consciousness (3,3).
        val roller = DiceRoller.deterministic(2, 4, 3, 4, 1, 3, 3)

        val (updated, events) = forcePsrOrFall(unit, modifier = 3, roller = roller)

        assertThat(updated.isProne).isTrue()
        assertThat(events.filterIsInstance<UnitFell>()).hasSize(1)
    }

    @Test
    fun `forcePsrOrFall - already prone unit skips PSR and emits no events`() {
        val unit = aUnit(pilotingSkill = 5).copy(isProne = true)
        val roller = DiceRoller.deterministic() // must not consume any dice

        val (updated, events) = forcePsrOrFall(unit, modifier = 0, roller = roller)

        assertThat(updated.isProne).isTrue()
        assertThat(events).isEmpty()
    }
}
