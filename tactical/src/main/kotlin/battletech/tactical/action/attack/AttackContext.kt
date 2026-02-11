package battletech.tactical.action.attack

import battletech.tactical.action.Unit
import battletech.tactical.model.GameState
import battletech.tactical.model.Weapon

public sealed interface AttackContext {
    public val actor: Unit
    public val gameState: GameState
    public val target: Unit
}

public data class WeaponAttackContext(
    override val actor: Unit,
    override val gameState: GameState,
    override val target: Unit,
    public val weapon: Weapon,
) : AttackContext

public data class PhysicalAttackContext(
    override val actor: Unit,
    override val gameState: GameState,
    override val target: Unit,
) : AttackContext
