package battletech.tactical.unit

/** The four mech components whose critical-hit counts drive standing rules penalties. */
public enum class CriticalComponent { ENGINE, GYRO, SENSOR, LIFE_SUPPORT }

/**
 * Snapshot of one [component]'s critical-damage state: [hits] destroyed slots against the rules
 * [capacity], and the active [penalties] (current/most-severe tier only; empty when [hits] is 0).
 * Drives the Critical hit points section of the UNIT STATUS panel — the TUI formats each
 * [CritEffect] for display and contributes no business logic of its own (mirrors [HeatSource]).
 */
public data class ComponentCritStatus(
    public val component: CriticalComponent,
    public val capacity: Int,
    public val hits: Int,
    public val penalties: List<CritEffect>,
)

/**
 * One [ComponentCritStatus] per component, in Engine/Gyro/Sensor/LifeSupport order, built from the
 * crit-count helpers in [CriticalLayout.kt] and [criticalEffects] — the single tier -> effect
 * source shared with enforcement. Only the current (most severe applicable) penalty tier is
 * reported per component.
 */
public fun CombatUnit.criticalDamageStatus(): List<ComponentCritStatus> = listOf(
    engineCritStatus(),
    gyroCritStatus(),
    sensorCritStatus(),
    lifeSupportCritStatus(),
)

private fun CombatUnit.engineCritStatus(): ComponentCritStatus {
    val hits = engineCritCount()
    return ComponentCritStatus(
        CriticalComponent.ENGINE,
        ENGINE_DESTROYED_AT,
        hits,
        critEffects(CriticalComponent.ENGINE),
    )
}

private fun CombatUnit.gyroCritStatus(): ComponentCritStatus {
    val hits = gyroCritCount()
    return ComponentCritStatus(
        CriticalComponent.GYRO,
        GYRO_DESTROYED_AT,
        hits,
        critEffects(CriticalComponent.GYRO),
    )
}

private fun CombatUnit.sensorCritStatus(): ComponentCritStatus {
    val hits = sensorCritCount()
    return ComponentCritStatus(
        CriticalComponent.SENSOR,
        SENSOR_BLIND_AT,
        hits,
        critEffects(CriticalComponent.SENSOR),
    )
}

private fun CombatUnit.lifeSupportCritStatus(): ComponentCritStatus {
    val hits = lifeSupportCritCount()
    return ComponentCritStatus(
        CriticalComponent.LIFE_SUPPORT,
        LIFE_SUPPORT_FAILURE_AT,
        hits,
        critEffects(CriticalComponent.LIFE_SUPPORT),
    )
}
