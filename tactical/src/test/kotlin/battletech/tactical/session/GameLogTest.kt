package battletech.tactical.session

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class GameLogTest {

    @Test
    fun `new log has an empty snapshot`() {
        val log = GameLog()

        assertThat(log.snapshot()).isEmpty()
    }

    @Test
    fun `lastN returns the most recent entries`() {
        val log = GameLog()
        log.append(LogEntry(1, "a"))
        log.append(LogEntry(1, "b"))
        log.append(LogEntry(2, "c"))
        log.append(LogEntry(2, "d"))

        assertThat(log.lastN(2)).containsExactly(
            LogEntry(2, "c"),
            LogEntry(2, "d"),
        )
    }

    @Test
    fun `lastN returns all entries when N exceeds size`() {
        val log = GameLog()
        log.append(LogEntry(1, "a"))
        log.append(LogEntry(1, "b"))

        assertThat(log.lastN(10)).containsExactly(
            LogEntry(1, "a"),
            LogEntry(1, "b"),
        )
    }

    @Test
    fun `appended entries appear in snapshot in append order`() {
        val log = GameLog()

        log.append(LogEntry(1, "first"))
        log.append(LogEntry(1, "second"))
        log.append(LogEntry(2, "third"))

        assertThat(log.snapshot()).containsExactly(
            LogEntry(1, "first"),
            LogEntry(1, "second"),
            LogEntry(2, "third"),
        )
    }
}
