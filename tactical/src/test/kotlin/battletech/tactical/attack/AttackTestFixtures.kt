package battletech.tactical.attack

import battletech.tactical.model.CombatUnit
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.query.aWeapon
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
