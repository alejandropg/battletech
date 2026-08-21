package battletech.tui.game.phase

import battletech.tactical.model.PlayerId
import battletech.tactical.unit.UnitId

/** This phase's contribution to the status bar. */
internal data class PhaseStatus(
    val prompt: String,
    /** Null when no player is acting — movement complete, or the impulse sequence not yet seeded. */
    val activePlayer: PlayerId? = null,
    /** The unit already selected for the action described by [prompt], if any. */
    val actionUnitId: UnitId? = null,
)
