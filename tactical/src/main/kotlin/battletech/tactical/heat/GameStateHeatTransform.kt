package battletech.tactical.heat

import battletech.tactical.attack.AttackDeclaration
import battletech.tactical.model.GameState

/**
 * Fold each unit's heat generated this turn into its standing heat and apply dissipation, via
 * [projectHeat].[HeatProjection.projected] — the same formula the TUI's end-of-turn preview
 * uses, so the two can never drift apart. The per-turn generation list is consumed and cleared
 * (this is also its reset).
 *
 * Shutdown and ammo-explosion consequences are rolled separately in
 * [battletech.tactical.heat.HeatPhaseHandler] since they require the dice roller.
 */
public fun GameState.applyHeatPhase(): GameState {
    val snapshot = this
    return copy(
        units = units.mapUnits { unit ->
            val newHeat = projectHeat(unit, snapshot.map).projected
            unit.copy(currentHeat = newHeat, heatGeneratedThisTurn = emptyList())
        },
    )
}

/**
 * Append the heat of each fired weapon to its attacker's per-turn generation
 * list. Heat lands in `heatGeneratedThisTurn` (not `currentHeat`) so the to-hit
 * heat penalty still reads the standing level during resolution.
 */
public fun GameState.applyWeaponHeat(declarations: List<AttackDeclaration>): GameState {
    if (declarations.isEmpty()) return this
    val declarationsByUnit = declarations.groupBy { it.attackerId }
    return copy(
        units = units.mapUnits { unit ->
            val unitDeclarations = declarationsByUnit[unit.id]
            if (unitDeclarations == null) {
                unit
            } else {
                val sources = unitDeclarations.mapNotNull { declaration ->
                    unit.weapons.getOrNull(declaration.weaponIndex)?.let(::weaponHeatSource)
                }
                unit.copy(heatGeneratedThisTurn = unit.heatGeneratedThisTurn + sources)
            }
        },
    )
}
