package battletech.tactical.heat

import battletech.tactical.attack.AttackDeclaration
import battletech.tactical.model.GameState
import battletech.tactical.model.mapUnits
import battletech.tactical.model.submersionDissipationBonus
import battletech.tactical.unit.engineHeatPerTurn

/**
 * Fold each unit's heat generated this turn into its standing heat and apply
 * dissipation: `newHeat = max(0, current + generated + engineHeat - capacity - waterBonus)`.
 * The per-turn generation list is consumed and cleared (this is also its reset). Engine
 * critical hits add a flat `5 × engineCritCount()` heat every turn (1st crit +5, 2nd
 * +10; a 3rd destroys the unit outright via the destruction sweep, so it never
 * reaches this fold) — it is generated heat for the turn, so it's folded in
 * alongside weapon/movement heat, before dissipation.
 *
 * **Water submersion dissipation bonus**: units standing in water receive extra
 * dissipation from [submersionDissipationBonus] — +6 at depth 1 (legs submerged),
 * +12 at depth 2+ (fully submerged). See [battletech.tactical.model.submersionDissipationBonus]
 * for the ASSUMPTION/simplification rationale.
 *
 * Shutdown and ammo-explosion consequences are rolled separately in
 * [battletech.tactical.session.HeatPhaseHandler] since they require the dice roller.
 */
public fun GameState.applyHeatPhase(): GameState {
    val snapshot = this
    return mapUnits { unit ->
        val generated = unit.heatGeneratedThisTurn.sumOf { it.amount }
        val engineHeat = unit.engineHeatPerTurn()
        val waterBonus = submersionDissipationBonus(unit, snapshot)
        val newHeat = maxOf(0, unit.currentHeat + generated + engineHeat - unit.heatSink.dissipation() - waterBonus)
        unit.copy(currentHeat = newHeat, heatGeneratedThisTurn = emptyList())
    }
}

/**
 * Append the heat of each fired weapon to its attacker's per-turn generation
 * list. Heat lands in `heatGeneratedThisTurn` (not `currentHeat`) so the to-hit
 * heat penalty still reads the standing level during resolution.
 */
public fun GameState.applyWeaponHeat(declarations: List<AttackDeclaration>): GameState {
    if (declarations.isEmpty()) return this
    val declarationsByUnit = declarations.groupBy { it.attackerId }
    return mapUnits { unit ->
        val unitDeclarations = declarationsByUnit[unit.id]
        if (unitDeclarations == null) {
            unit
        } else {
            val sources = unitDeclarations.mapNotNull { declaration ->
                unit.weapons.getOrNull(declaration.weaponIndex)?.let(::weaponHeatSource)
            }
            unit.copy(heatGeneratedThisTurn = unit.heatGeneratedThisTurn + sources)
        }
    }
}
