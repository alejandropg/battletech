package battletech.tactical.attack.physical

import battletech.tactical.model.HexDirection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AttackDirectionTest {

    // Target faces North. The bearing is the hexside the attacker sits on
    // relative to the target. Front = facing hexside, Rear = opposite,
    // the two clockwise hexsides = Right, the two counter-clockwise = Left.
    @Test
    fun `attacker on the facing hexside is a front attack`() {
        assertEquals(AttackDirection.FRONT, attackDirectionFor(HexDirection.N, HexDirection.N))
    }

    @Test
    fun `attacker on the opposite hexside is a rear attack`() {
        assertEquals(AttackDirection.REAR, attackDirectionFor(HexDirection.N, HexDirection.S))
    }

    @Test
    fun `attacker on a clockwise hexside is a right attack`() {
        assertEquals(AttackDirection.RIGHT, attackDirectionFor(HexDirection.N, HexDirection.NE))
        assertEquals(AttackDirection.RIGHT, attackDirectionFor(HexDirection.N, HexDirection.SE))
    }

    @Test
    fun `attacker on a counter-clockwise hexside is a left attack`() {
        assertEquals(AttackDirection.LEFT, attackDirectionFor(HexDirection.N, HexDirection.SW))
        assertEquals(AttackDirection.LEFT, attackDirectionFor(HexDirection.N, HexDirection.NW))
    }
}
