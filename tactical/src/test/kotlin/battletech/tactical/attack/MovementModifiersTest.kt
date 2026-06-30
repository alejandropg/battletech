package battletech.tactical.attack

import battletech.tactical.model.MovementMode
import battletech.tactical.unit.MovementThisTurn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MovementModifiersTest {

    // --- attackerMovementModifier ---

    @Test
    fun `stationary attacker contributes zero`() {
        assertEquals(0, attackerMovementModifier(null))
    }

    @Test
    fun `walking attacker adds one`() {
        assertEquals(1, attackerMovementModifier(MovementMode.WALK))
    }

    @Test
    fun `running attacker adds two`() {
        assertEquals(2, attackerMovementModifier(MovementMode.RUN))
    }

    @Test
    fun `jumping attacker adds three`() {
        assertEquals(3, attackerMovementModifier(MovementMode.JUMP))
    }

    // --- targetMovementModifier ---

    @Test
    fun `stationary target contributes zero`() {
        assertEquals(0, targetMovementModifier(MovementThisTurn.STATIONARY))
    }

    @Test
    fun `target moved two hexes is in 0-2 band and contributes zero`() {
        assertEquals(0, targetMovementModifier(MovementThisTurn(MovementMode.WALK, 2)))
    }

    @Test
    fun `target ran five hexes is in 5-6 band and adds two`() {
        assertEquals(2, targetMovementModifier(MovementThisTurn(MovementMode.RUN, 5)))
    }

    @Test
    fun `target jumped four hexes applies band plus one for jumping`() {
        // 4 hexes → band +1, jump bonus +1 = +2
        assertEquals(2, targetMovementModifier(MovementThisTurn(MovementMode.JUMP, 4)))
    }

    @Test
    fun `target movement modifier bands are correct across the full table`() {
        assertEquals(0, targetMovementModifier(MovementThisTurn(MovementMode.WALK, 0)))
        assertEquals(0, targetMovementModifier(MovementThisTurn(MovementMode.WALK, 2)))
        assertEquals(1, targetMovementModifier(MovementThisTurn(MovementMode.WALK, 3)))
        assertEquals(1, targetMovementModifier(MovementThisTurn(MovementMode.WALK, 4)))
        assertEquals(2, targetMovementModifier(MovementThisTurn(MovementMode.RUN, 5)))
        assertEquals(2, targetMovementModifier(MovementThisTurn(MovementMode.RUN, 6)))
        assertEquals(3, targetMovementModifier(MovementThisTurn(MovementMode.RUN, 7)))
        assertEquals(3, targetMovementModifier(MovementThisTurn(MovementMode.RUN, 9)))
        assertEquals(4, targetMovementModifier(MovementThisTurn(MovementMode.RUN, 10)))
        assertEquals(4, targetMovementModifier(MovementThisTurn(MovementMode.RUN, 17)))
        assertEquals(5, targetMovementModifier(MovementThisTurn(MovementMode.RUN, 18)))
        assertEquals(5, targetMovementModifier(MovementThisTurn(MovementMode.RUN, 24)))
        assertEquals(6, targetMovementModifier(MovementThisTurn(MovementMode.RUN, 25)))
        assertEquals(6, targetMovementModifier(MovementThisTurn(MovementMode.RUN, 99)))
    }

    @Test
    fun `jumping target gets one on top of band for distance above 24`() {
        // 25+ hexes → band 6, jump bonus +1 = 7
        assertEquals(7, targetMovementModifier(MovementThisTurn(MovementMode.JUMP, 25)))
    }
}
