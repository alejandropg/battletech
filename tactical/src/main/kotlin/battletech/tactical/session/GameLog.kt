package battletech.tactical.session

public data class LogEntry(
    public val turn: Int,
    public val event: GameEvent,
)

public class GameLog {

    private val entries: MutableList<LogEntry> = mutableListOf()

    public fun snapshot(): List<LogEntry> = entries.toList()

    public fun append(entry: LogEntry) {
        entries += entry
    }
}
