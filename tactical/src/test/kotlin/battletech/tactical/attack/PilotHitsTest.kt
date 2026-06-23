package battletech.tactical.attack

import battletech.tactical.dice.DiceRoller
import battletech.tactical.query.aUnit
import battletech.tactical.session.PilotHit
import battletech.tactical.session.PilotKnockedUnconscious
import battletech.tactical.session.PilotRecoveredConsciousness
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PilotHitsTest {

    @Test
    fun `applyPilotHit increments pilotHits`() {
        val unit = aUnit(pilotHits = 0)

        // 1st hit -> target 3; roll (3,3)=6 passes
        val (updated, _) = applyPilotHit(unit, DiceRoller.deterministic(3, 3))

        assertThat(updated.pilotHits).isEqualTo(1)
    }

    @Test
    fun `a failed consciousness roll sets isPilotConscious false and emits the events`() {
        // 1st hit -> target 3; roll (1,1)=2 fails
        val unit = aUnit(pilotHits = 0)

        val (updated, events) = applyPilotHit(unit, DiceRoller.deterministic(1, 1))

        assertThat(updated.isPilotConscious).isFalse()
        val hitEvent = events.filterIsInstance<PilotHit>().single()
        assertThat(hitEvent.pilotHits).isEqualTo(1)
        assertThat(hitEvent.conscious).isFalse()
        assertThat(events.filterIsInstance<PilotKnockedUnconscious>()).hasSize(1)
    }

    @Test
    fun `a passed consciousness roll keeps the pilot conscious`() {
        // 1st hit -> target 3; roll (3,3)=6 passes
        val unit = aUnit(pilotHits = 0)

        val (updated, events) = applyPilotHit(unit, DiceRoller.deterministic(3, 3))

        assertThat(updated.isPilotConscious).isTrue()
        assertThat(events.filterIsInstance<PilotKnockedUnconscious>()).isEmpty()
        val hitEvent = events.filterIsInstance<PilotHit>().single()
        assertThat(hitEvent.conscious).isTrue()
    }

    @Test
    fun `reaching the death threshold rolls no consciousness check`() {
        val unit = aUnit(pilotHits = 5)

        val (updated, events) = applyPilotHit(unit, DiceRoller.deterministic())

        assertThat(updated.pilotHits).isEqualTo(6)
        val hitEvent = events.filterIsInstance<PilotHit>().single()
        assertThat(hitEvent.consciousnessRoll).isNull()
    }

    @Test
    fun `consciousness recovery succeeds and emits PilotRecoveredConsciousness`() {
        // pilotHits = 1 -> recovery target 3; roll (3,3)=6 passes
        val unit = aUnit(pilotHits = 1, isPilotConscious = false)

        val (updated, event) = attemptConsciousnessRecovery(unit, DiceRoller.deterministic(3, 3))

        assertThat(updated.isPilotConscious).isTrue()
        assertThat(event).isInstanceOf(PilotRecoveredConsciousness::class.java)
    }

    @Test
    fun `consciousness recovery failure stays unconscious and emits no event`() {
        // pilotHits = 1 -> recovery target 3; roll (1,1)=2 fails
        val unit = aUnit(pilotHits = 1, isPilotConscious = false)

        val (updated, event) = attemptConsciousnessRecovery(unit, DiceRoller.deterministic(1, 1))

        assertThat(updated.isPilotConscious).isFalse()
        assertThat(event).isNull()
    }
}
