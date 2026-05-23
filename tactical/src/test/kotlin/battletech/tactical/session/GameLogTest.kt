package battletech.tactical.session

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class GameLogTest {

    private fun entry(turn: Int, n: Int) = LogEntry(turn, TurnEnded(n))

    @Test
    fun `new log has an empty snapshot`() {
        val log = GameLog()

        assertThat(log.snapshot()).isEmpty()
    }

    @Test
    fun `lastN returns the most recent entries`() {
        val log = GameLog()
        log.append(entry(1, 1))
        log.append(entry(1, 2))
        log.append(entry(2, 3))
        log.append(entry(2, 4))

        assertThat(log.lastN(2)).containsExactly(
            entry(2, 3),
            entry(2, 4),
        )
    }

    @Test
    fun `lastN returns all entries when N exceeds size`() {
        val log = GameLog()
        log.append(entry(1, 1))
        log.append(entry(1, 2))

        assertThat(log.lastN(10)).containsExactly(
            entry(1, 1),
            entry(1, 2),
        )
    }

    @Test
    fun `appended entries appear in snapshot in append order`() {
        val log = GameLog()

        log.append(entry(1, 1))
        log.append(entry(1, 2))
        log.append(entry(2, 3))

        assertThat(log.snapshot()).containsExactly(
            entry(1, 1),
            entry(1, 2),
            entry(2, 3),
        )
    }
}
