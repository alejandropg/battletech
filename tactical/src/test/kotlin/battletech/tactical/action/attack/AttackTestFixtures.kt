package battletech.tactical.action.attack

import battletech.tactical.action.CombatUnit
import battletech.tactical.action.aGameState
import battletech.tactical.action.aUnit
import battletech.tactical.action.aWeapon
import battletech.tactical.model.GameState
import battletech.tactical.model.Weapon

internal fun aWeaponAttackContext(
    actor: CombatUnit = aUnit(),
    gameState: GameState = aGameState(),
    target: CombatUnit = aUnit(id = "target"),
    weapon: Weapon = aWeapon(),
): WeaponAttackContext = WeaponAttackContext(
    actor = actor,
    gameState = gameState,
    target = target,
    weapon = weapon,
)

internal fun aPhysicalAttackContext(
    actor: CombatUnit = aUnit(),
    gameState: GameState = aGameState(),
    target: CombatUnit = aUnit(id = "target"),
): PhysicalAttackContext = PhysicalAttackContext(
    actor = actor,
    gameState = gameState,
    target = target,
)
