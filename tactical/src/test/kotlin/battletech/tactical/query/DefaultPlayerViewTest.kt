package battletech.tactical.query

import battletech.tactical.model.PlayerId
import battletech.tactical.unit.UnitId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class DefaultPlayerViewTest {

    private fun viewFor(vararg units: battletech.tactical.unit.CombatUnit): PlayerView =
        DefaultPlayerView(PlayerId.PLAYER_1, aGameState(units = units.toList()))

    @Test
    fun `publicUnit returns null for unknown unit id`() {
        val view = viewFor(aUnit(id = "u1"))
        assertNull(view.publicUnit(UnitId("unknown")))
    }

    @Test
    fun `publicUnit returns correct scalar fields`() {
        val unit = aUnit(
            id = "u1",
            name = "Hunchback",
            walkingMP = 4,
            runningMP = 6,
            jumpMP = 0,
            armor = anArmorLayout(head = 9, centerTorso = 47),
        )
        val result = viewFor(unit).publicUnit(UnitId("u1"))!!

        assertEquals(UnitId("u1"), result.id)
        assertEquals("Hunchback", result.name)
        assertEquals(4, result.walkingMP)
        assertEquals(6, result.runningMP)
        assertEquals(0, result.jumpMP)
        assertEquals(9, result.armor.head)
        assertEquals(47, result.armor.centerTorso)
    }

    @Test
    fun `publicUnit maps weapon names only`() {
        val unit = aUnit(
            id = "u1",
            weapons = listOf(aWeapon(name = "AC/20"), aWeapon(name = "Medium Laser")),
        )
        val result = viewFor(unit).publicUnit(UnitId("u1"))!!

        assertEquals(listOf(PublicWeapon("AC/20"), PublicWeapon("Medium Laser")), result.weapons)
    }

    @Test
    fun `publicUnit does not expose private fields`() {
        // PublicUnit has no gunnerySkill, pilotingSkill, currentHeat, heatSinkCapacity fields —
        // verified by the fact that this file compiles without referencing them on PublicUnit.
        val unit = aUnit(id = "u1", gunnerySkill = 3, pilotingSkill = 4, currentHeat = 15, heatSinkCapacity = 10)
        val result = viewFor(unit).publicUnit(UnitId("u1"))!!

        // Only the public projection fields are accessible
        assertEquals("Test Mech", result.name)
        assertEquals(listOf("gunnerySkill", "pilotingSkill", "currentHeat", "heatSinkCapacity").none { field ->
            result.javaClass.declaredFields.any { it.name == field }
        }, true)
    }

    @Test
    fun `publicUnit is accessible through PlayerView interface`() {
        val unit = aUnit(id = "u1", name = "Atlas")
        val view: PlayerView = DefaultPlayerView(PlayerId.PLAYER_1, aGameState(units = listOf(unit)))
        assertEquals("Atlas", view.publicUnit(UnitId("u1"))?.name)
    }
}
