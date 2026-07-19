package battletech.tactical.unit

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.query.aUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class UnitRosterTest {

    @Test
    fun `at returns the unit occupying a position`() {
        val here = aUnit(id = "here", position = HexCoordinates(1, 1))
        val elsewhere = aUnit(id = "elsewhere", position = HexCoordinates(2, 2))
        val roster = UnitRoster(listOf(here, elsewhere))

        assertThat(roster.at(HexCoordinates(1, 1))).isEqualTo(here)
    }

    @Test
    fun `at returns null when no unit occupies the position`() {
        val roster = UnitRoster(listOf(aUnit(position = HexCoordinates(1, 1))))

        assertThat(roster.at(HexCoordinates(9, 9))).isNull()
    }

    @Test
    fun `byId returns the unit with the matching id`() {
        val unit = aUnit(id = "target")
        val roster = UnitRoster(listOf(aUnit(id = "other"), unit))

        assertThat(roster.byId(UnitId("target"))).isEqualTo(unit)
    }

    @Test
    fun `byId throws UnknownUnitException when id is absent`() {
        val roster = UnitRoster(listOf(aUnit(id = "present")))

        assertThrows<UnknownUnitException> { roster.byId(UnitId("missing")) }
    }

    @Test
    fun `of filters to units owned by the given player`() {
        val p1 = aUnit(id = "p1", owner = PlayerId.PLAYER_1)
        val p2 = aUnit(id = "p2", owner = PlayerId.PLAYER_2)
        val roster = UnitRoster(listOf(p1, p2))

        assertThat(roster.of(PlayerId.PLAYER_1).all).containsExactly(p1)
    }

    @Test
    fun `activeOf excludes shutdown, destroyed, and unconscious-pilot units`() {
        val active = aUnit(id = "active", owner = PlayerId.PLAYER_1)
        val shutdown = aUnit(id = "shutdown", owner = PlayerId.PLAYER_1).copy(isShutdown = true)
        val destroyed = aUnit(id = "destroyed", owner = PlayerId.PLAYER_1, isDestroyed = true)
        val unconscious = aUnit(id = "unconscious", owner = PlayerId.PLAYER_1, isPilotConscious = false)
        val roster = UnitRoster(listOf(active, shutdown, destroyed, unconscious))

        assertThat(roster.activeOf(PlayerId.PLAYER_1).all).containsExactly(active)
    }

    @Test
    fun `activeOf excludes units belonging to other players`() {
        val ownUnit = aUnit(id = "own", owner = PlayerId.PLAYER_1)
        val enemyUnit = aUnit(id = "enemy", owner = PlayerId.PLAYER_2)
        val roster = UnitRoster(listOf(ownUnit, enemyUnit))

        assertThat(roster.activeOf(PlayerId.PLAYER_1).all).containsExactly(ownUnit)
    }

    @Test
    fun `enemiesOf returns units owned by someone else, excluding destroyed ones`() {
        val self = aUnit(id = "self", owner = PlayerId.PLAYER_1)
        val ally = aUnit(id = "ally", owner = PlayerId.PLAYER_1)
        val liveEnemy = aUnit(id = "live-enemy", owner = PlayerId.PLAYER_2)
        val deadEnemy = aUnit(id = "dead-enemy", owner = PlayerId.PLAYER_2, isDestroyed = true)
        val roster = UnitRoster(listOf(self, ally, liveEnemy, deadEnemy))

        assertThat(roster.enemiesOf(self).all).containsExactly(liveEnemy)
    }

    @Test
    fun `withUnit replaces the unit sharing the given id and leaves others untouched`() {
        val original = aUnit(id = "target", currentHeat = 0)
        val other = aUnit(id = "other")
        val roster = UnitRoster(listOf(original, other))

        val updated = original.copy(currentHeat = 5)

        assertThat(roster.withUnit(updated).all).containsExactly(updated, other)
    }

    @Test
    fun `withUnits bulk-replaces every unit sharing an id, leaving the rest untouched`() {
        val a = aUnit(id = "a", currentHeat = 0)
        val b = aUnit(id = "b", currentHeat = 0)
        val c = aUnit(id = "c", currentHeat = 0)
        val roster = UnitRoster(listOf(a, b, c))

        val updatedA = a.copy(currentHeat = 3)
        val updatedC = c.copy(currentHeat = 7)

        assertThat(roster.withUnits(listOf(updatedA, updatedC)).all).containsExactly(updatedA, b, updatedC)
    }

    @Test
    fun `mapUnits transforms every unit in place`() {
        val a = aUnit(id = "a", currentHeat = 0)
        val b = aUnit(id = "b", currentHeat = 0)
        val roster = UnitRoster(listOf(a, b))

        val mapped = roster.mapUnits { it.copy(currentHeat = it.currentHeat + 1) }

        assertThat(mapped.all).containsExactly(a.copy(currentHeat = 1), b.copy(currentHeat = 1))
    }

    @Test
    fun `equals and hashCode delegate to the unit list`() {
        val units = listOf(aUnit(id = "a"), aUnit(id = "b"))

        assertThat(UnitRoster(units)).isEqualTo(UnitRoster(units.toList()))
        assertThat(UnitRoster(units).hashCode()).isEqualTo(UnitRoster(units.toList()).hashCode())
    }

    @Test
    fun `size, isEmpty, and isNotEmpty reflect the underlying list`() {
        val empty = UnitRoster<CombatUnit>(emptyList())
        val nonEmpty = UnitRoster(listOf(aUnit()))

        assertThat(empty.size).isEqualTo(0)
        assertThat(empty.isEmpty()).isTrue()
        assertThat(nonEmpty.isNotEmpty()).isTrue()
        assertThat(nonEmpty.size).isEqualTo(1)
    }

    @Test
    fun `is iterable over its units`() {
        val a = aUnit(id = "a")
        val b = aUnit(id = "b")

        assertThat(UnitRoster(listOf(a, b)).toList()).containsExactly(a, b)
    }
}
