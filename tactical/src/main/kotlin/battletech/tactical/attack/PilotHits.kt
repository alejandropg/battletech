package battletech.tactical.attack

import battletech.tactical.dice.DiceRoller
import battletech.tactical.session.GameEvent
import battletech.tactical.session.PilotHit
import battletech.tactical.session.PilotKnockedUnconscious
import battletech.tactical.session.PilotRecoveredConsciousness
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.PILOT_DEATH_THRESHOLD

/**
 * Consciousness-check target (`docs/rules/pilot.md` §2), keyed by the pilot's *current*
 * hit count after the hit being resolved is applied. A hit count with no entry here
 * never reaches this table: 0 hits never triggers a check, and [PILOT_DEATH_THRESHOLD]
 * or more is death, not a check.
 */
private val CONSCIOUSNESS_TARGET: Map<Int, Int> = mapOf(
    1 to 3,
    2 to 5,
    3 to 7,
    4 to 10,
    5 to 11,
)

/**
 * The public face of [CONSCIOUSNESS_TARGET]: the 2d6 consciousness-check target for a pilot at
 * [hits] hits taken, or null when [hits] never triggers a check (0, or [PILOT_DEATH_THRESHOLD]
 * or more — death, not a check). Record-sheet display (the printed Consciousness# row) reads
 * this rather than restating the thresholds.
 */
public fun consciousnessTarget(hits: Int): Int? = CONSCIOUSNESS_TARGET[hits]

/**
 * The single entry point for applying one pilot hit to [unit], whatever inflicted it
 * (`docs/rules/pilot.md` §1–2).
 *
 * On death no consciousness roll is made, and [PilotHit.consciousnessRoll] is null —
 * [battletech.tactical.unit.destructionReason] picks up `PILOT_DEAD` from the updated
 * [CombatUnit.pilotHits] in the session's destruction sweep, so nothing extra is wired
 * here. Otherwise a failed check sets [CombatUnit.isPilotConscious] false and appends
 * [PilotKnockedUnconscious] after the [PilotHit]. Only the *transition* into
 * unconsciousness is reported: a pilot already unconscious before this hit produces no
 * second event.
 *
 * Returns the updated unit and the events produced (1 or 2 entries, in order).
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
 * (`docs/rules/pilot.md` §2). ASSUMPTION: the doc does not state the recovery target, so
 * this reuses [CONSCIOUSNESS_TARGET] keyed by the pilot's *current* (unchanged) hit
 * count, mirroring [HeatPhaseHandler]'s shutdown/restart pattern — an "avoid bad state"
 * roll while down, a recovery attempt every turn while up.
 *
 * Only called by [battletech.tactical.heat.HeatPhaseHandler.onEntry] when
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
 * Applies the pilot hits an ammo explosion inflicts on [unit] (`docs/rules/pilot.md` §1): each
 * hit runs its own consciousness check via [applyPilotHit]. Shared by [CriticalHitResolution]'s
 * two detonation sites (limb blow-off, ordinary crit) and
 * [battletech.tactical.heat.HeatPhaseHandler]'s heat-driven cook-off.
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
