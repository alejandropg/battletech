package battletech.tui.game

import battletech.tactical.action.TurnPhase
import com.github.ajalt.mordant.input.InputEvent

public sealed interface PhaseState {
    public val prompt: String

    public fun processEvent(
        event: InputEvent,
        appState: AppState,
        phaseManager: PhaseManager,
    ): HandleResult?

}

internal fun isAttackPhase(phase: TurnPhase): Boolean =
    phase == TurnPhase.WEAPON_ATTACK || phase == TurnPhase.PHYSICAL_ATTACK
