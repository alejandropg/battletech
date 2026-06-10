package battletech.tactical.session

import battletech.tactical.attack.AttackDeclaration
import battletech.tactical.heat.weaponHeatSource
import battletech.tactical.model.GameState

/**
 * Fold each unit's heat generated this turn into its standing heat and apply
 * dissipation: `newHeat = max(0, current + generated - capacity)`. The per-turn
 * generation list is consumed and cleared (this is also its reset). Shutdown
 * and ammo-explosion consequences are rolled separately in
 * [HeatPhaseHandler] since they require the dice roller.
 */
public fun GameState.applyHeatPhase(): GameState {
    val updatedUnits = units.map { unit ->
        val generated = unit.heatGeneratedThisTurn.sumOf { it.amount }
        val newHeat = maxOf(0, unit.currentHeat + generated - unit.heatSinkCapacity)
        unit.copy(currentHeat = newHeat, heatGeneratedThisTurn = emptyList())
    }
    return copy(units = updatedUnits)
}

/**
 * Append the heat of each fired weapon to its attacker's per-turn generation
 * list. Heat lands in `heatGeneratedThisTurn` (not `currentHeat`) so the to-hit
 * heat penalty still reads the standing level during resolution.
 */
public fun GameState.applyWeaponHeat(declarations: List<AttackDeclaration>): GameState {
    if (declarations.isEmpty()) return this
    val declarationsByUnit = declarations.groupBy { it.attackerId }
    val updatedUnits = units.map { unit ->
        val unitDeclarations = declarationsByUnit[unit.id] ?: return@map unit
        val sources = unitDeclarations.mapNotNull { declaration ->
            unit.weapons.getOrNull(declaration.weaponIndex)?.let(::weaponHeatSource)
        }
        unit.copy(heatGeneratedThisTurn = unit.heatGeneratedThisTurn + sources)
    }
    return copy(units = updatedUnits)
}
