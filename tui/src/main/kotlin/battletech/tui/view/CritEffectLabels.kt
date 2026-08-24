package battletech.tui.view

import battletech.tactical.unit.CritEffect

/** Renders one [CritEffect] as its UNIT STATUS panel / record sheet string. */
internal fun formatCritEffect(effect: CritEffect): String = when (effect) {
    is CritEffect.ToHitPenalty -> "+${effect.amount} To-Hit"
    is CritEffect.CannotFire -> "Cannot fire"
    is CritEffect.PsrPenalty -> "+${effect.amount} PSR"
    is CritEffect.CannotStand -> "Cannot stand"
    is CritEffect.HeatPerTurn -> "+${effect.amount} Heat/turn"
    is CritEffect.PilotDamageWhenHeatAtLeast -> "Pilot hit @ ${effect.heat}+ heat"
    is CritEffect.PilotDamageEachTurn -> "Pilot hit / turn"
}
