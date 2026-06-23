package battletech.tactical.unit

import battletech.tactical.model.MechLocation
import battletech.tactical.query.aUnit
import battletech.tactical.query.anInternalStructureLayout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class DestructionTest {

    @Test
    fun `head internal structure at 0 returns HEAD_DESTROYED`() {
        val unit = aUnit(internalStructure = anInternalStructureLayout(head = 0))

        assertThat(destructionReason(unit)).isEqualTo(DestructionReason.HEAD_DESTROYED)
    }

    @Test
    fun `center torso internal structure at 0 returns CENTER_TORSO_DESTROYED`() {
        val unit = aUnit(internalStructure = anInternalStructureLayout(centerTorso = 0))

        assertThat(destructionReason(unit)).isEqualTo(DestructionReason.CENTER_TORSO_DESTROYED)
    }

    @Test
    fun `both legs at 0 internal structure returns BOTH_LEGS_DESTROYED`() {
        val unit = aUnit(internalStructure = anInternalStructureLayout(leftLeg = 0, rightLeg = 0))

        assertThat(destructionReason(unit)).isEqualTo(DestructionReason.BOTH_LEGS_DESTROYED)
    }

    @Test
    fun `only one leg destroyed returns null`() {
        val unit = aUnit(internalStructure = anInternalStructureLayout(leftLeg = 0))

        assertThat(destructionReason(unit)).isNull()
    }

    @Test
    fun `fully intact unit returns null`() {
        val unit = aUnit()

        assertThat(destructionReason(unit)).isNull()
    }

    @Test
    fun `3 engine crits returns ENGINE_DESTROYED`() {
        // CENTER_TORSO framework: Engine at indices 0,1,2 and 7,8,9.
        val unit = aUnit().copy(criticalHits = mapOf(MechLocation.CENTER_TORSO to setOf(0, 1, 2)))

        assertThat(destructionReason(unit)).isEqualTo(DestructionReason.ENGINE_DESTROYED)
    }

    @Test
    fun `2 gyro crits do not eliminate the unit (immobilized, not destroyed)`() {
        // CENTER_TORSO framework: Gyro at indices 3,4,5,6. A shattered gyro crashes the
        // mech prone and it can never stand, but it keeps fighting — never eliminated.
        val unit = aUnit().copy(criticalHits = mapOf(MechLocation.CENTER_TORSO to setOf(3, 4)))

        assertThat(destructionReason(unit)).isNull()
    }

    @Test
    fun `1 engine crit returns null, not ENGINE_DESTROYED`() {
        val unit = aUnit().copy(criticalHits = mapOf(MechLocation.CENTER_TORSO to setOf(0)))

        assertThat(destructionReason(unit)).isNull()
    }

    @Test
    fun `1 gyro crit does not eliminate the unit`() {
        val unit = aUnit().copy(criticalHits = mapOf(MechLocation.CENTER_TORSO to setOf(3)))

        assertThat(destructionReason(unit)).isNull()
    }

    @Test
    fun `pilotHits at 6 returns PILOT_DEAD`() {
        val unit = aUnit(pilotHits = 6)

        assertThat(destructionReason(unit)).isEqualTo(DestructionReason.PILOT_DEAD)
    }

    @Test
    fun `pilotHits below 6 returns null, not PILOT_DEAD`() {
        val unit = aUnit(pilotHits = 5)

        assertThat(destructionReason(unit)).isNull()
    }
}
