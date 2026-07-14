package battletech.tactical.model

import kotlinx.serialization.Serializable

/**
 * How a completed match resolved. Produced by the destruction sweep
 * ([battletech.tactical.session.BattleSession]) once at most one player has surviving units,
 * and carried on [battletech.tactical.session.MatchEnded]. Lives in `model/` (rather than
 * `session/`) so rules code — e.g. a future victory-condition rule — can depend on it without
 * an upward dependency on `session/`.
 */
@Serializable
public sealed interface MatchOutcome {
    /** [winner] is the sole player with surviving units. */
    @Serializable
    public data class Victory(public val winner: PlayerId) : MatchOutcome

    /** Neither side has survivors. */
    @Serializable
    public data object Draw : MatchOutcome
}
