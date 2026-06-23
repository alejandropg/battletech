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

/** Convenience: effects for [component] given this unit's current crit count for it. */
public fun CombatUnit.critEffects(component: CriticalComponent): List<CritEffect> {
    val hits = when (component) {
        CriticalComponent.ENGINE -> engineCritCount()
        CriticalComponent.GYRO -> gyroCritCount()
        CriticalComponent.SENSOR -> sensorCritCount()
        CriticalComponent.LIFE_SUPPORT -> lifeSupportCritCount()
    }
    return criticalEffects(component, hits)
}

/** Sum of this unit's active engine [CritEffect.HeatPerTurn] amounts (0 when no engine crits). */
public fun CombatUnit.engineHeatPerTurn(): Int =
    critEffects(CriticalComponent.ENGINE).filterIsInstance<CritEffect.HeatPerTurn>().sumOf { it.amount }

/** True when sensor damage has blinded this unit ([CritEffect.CannotFire] tier reached). */
public fun CombatUnit.cannotFireFromSensorDamage(): Boolean =
    critEffects(CriticalComponent.SENSOR).any { it is CritEffect.CannotFire }

/** True when gyro damage prevents this unit from standing ([CritEffect.CannotStand] tier reached). */
public fun CombatUnit.cannotStandFromGyroDamage(): Boolean =
    critEffects(CriticalComponent.GYRO).any { it is CritEffect.CannotStand }
