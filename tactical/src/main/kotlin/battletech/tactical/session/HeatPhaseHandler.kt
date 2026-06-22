package battletech.tactical.session

import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.applyDamage
import battletech.tactical.dice.DiceRoller
import battletech.tactical.heat.HeatScale
import battletech.tactical.model.GameState
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.withSlot

/**
 * System phase. On entry, folds each unit's heat generated this turn into its
 * standing heat and dissipates ([GameState.applyHeatPhase]), then walks the
 * units in state order rolling, per unit, a shutdown/startup avoidance roll and
 * then an ammo-explosion roll — each only when the unit's heat reaches the
 * relevant threshold, so seeded tests see no spurious dice. Completes
 * immediately. Accepts no commands.
 */
public class HeatPhaseHandler : PhaseHandler {

    override val phase: TurnPhase = TurnPhase.HEAT

    override fun activePlayer(turn: TurnState): PlayerId? = null

    override fun accepts(command: GameCommand, turn: TurnState): Boolean = false

    override fun apply(
        command: GameCommand,
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome = PhaseOutcome(state, turn, emptyList())

    override fun isComplete(turn: TurnState): Boolean = true

    override fun onEntry(
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome {
        val before = state.units.associate { it.id to it.currentHeat }
        val folded = state.applyHeatPhase()
        val after = folded.units.associate { it.id to it.currentHeat }
        val events = mutableListOf<GameEvent>(HeatDissipated(before, after))

        val processedUnits = folded.units.map { unit ->
            var working = unit
            val (afterPower, powerEvent) = resolvePower(working, roller)
            powerEvent?.let { events += it }
            working = afterPower
            val (afterAmmo, ammoEvent) = resolveAmmoExplosion(working, roller)
            ammoEvent?.let { events += it }
            afterAmmo
        }

        return PhaseOutcome(folded.copy(units = processedUnits), turn, events)
    }

    /** Shutdown (for an operational unit) or startup (for a shut-down unit). */
    private fun resolvePower(unit: CombatUnit, roller: DiceRoller): Pair<CombatUnit, GameEvent?> {
        val heat = unit.currentHeat
        return if (unit.isShutdown) {
            when {
                HeatScale.isAutoShutdown(heat) -> unit to null // pinned down, no restart
                HeatScale.shutdownAvoidTarget(heat) == null ->
                    unit.copy(isShutdown = false) to UnitRestarted(unit.id, null)
                else -> {
                    val target = HeatScale.shutdownAvoidTarget(heat)!!
                    val roll = roller.roll2d6()
                    if (roll.total >= target) {
                        unit.copy(isShutdown = false) to UnitRestarted(unit.id, roll)
                    } else {
                        unit to null // restart failed; still down
                    }
                }
            }
        } else {
            when {
                HeatScale.isAutoShutdown(heat) ->
                    unit.copy(isShutdown = true) to UnitShutdown(unit.id, roll = null, auto = true)
                else -> {
                    val target = HeatScale.shutdownAvoidTarget(heat) ?: return unit to null
                    val roll = roller.roll2d6()
                    if (roll.total >= target) {
                        unit to null // avoided
                    } else {
                        unit.copy(isShutdown = true) to UnitShutdown(unit.id, roll, auto = false)
                    }
                }
            }
        }
    }

    /**
     * Ammo explosion: on a failed avoidance roll the ammo bin with the greatest
     * potential damage (`shots × damagePerShot`) cooks off, applying that damage
     * to the center torso through the standard damage path; the bin is emptied.
     */
    private fun resolveAmmoExplosion(unit: CombatUnit, roller: DiceRoller): Pair<CombatUnit, GameEvent?> {
        val target = HeatScale.ammoExplosionAvoidTarget(unit.currentHeat) ?: return unit to null
        val roll = roller.roll2d6()
        if (roll.total >= target) return unit to null

        val worst = unit.criticalLayout.ammoBins()
            .filter { (_, _, bin) -> bin.shots > 0 }
            .maxByOrNull { (_, _, bin) -> bin.shots * bin.type.damagePerShot }
            ?: return unit to null // nothing to explode

        val (location, slotIndex, bin) = worst
        val damage = bin.shots * bin.type.damagePerShot
        val updatedLayout = unit.criticalLayout.withSlot(location, slotIndex, bin.copy(shots = 0))
        val damaged = applyDamage(
            unit.copy(criticalLayout = updatedLayout),
            HitLocation.CENTER_TORSO,
            damage,
        )
        return damaged to AmmoExploded(unit.id, bin.type, damage)
    }
}
