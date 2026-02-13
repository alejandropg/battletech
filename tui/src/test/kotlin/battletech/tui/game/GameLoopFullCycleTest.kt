package battletech.tui.game

import battletech.tactical.action.TurnPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class GameLoopFullCycleTest {

    @Test
    fun `full phase cycle`() {
        val phases = listOf(
            TurnPhase.INITIATIVE,
            TurnPhase.MOVEMENT,
            TurnPhase.WEAPON_ATTACK,
            TurnPhase.PHYSICAL_ATTACK,
            TurnPhase.HEAT,
            TurnPhase.END,
        )

        var current = TurnPhase.INITIATIVE
        val visited = mutableListOf(current)

        repeat(6) {
            current = GameLoop.nextPhase(current)
            visited.add(current)
        }

        assertEquals(
            listOf(
                TurnPhase.INITIATIVE,
                TurnPhase.MOVEMENT,
                TurnPhase.WEAPON_ATTACK,
                TurnPhase.PHYSICAL_ATTACK,
                TurnPhase.HEAT,
                TurnPhase.END,
                TurnPhase.INITIATIVE, // loops back
            ),
            visited,
        )
    }
}
