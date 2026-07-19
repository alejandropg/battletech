package battletech.tactical.model

/**
 * Whether a match is still being played or has concluded. Produced by [victoryStatus];
 * [battletech.tactical.session.BattleSession.runDestructionSweep] is the only caller that
 * acts on it — flipping its own `_matchOver` flag and emitting [battletech.tactical.session.MatchEnded]
 * — so the sweep stays the single source of "the match just ended" events while the rule
 * itself is a pure, session-free query over [GameState].
 */
public sealed interface MatchStatus {
    public data object Ongoing : MatchStatus
    public data class Ended(public val outcome: MatchOutcome) : MatchStatus
}

/**
 * Evaluates victory for [state]: only players with units actually deployed are considered
 * (an empty roster — e.g. a not-yet-populated match, or a test fixture with no units for
 * that player — must never be misread as "eliminated"). The match ends once at most one
 * deployed player still has a surviving (non-[battletech.tactical.unit.CombatUnit.isDestroyed]) unit — that player
 * wins, or it's a [MatchOutcome.Draw] if none do.
 */
public fun victoryStatus(state: GameState): MatchStatus {
    val rosterByPlayer = PlayerId.entries.associateWith { state.units.of(it) }
    val deployedPlayers = rosterByPlayer.filterValues { it.isNotEmpty() }.keys
    val survivingPlayers = deployedPlayers.filter { player -> rosterByPlayer.getValue(player).any { !it.isDestroyed } }
    if (deployedPlayers.size > 1 && survivingPlayers.size <= 1) {
        val winner = survivingPlayers.singleOrNull()
        val outcome = if (winner != null) MatchOutcome.Victory(winner) else MatchOutcome.Draw
        return MatchStatus.Ended(outcome)
    }
    return MatchStatus.Ongoing
}
