package battletech.tactical.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class GameStateFactoryTest {

    @Test
    fun `sample game state uses the classic map when no map is specified`() {
        val state = GameStateFactory().sampleGameState()

        assertEquals("battletech-classic", state.map.name)
    }
}
