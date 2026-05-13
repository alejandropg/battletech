package battletech.tui.game

import battletech.tactical.action.Impulse
import battletech.tactical.action.PlayerId

public data class ImpulseSequence(
    val order: List<Impulse>,
    val currentIndex: Int = 0,
) {
    val isComplete: Boolean get() = currentIndex >= order.size
    val current: Impulse get() = order[currentIndex]
    val activePlayer: PlayerId get() = current.player
    public fun advance(): ImpulseSequence = copy(currentIndex = currentIndex + 1)
}
