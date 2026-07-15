package battletech.tactical.attack

import battletech.tactical.dice.DiceRoller
import battletech.tactical.session.GameEvent
import battletech.tactical.session.PilotHit
import battletech.tactical.session.PilotKnockedUnconscious
import battletech.tactical.session.PilotRecoveredConsciousness
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.PILOT_DEATH_THRESHOLD

/**
 * 2d6 consciousness-check target, keyed by the pilot's *current* hit count after the
 * hit being resolved is applied (ASSUMPTION/standard BattleTech table — not stated in
 * `docs/rules/armor-damage.md`, which only pins life-support pilot-damage sources and
 * the death condition). A hit count with no entry here (0, or >= [PILOT_DEATH_THRESHOLD])
 * never reaches this table: 0 hits never triggers a check, and >= 6 hits is death, not
 * a check.
 */
private val CONSCIOUSNESS_TARGET: Map<Int, Int> = mapOf(
    1 to 3,
    2 to 5,
    3 to 7,
    4 to 10,
    5 to 11,
)

/**
 * Applies one pilot hit to [unit] (`docs/rules/armor-damage.md` §3 Life Support is the
 * only doc-specified source this stage wires up; future stages may add head-hit/fall/
 * ammo-explosion pilot damage through this same entry point).
 *
 * - Increments [CombatUnit.pilotHits].
 * - If the new total reaches [PILOT_DEATH_THRESHOLD] (ASSUMPTION: 6, standard BT), the
 *   pilot is dead — no consciousness roll is made (a dead pilot can't be conscious or
 *   not; [battletech.tactical.unit.destructionReason] picks up `PILOT_DEAD` from the
 *   updated [CombatUnit.pilotHits] in the session's destruction sweep, no extra wiring
 *   needed here). [PilotHit.consciousnessRoll] is null in this case.
 * - Otherwise rolls 2d6 against [CONSCIOUSNESS_TARGET] for the new hit count via
 *   [roller] (ASSUMPTION table). On failure, [CombatUnit.isPilotConscious] is set false
 *   and a [PilotKnockedUnconscious] is appended after the [PilotHit]. A pilot already
 *   unconscious before this hit stays unconscious (no further event) — only the
 *   *transition* into unconsciousness is reported via [PilotKnockedUnconscious].
 *
 * Returns the updated unit and the list of events produced (1 or 2 entries, in order).
 * Pure: all dice flow through [roller]; no raw `Random`.
 */
public fun applyPilotHit(unit: CombatUnit, roller: DiceRoller): Pair<CombatUnit, List<GameEvent>> {
    val newHits = unit.pilotHits + 1

    if (newHits >= PILOT_DEATH_THRESHOLD) {
        val dead = unit.copy(pilotHits = newHits)
        return dead to listOf(PilotHit.Fatal(unit.id, newHits))
    }

    val target = CONSCIOUSNESS_TARGET.getValue(newHits)
    val roll = roller.roll2d6()
    val staysConscious = unit.isPilotConscious && roll.total >= target
    val updated = unit.copy(pilotHits = newHits, isPilotConscious = staysConscious)

    val events = mutableListOf<GameEvent>(PilotHit.Checked(unit.id, newHits, roll, staysConscious))
    if (unit.isPilotConscious && !staysConscious) {
        events += PilotKnockedUnconscious(unit.id)
    }
    return updated to events
}

/**
 * Consciousness RECOVERY attempt for a pilot who is alive but unconscious
 * (ASSUMPTION/standard BT — `docs/rules/armor-damage.md` only specifies death and the
 * life-support damage sources, not recovery; we mirror [HeatPhaseHandler]'s
 * shutdown/restart pattern: an "avoid bad state" roll while down, an automatic-style
 * recovery attempt every turn while up). Reuses [CONSCIOUSNESS_TARGET], keyed by the
 * pilot's *current* (unchanged) hit count, same table as the original hit check.
 *
 * Only called by [battletech.tactical.session.HeatPhaseHandler.onEntry] when
 * `!unit.isPilotConscious && unit.pilotHits < PILOT_DEATH_THRESHOLD` (a dead pilot
 * never recovers; a conscious pilot never rolls) — so untouched fixtures consume no
 * dice. On success, sets [CombatUnit.isPilotConscious] back to true and emits
 * [PilotRecoveredConsciousness]; on failure, returns the unit unchanged and no event.
 */
public fun attemptConsciousnessRecovery(unit: CombatUnit, roller: DiceRoller): Pair<CombatUnit, GameEvent?> {
    val target = CONSCIOUSNESS_TARGET[unit.pilotHits] ?: return unit to null
    val roll = roller.roll2d6()
    if (roll.total < target) return unit to null
    return unit.copy(isPilotConscious = true) to PilotRecoveredConsciousness.Detailed(unit.id, roll)
}

/**
 * Applies the 2 pilot hits an ammo explosion inflicts on [unit] (standard BT rule): each hit
 * runs its own consciousness check via [applyPilotHit]. Shared by [CriticalHitResolution]'s two
 * detonation sites (limb blow-off, ordinary crit) and [battletech.tactical.session.HeatPhaseHandler]'s
 * heat-driven cook-off.
 *
 * **Canonical dice order:** consciousness check 2d6 (hit 1), then consciousness check 2d6 (hit 2).
 */
public fun applyAmmoExplosionPilotHits(unit: CombatUnit, roller: DiceRoller): Pair<CombatUnit, List<GameEvent>> {
    var working = unit
    val events = mutableListOf<GameEvent>()
    repeat(2) {
        val (afterHit, hitEvents) = applyPilotHit(working, roller)
        working = afterHit
        events += hitEvents
    }
    return working to events
}
