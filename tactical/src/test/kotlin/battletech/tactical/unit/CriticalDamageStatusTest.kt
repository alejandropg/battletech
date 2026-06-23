package battletech.tactical.unit

import battletech.tactical.model.MechLocation
import battletech.tactical.query.aUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CriticalDamageStatusTest {

    @Test
    fun `undamaged unit reports zero hits and no penalties for every component`() {
        val unit = aUnit()

        val statuses = unit.criticalDamageStatus()

        assertThat(statuses.map { it.component })
            .containsExactly(
                CriticalComponent.ENGINE,
                CriticalComponent.GYRO,
                CriticalComponent.SENSOR,
                CriticalComponent.LIFE_SUPPORT,
            )
        assertThat(statuses).allSatisfy { status ->
            assertThat(status.hits).isZero()
            assertThat(status.penalties).isEmpty()
        }
    }

    @Test
    fun `engine status reports capacity 3 and heat-per-turn penalty`() {
        // CENTER_TORSO framework: Engine at indices 0,1,2 and 7,8,9.
        val oneHit = aUnit().copy(criticalHits = mapOf(MechLocation.CENTER_TORSO to setOf(0)))
        val twoHits = aUnit().copy(criticalHits = mapOf(MechLocation.CENTER_TORSO to setOf(0, 7)))
        val threeHits = aUnit().copy(criticalHits = mapOf(MechLocation.CENTER_TORSO to setOf(0, 7, 8)))

        val engineOf = { unit: CombatUnit -> unit.criticalDamageStatus().first { it.component == CriticalComponent.ENGINE } }

        val oneStatus = engineOf(oneHit)
        assertThat(oneStatus.capacity).isEqualTo(3)
        assertThat(oneStatus.hits).isEqualTo(1)
        assertThat(oneStatus.penalties).containsExactly("+5 Heat/turn")

        val twoStatus = engineOf(twoHits)
        assertThat(twoStatus.hits).isEqualTo(2)
        assertThat(twoStatus.penalties).containsExactly("+10 Heat/turn")

        val threeStatus = engineOf(threeHits)
        assertThat(threeStatus.hits).isEqualTo(3)
        // 3 engine crits destroy the unit (see DestructionTest) rather than report an
        // ongoing penalty here — that unit is eliminated and shown via the destruction path.
        assertThat(threeStatus.penalties).isEmpty()
    }

    @Test
    fun `gyro status reports capacity 2 - 1 crit is PSR penalty, 2 crits cannot stand`() {
        // CENTER_TORSO framework: Gyro at indices 3,4,5,6.
        val oneHit = aUnit().copy(criticalHits = mapOf(MechLocation.CENTER_TORSO to setOf(3)))
        val twoHits = aUnit().copy(criticalHits = mapOf(MechLocation.CENTER_TORSO to setOf(3, 4)))

        val gyroOf = { unit: CombatUnit -> unit.criticalDamageStatus().first { it.component == CriticalComponent.GYRO } }

        val oneStatus = gyroOf(oneHit)
        assertThat(oneStatus.capacity).isEqualTo(2)
        assertThat(oneStatus.hits).isEqualTo(1)
        assertThat(oneStatus.penalties).containsExactly("+3 PSR")

        val twoStatus = gyroOf(twoHits)
        assertThat(twoStatus.hits).isEqualTo(2)
        assertThat(twoStatus.penalties).contains("Cannot stand")
    }

    @Test
    fun `sensor status reports capacity 2 - 1 crit is to-hit penalty, 2 crits cannot fire`() {
        // HEAD framework: Sensors at indices 1 and 4.
        val oneHit = aUnit().copy(criticalHits = mapOf(MechLocation.HEAD to setOf(1)))
        val twoHits = aUnit().copy(criticalHits = mapOf(MechLocation.HEAD to setOf(1, 4)))

        val sensorOf = { unit: CombatUnit -> unit.criticalDamageStatus().first { it.component == CriticalComponent.SENSOR } }

        val oneStatus = sensorOf(oneHit)
        assertThat(oneStatus.capacity).isEqualTo(2)
        assertThat(oneStatus.hits).isEqualTo(1)
        assertThat(oneStatus.penalties).containsExactly("+2 To-Hit")

        val twoStatus = sensorOf(twoHits)
        assertThat(twoStatus.hits).isEqualTo(2)
        assertThat(twoStatus.penalties).containsExactly("Cannot fire")
    }

    @Test
    fun `life support status reports capacity 2 - 1 crit is heat-threshold hit, 2 crits is per-turn hit`() {
        // HEAD framework: LifeSupport at indices 0 and 5.
        val oneHit = aUnit().copy(criticalHits = mapOf(MechLocation.HEAD to setOf(0)))
        val twoHits = aUnit().copy(criticalHits = mapOf(MechLocation.HEAD to setOf(0, 5)))

        val lifeSupportOf =
            { unit: CombatUnit -> unit.criticalDamageStatus().first { it.component == CriticalComponent.LIFE_SUPPORT } }

        val oneStatus = lifeSupportOf(oneHit)
        assertThat(oneStatus.capacity).isEqualTo(2)
        assertThat(oneStatus.hits).isEqualTo(1)
        assertThat(oneStatus.penalties).containsExactly("Pilot hit @ 15+ heat")

        val twoStatus = lifeSupportOf(twoHits)
        assertThat(twoStatus.hits).isEqualTo(2)
        assertThat(twoStatus.penalties).containsExactly("Pilot hit / turn")
    }
}
