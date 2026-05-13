package battletech.tui.game

import battletech.tactical.action.MovementImpulse
import battletech.tactical.action.PlayerId

public data class ImpulseSequence(
    val order: List<MovementImpulse>,
    val currentIndex: Int = 0,
) {
    val isComplete: Boolean get() = currentIndex >= order.size
    val current: MovementImpulse get() = order[currentIndex]
    val activePlayer: PlayerId get() = current.player
    public fun advance(): ImpulseSequence = copy(currentIndex = currentIndex + 1)
}
