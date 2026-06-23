package battletech.tactical.attack.physical

import battletech.tactical.attack.physical.PhysicalAttackKind.Punch
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain
import battletech.tactical.model.MovementMode
import battletech.tactical.unit.MovementThisTurn
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.unit.CombatUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PhysicalToHitTest {

    private val attackerPos = HexCoordinates(0, 0)
    private val targetPos = HexCoordinates(1, 0)

    private fun attacker(piloting: Int = 4, movement: MovementThisTurn = MovementThisTurn.STATIONARY) =
        aUnit(id = "attacker", pilotingSkill = piloting, position = attackerPos)
            .copy(movementThisTurn = movement)

    private fun target(movement: MovementThisTurn = MovementThisTurn.STATIONARY) =
        aUnit(id = "target", position = targetPos).copy(movementThisTurn = movement)

    private fun punchTn(attacker: CombatUnit, target: CombatUnit, hexes: Map<HexCoordinates, Hex> = emptyMap()): Int =
        physicalToHitTargetNumber(attacker, target, Punch(Side.LEFT), aGameState(units = listOf(attacker, target), hexes = hexes))

    @Test
    fun `a stationary attacker against a stationary target is just the piloting skill`() {
        assertEquals(4, punchTn(attacker(piloting = 4), target()))
    }

    @Test
    fun `a walking attacker adds one`() {
        assertEquals(5, punchTn(attacker(movement = MovementThisTurn(MovementMode.WALK, 2)), target()))
    }

    @Test
    fun `a running attacker adds two`() {
        assertEquals(6, punchTn(attacker(movement = MovementThisTurn(MovementMode.RUN, 5)), target()))
    }

    @Test
    fun `target movement modifier follows the standard hex bands`() {
        assertEquals(4, punchTn(attacker(), target(MovementThisTurn(MovementMode.WALK, 2)))) // 0-2 -> +0
        assertEquals(5, punchTn(attacker(), target(MovementThisTurn(MovementMode.WALK, 4)))) // 3-4 -> +1
        assertEquals(6, punchTn(attacker(), target(MovementThisTurn(MovementMode.RUN, 6)))) // 5-6 -> +2
        assertEquals(7, punchTn(attacker(), target(MovementThisTurn(MovementMode.RUN, 9)))) // 7-9 -> +3
        assertEquals(8, punchTn(attacker(), target(MovementThisTurn(MovementMode.RUN, 17)))) // 10-17 -> +4
    }

    @Test
    fun `a jumping target adds one on top of its hex-based modifier`() {
        // 4 hexes -> +1 band, +1 for jumping = +2.
        assertEquals(6, punchTn(attacker(), target(MovementThisTurn(MovementMode.JUMP, 4))))
    }

    @Test
    fun `a prone adjacent target is easier to hit`() {
        val proneTarget = target().copy(isProne = true)
        // piloting 4, adjacent prone target -> -2.
        assertEquals(2, punchTn(attacker(), proneTarget))
    }

    @Test
    fun `woods on the target hex add to the target number`() {
        val light = mapOf(targetPos to Hex(targetPos, Terrain.LIGHT_WOODS))
        val heavy = mapOf(targetPos to Hex(targetPos, Terrain.HEAVY_WOODS))

        assertEquals(5, punchTn(attacker(), target(), light)) // +1
        assertEquals(6, punchTn(attacker(), target(), heavy)) // +2
    }
}
