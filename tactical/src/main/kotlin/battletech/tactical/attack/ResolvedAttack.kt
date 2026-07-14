package battletech.tactical.attack

import battletech.tactical.unit.UnitId

/**
 * The minimal read surface [ImpulseAttackPhaseHandler.resolveVolleyTail] needs from a
 * resolved hit, regardless of whether it came from weapon fire or a physical attack:
 * which unit it struck, and what damage it dealt. Implemented by [AttackResult.Hit]
 * and [battletech.tactical.attack.physical.PhysicalAttackResult.Hit] — the two
 * post-Stage-4 sealed "hit" families — so the shared tail (gyro-crit effects,
 * location-destruction consequences, 20-damage PSRs) can group damage by target
 * without depending on either concrete result hierarchy.
 */
public interface ResolvedAttack {
    public val targetId: UnitId
    public val damage: List<LocationDamage>
}
