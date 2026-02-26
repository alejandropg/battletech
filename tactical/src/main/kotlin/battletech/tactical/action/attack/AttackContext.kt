package battletech.tactical.action.attack

import battletech.tactical.action.CombatUnit
import battletech.tactical.model.GameState
import battletech.tactical.model.Weapon

public sealed interface AttackContext {
    public val actor: CombatUnit
    public val gameState: GameState
    public val target: CombatUnit
}

public data class WeaponAttackContext(
    override val actor: CombatUnit,
    override val gameState: GameState,
    override val target: CombatUnit,
    public val weapon: Weapon,
) : AttackContext

public data class PhysicalAttackContext(
    override val actor: CombatUnit,
    override val gameState: GameState,
    override val target: CombatUnit,
) : AttackContext
