package battletech.tactical.attack

import battletech.tactical.model.GameState
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.Weapon

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
