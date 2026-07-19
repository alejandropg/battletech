package battletech.tactical.session

import battletech.tactical.dice.DiceRoller
import battletech.tactical.heat.applyHeatPhase
import battletech.tactical.heat.resolveUnitHeatPhase
import battletech.tactical.model.GameState
import battletech.tactical.model.TurnPhase

/**
 * System phase. On entry, folds each unit's heat generated this turn into its
 * standing heat and dissipates ([battletech.tactical.heat.applyHeatPhase]), then
 * walks the units in state order running, per unit, [resolveUnitHeatPhase]'s fixed
 * sequence of per-unit effects (shutdown/restart, life support, consciousness
 * recovery, ammo explosion, drowning) — each gated so it only consumes dice when
 * the unit's state actually warrants it, keeping untouched fixtures dice-free.
 *
 * Completes immediately. Accepts no commands.
 */
public class HeatPhaseHandler : SystemPhaseHandler() {

    override val phase: TurnPhase = TurnPhase.HEAT

    override fun onEntry(
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome {
        val before = state.units.associate { it.id to it.currentHeat }
        val folded = state.applyHeatPhase()
        val after = folded.units.associate { it.id to it.currentHeat }
        val events = mutableListOf<GameEvent>(HeatDissipated(before, after))

        val processedUnits = folded.units.mapUnits { unit ->
            // Captured before any of this turn's resolution steps mutate consciousness,
            // so resolveUnitHeatPhase's recovery step can tell "was already unconscious
            // coming into this phase" apart from "knocked out by life support just now".
            val wasUnconsciousBeforePhase = !unit.isPilotConscious
            val (processed, unitEvents) = resolveUnitHeatPhase(unit, folded, wasUnconsciousBeforePhase, roller)
            events += unitEvents
            processed
        }

        return PhaseOutcome(folded.copy(units = processedUnits), turn, events)
    }
}
