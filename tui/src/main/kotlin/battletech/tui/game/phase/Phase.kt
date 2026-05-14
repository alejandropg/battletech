package battletech.tui.game.phase

import battletech.tactical.action.CombatUnit
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnitId
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MovementMode
import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage
import battletech.tui.game.RenderData
import battletech.tui.game.TargetInfo
import com.github.ajalt.mordant.input.InputEvent

public sealed interface Phase {
    public val turnPhase: TurnPhase

    public fun tick(app: AppState, svc: PhaseServices): Transition? = null

    public fun handle(event: InputEvent, app: AppState, svc: PhaseServices): Transition? = null

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
    val flash: FlashMessage? = null
)

public data class AttackRender(
    val targets: List<TargetInfo>,
    val weaponAssignments: Map<UnitId, Set<Int>>,
    val primaryTargetId: UnitId?,
    val cursorTargetIndex: Int,
    val cursorWeaponIndex: Int,
)
