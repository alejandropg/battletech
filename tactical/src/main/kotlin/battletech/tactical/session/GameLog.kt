package battletech.tactical.session

public data class LogEntry(
    public val turn: Int,
    public val text: String,
)

public class GameLog {

    private val entries: MutableList<LogEntry> = mutableListOf()

    public fun snapshot(): List<LogEntry> = entries.toList()

    public fun append(entry: LogEntry) {
        entries += entry
    }

    public fun lastN(n: Int): List<LogEntry> = entries.takeLast(n)
}
