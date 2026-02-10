package battletech.tactical.action

import battletech.tactical.model.GameState
import battletech.tactical.model.MovementMode
import battletech.tactical.model.Weapon

public data class ActionContext(
    public val actor: Unit,
    public val target: Unit? = null,
    public val weapon: Weapon? = null,
    public val movementMode: MovementMode? = null,
    public val gameState: GameState,
)
