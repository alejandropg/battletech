package battletech.tactical.model

import battletech.tactical.action.UnitId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class UnitFactoryTest {

    @Test
    fun `createUnit sets instance-specific fields`() {
        val unit = MechModels["AS7-D"].createUnit(
            id = UnitId("test-atlas"),
            position = HexCoordinates(2, 3),
        )

        assertEquals(UnitId("test-atlas"), unit.id)
        assertEquals(HexCoordinates(2, 3), unit.position)
    }

    @Test
    fun `createUnit uses chassis stats from model`() {
        val unit = MechModels["AS7-D"].createUnit(
            id = UnitId("atlas"),
            position = HexCoordinates(0, 0),
        )

        assertEquals("Atlas AS7-D", unit.name)
        assertEquals(3, unit.walkingMP)
        assertEquals(5, unit.runningMP)
        assertEquals(0, unit.jumpMP)
        assertEquals(20, unit.heatSinkCapacity)
    }

    @Test
    fun `createUnit uses default pilot skills`() {
        val unit = MechModels["HBK-4G"].createUnit(
            id = UnitId("hunchback"),
            position = HexCoordinates(0, 0),
        )

        assertEquals(4, unit.gunnerySkill)
        assertEquals(5, unit.pilotingSkill)
    }

    @Test
    fun `createUnit accepts custom pilot skills`() {
        val unit = MechModels["WVR-6R"].createUnit(
            id = UnitId("wolverine"),
            gunnerySkill = 3,
            pilotingSkill = 4,
            position = HexCoordinates(0, 0),
        )

        assertEquals(3, unit.gunnerySkill)
        assertEquals(4, unit.pilotingSkill)
    }

    @Test
    fun `createUnit sets jump MP for jumping mechs`() {
        val unit = MechModels["WVR-6R"].createUnit(
            id = UnitId("wolverine"),
            position = HexCoordinates(0, 0),
        )

        assertEquals(5, unit.jumpMP)
    }

    @Test
    fun `each createUnit call returns independent weapon instances`() {
        val unit1 = MechModels["AS7-D"].createUnit(id = UnitId("a"), position = HexCoordinates(0, 0))
        val unit2 = MechModels["AS7-D"].createUnit(id = UnitId("b"), position = HexCoordinates(1, 1))

        assertNotSame(unit1.weapons[0], unit2.weapons[0])
    }

    @Test
    fun `MechModels lookup by variant string`() {
        val model = MechModels["HBK-4G"]

        assertEquals("HBK-4G", model.variant)
        assertEquals("Hunchback HBK-4G", model.name)
    }

    @Test
    fun `MechModels throws on unknown variant`() {
        assertThrows<IllegalStateException> {
            MechModels["UNKNOWN"]
        }
    }
}
