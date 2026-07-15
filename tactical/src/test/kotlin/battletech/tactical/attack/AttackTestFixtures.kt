package battletech.tactical.attack

import battletech.tactical.model.GameState
import battletech.tactical.query.OwnUnit
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.query.aWeapon
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.Weapon

/**
 * Builds a [WeaponAttackContext] from full [CombatUnit]s / [GameState], mirroring what the
 * authoritative handlers do: the target is wrapped as [OwnUnit] (the rules only ever read its
 * public projection — see [AttackContext]) and only the map is carried over. Tests keep
 * expressing fixtures in terms of whole units and game state; the projection detail stays
 * here rather than being restated at every call site.
 */
internal fun aWeaponAttackContext(
    actor: CombatUnit = aUnit(),
    gameState: GameState = aGameState(),
    target: CombatUnit = aUnit(id = "target"),
    weapon: Weapon = aWeapon(),
): WeaponAttackContext = WeaponAttackContext(
    actor = actor,
    map = gameState.map,
    target = OwnUnit(target),
    weapon = weapon,
)

/** Physical counterpart of [aWeaponAttackContext]; same wrapping rationale. */
internal fun aPhysicalAttackContext(
    actor: CombatUnit = aUnit(),
    gameState: GameState = aGameState(),
    target: CombatUnit = aUnit(id = "target"),
): PhysicalAttackContext = PhysicalAttackContext(
    actor = actor,
    map = gameState.map,
    target = OwnUnit(target),
)
