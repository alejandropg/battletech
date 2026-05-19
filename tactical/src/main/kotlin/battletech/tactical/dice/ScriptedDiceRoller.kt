package battletech.tactical.dice

public class ScriptedDiceRoller(rolls: List<Int>) : DiceRoller {
    private val queue: ArrayDeque<Int> = ArrayDeque(rolls)

    override fun d6(): Int {
        require(queue.isNotEmpty()) { "ScriptedDiceRoller exhausted: no more rolls scripted" }
        val next = queue.removeFirst()
        require(next in 1..6) { "ScriptedDiceRoller roll out of range: $next (expected 1..6)" }
        return next
    }
}
