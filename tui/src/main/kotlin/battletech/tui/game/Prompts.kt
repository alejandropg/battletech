package battletech.tui.game

import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.model.MovementMode
import battletech.tactical.movement.ReachabilityMap

internal const val DECLARING_PROMPT =
    "←/→ twist torso | ↑/↓ navigate weapons | Space: toggle | Tab: next attacker | 'c': commit"
internal const val SELECT_FACING_PROMPT = "Select facing (1-6)"
internal const val DEFAULT_IDLE_PROMPT = "Move cursor to select a unit"

public fun phasePrompt(appState: AppState): String = when (val phase = appState.phase) {
    is IdlePhaseState -> idlePrompt(appState)
    is MovementPhaseState.Browsing -> modePrompt(phase.reachability)
    is MovementPhaseState.SelectingFacing -> SELECT_FACING_PROMPT
    is AttackPhaseState -> DECLARING_PROMPT
}

public fun idlePrompt(appState: AppState): String {
    val turnState = appState.turnState ?: return DEFAULT_IDLE_PROMPT
    return when (appState.currentPhase) {
        TurnPhase.MOVEMENT -> movementPrompt(turnState)
        TurnPhase.WEAPON_ATTACK, TurnPhase.PHYSICAL_ATTACK -> attackPrompt(turnState)
        else -> DEFAULT_IDLE_PROMPT
    }
}

public fun movementPrompt(turnState: TurnState): String {
    val playerName = if (turnState.activePlayer == PlayerId.PLAYER_1) "Player 1" else "Player 2"
    val remaining = turnState.remainingInImpulse
    return "$playerName: select a unit to move ($remaining remaining)"
}

public fun attackPrompt(turnState: TurnState): String {
    if (turnState.allAttackImpulsesComplete) return "All attacks declared"
    val playerName = if (turnState.activeAttackPlayer == PlayerId.PLAYER_1) "Player 1" else "Player 2"
    return "$playerName: select units, toggle weapons | 'c' to commit"
}

public fun modePrompt(reachability: ReachabilityMap?): String {
    if (reachability == null) return "No movement available"
    val modeName = when (reachability.mode) {
        MovementMode.WALK -> "Walk"
        MovementMode.RUN -> "Run"
        MovementMode.JUMP -> "Jump"
    }
    val suffix = when (reachability.mode) {
        MovementMode.RUN -> " (+2 to-hit)"
        MovementMode.JUMP -> " (+3 to-hit)"
        else -> ""
    }
    return "$modeName (${reachability.maxMP} MP)$suffix"
}
