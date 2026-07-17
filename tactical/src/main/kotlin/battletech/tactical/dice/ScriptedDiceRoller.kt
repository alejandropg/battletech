package battletech.tactical.dice

/**
 * Consumes [rolls] in order, one per [d6] call, and fails loudly rather than inventing a value
 * when it runs out — a roll order mismatch is a bug, not something to paper over with a fallback.
 *
 * Reached only via [DiceRoller.deterministic], and only from tests today. That does NOT make it a
 * test double to be moved into test sources: replaying an exact game means supplying the exact
 * rolls in the exact order the session consumed them, so this is the mechanism a future
 * replay/save feature is built on. See [DiceRoller.Companion]'s KDoc.
 */
internal class ScriptedDiceRoller(rolls: List<Int>) : DiceRoller {
    private val queue: ArrayDeque<Int> = ArrayDeque(rolls)

    override fun d6(): Int {
        require(queue.isNotEmpty()) { "ScriptedDiceRoller exhausted: no more rolls scripted" }
        val next = queue.removeFirst()
        require(next in 1..6) { "ScriptedDiceRoller roll out of range: $next (expected 1..6)" }
        return next
    }
}
