package battletech.tactical.session

import battletech.tactical.attack.AttackDeclaration
import battletech.tactical.heat.weaponHeatSource
import battletech.tactical.model.GameState
import battletech.tactical.unit.engineHeatPerTurn

/**
 * Fold each unit's heat generated this turn into its standing heat and apply
 * dissipation: `newHeat = max(0, current + generated + engineHeat - capacity)`. The
 * per-turn generation list is consumed and cleared (this is also its reset). Engine
 * critical hits add a flat `5 × engineCritCount()` heat every turn (1st crit +5, 2nd
 * +10; a 3rd destroys the unit outright via the destruction sweep, so it never
 * reaches this fold) — it is generated heat for the turn, so it's folded in
 * alongside weapon/movement heat, before dissipation. Shutdown and ammo-explosion
 * consequences are rolled separately in [HeatPhaseHandler] since they require the
 * dice roller.
 */
public fun GameState.applyHeatPhase(): GameState {
    val updatedUnits = units.map { unit ->
        val generated = unit.heatGeneratedThisTurn.sumOf { it.amount }
        val engineHeat = unit.engineHeatPerTurn()
        val newHeat = maxOf(0, unit.currentHeat + generated + engineHeat - unit.heatSink.dissipation())
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
