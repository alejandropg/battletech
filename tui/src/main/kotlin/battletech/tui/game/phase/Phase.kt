package battletech.tui.game.phase

import battletech.tactical.action.CombatUnit
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnitId
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.movement.MovementMode
import battletech.tactical.view.TargetInfo
import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage
import battletech.tui.game.RenderData
import com.github.ajalt.mordant.input.InputEvent

/**
 * UI sub-state machine for the active player phase. Phases are pure
 * UI-workflow objects: they hold cursor / hover / draft state, map input
 * events to [battletech.tactical.command.GameCommand]s, and produce render
 * data. They never mutate game state directly — all writes flow through
 * [AppState.session].
 */
public sealed interface Phase {
    public val turnPhase: TurnPhase

    public fun handle(event: InputEvent, app: AppState): Transition? = null

    public fun render(gameState: GameState): RenderData = RenderData.EMPTY

    public fun prompt(app: AppState): String

    public fun selectedUnit(app: AppState): CombatUnit? = null

    public fun pathDestination(): HexCoordinates? = null

    public fun attackRender(gameState: GameState): AttackRender? = null

    public fun movementMode(): MovementMode? = null

    public fun activePlayerLabel(app: AppState): String? = null
}

public data class Transition(
    val app: AppState,
    val flash: FlashMessage? = null,
)

public data class AttackRender(
    val targets: List<TargetInfo>,
    val weaponAssignments: Map<UnitId, Set<Int>>,
    val primaryTargetId: UnitId?,
    val cursorTargetIndex: Int,
    val cursorWeaponIndex: Int,
)
