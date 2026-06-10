package battletech.tui.game

import battletech.tactical.model.PlayerId
import battletech.tactical.session.TurnState

/** Human-readable name shown in prompts and the status bar. */
internal val PlayerId.displayName: String
    get() = if (this == PlayerId.PLAYER_1) "Player 1" else "Player 2"

/**
 * Returns the active attacker's display name, or null when the attack phase
 * status bar should show nothing.
 *
 * @param requireSeeded when true (default), also returns null if the attack
 *   sequence order hasn't been seeded yet (i.e. order is empty).
 */
internal fun attackPlayerLabel(turnState: TurnState, requireSeeded: Boolean = true): String? {
    if (requireSeeded && turnState.attack.sequence.order.isEmpty()) return null
    if (turnState.attack.isComplete) return null
    return turnState.attack.activePlayer.displayName
}
