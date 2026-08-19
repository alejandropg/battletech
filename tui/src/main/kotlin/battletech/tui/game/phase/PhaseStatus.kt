package battletech.tui.game.phase

/** This phase's contribution to the status bar. */
internal data class PhaseStatus(
    val prompt: String,
    /** Null when no player is acting — movement complete, or the impulse sequence not yet seeded. */
    val activePlayerLabel: String? = null,
)
