package battletech.tactical.unit

/**
 * A single mechanical consequence of a critical-hit tier. Each [CriticalComponent]'s
 * active effects at a given hit count are produced by [criticalEffects] — THE single
 * source of "which effect applies at which crit tier" that both enforcement (gyro PSR,
 * sensor to-hit, engine heat, blind/cannot-stand checks, life-support pilot damage) and
 * display ([criticalDamageStatus]) derive from.
 */
public sealed interface CritEffect {
    public data class ToHitPenalty(public val amount: Int) : CritEffect
    public data object CannotFire : CritEffect
    public data class PsrPenalty(public val amount: Int) : CritEffect
    public data object CannotStand : CritEffect
    public data class HeatPerTurn(public val amount: Int) : CritEffect
    public data class PilotDamageWhenHeatAtLeast(public val heat: Int) : CritEffect
    public data object PilotDamageEachTurn : CritEffect
}

/**
 * THE single source of the crit tier -> effect mapping (most-severe tier per component;
 * `docs/rules/armor-damage.md` §3). Both enforcement and display ([criticalDamageStatus])
 * derive from this — add or change a tier here and every consumer follows.
 */
public fun criticalEffects(component: CriticalComponent, hits: Int): List<CritEffect> = when (component) {
    CriticalComponent.ENGINE -> when {
        hits in 1..2 -> listOf(CritEffect.HeatPerTurn(hits * ENGINE_CRIT_HEAT_PER_HIT))
        // 3+ destroys the unit outright via destructionReason, not an ongoing penalty.
        else -> emptyList()
    }

    CriticalComponent.GYRO -> when {
        hits >= GYRO_DESTROYED_AT -> listOf(CritEffect.CannotStand)
        hits >= 1 -> listOf(CritEffect.PsrPenalty(GYRO_PSR_PENALTY))
        else -> emptyList()
    }

    CriticalComponent.SENSOR -> when {
        hits >= SENSOR_BLIND_AT -> listOf(CritEffect.CannotFire)
        hits >= SENSOR_HIT_TO_HIT_PENALTY_AT -> listOf(CritEffect.ToHitPenalty(SENSOR_TO_HIT_PENALTY))
        else -> emptyList()
    }

    CriticalComponent.LIFE_SUPPORT -> when {
        hits >= LIFE_SUPPORT_FAILURE_AT -> listOf(CritEffect.PilotDamageEachTurn)
        hits >= 1 -> listOf(CritEffect.PilotDamageWhenHeatAtLeast(LIFE_SUPPORT_HEAT_THRESHOLD))
        else -> emptyList()
    }
}

// The CombatUnit-facing extensions that consume this mapping (critEffects,
// engineHeatPerTurn, cannotFireFromSensorDamage, cannotStandFromGyroDamage) live in
// CriticalDamage.kt, alongside the rest of CombatUnit's critical-damage behavior.
