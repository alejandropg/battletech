package battletech.tui.view

import battletech.tactical.heat.HeatScale
import tenter.screen.ChromeRole

/**
 * Single-worst-value-per-category heat penalty lines for a unit's current vs. projected heat —
 * shared by [UnitStatusView] and the maximized record sheet's heat ladder so both read the same
 * penalty text off [HeatScale].
 */
internal object HeatPenalties {

    /**
     * A line is solid ([ChromeRole.DEFAULT]) when the worst value is already in force at
     * [current]; otherwise it is projection-only ([ChromeRole.DRAFT]).
     */
    fun lines(current: Int, projected: Int): List<Pair<String, ChromeRole>> {
        val lines = mutableListOf<Pair<String, ChromeRole>>()

        val mp = maxOf(HeatScale.movementPenalty(current), HeatScale.movementPenalty(projected))
        if (mp > 0) {
            val applied = HeatScale.movementPenalty(current) == mp
            lines += "-$mp MP" to (if (applied) ChromeRole.DEFAULT else ChromeRole.DRAFT)
        }

        val th = maxOf(HeatScale.toHitPenalty(current), HeatScale.toHitPenalty(projected))
        if (th > 0) {
            val applied = HeatScale.toHitPenalty(current) == th
            lines += "+$th To-Hit" to (if (applied) ChromeRole.DEFAULT else ChromeRole.DRAFT)
        }

        val currentAutoShutdown = HeatScale.isAutoShutdown(current)
        val projectedAutoShutdown = HeatScale.isAutoShutdown(projected)
        val currentShutdownTarget = HeatScale.shutdownAvoidTarget(current)
        val projectedShutdownTarget = HeatScale.shutdownAvoidTarget(projected)
        if (currentAutoShutdown || projectedAutoShutdown) {
            lines += "Shutdown AUTO" to (if (currentAutoShutdown) ChromeRole.DEFAULT else ChromeRole.DRAFT)
        } else {
            val target = maxOfNullable(currentShutdownTarget, projectedShutdownTarget)
            if (target != null) {
                val applied = currentShutdownTarget == target
                lines += "Shutdown $target+" to (if (applied) ChromeRole.DEFAULT else ChromeRole.DRAFT)
            }
        }

        val currentAmmoTarget = HeatScale.ammoExplosionAvoidTarget(current)
        val projectedAmmoTarget = HeatScale.ammoExplosionAvoidTarget(projected)
        val ammoTarget = maxOfNullable(currentAmmoTarget, projectedAmmoTarget)
        if (ammoTarget != null) {
            val applied = currentAmmoTarget == ammoTarget
            lines += "Ammo $ammoTarget+" to (if (applied) ChromeRole.DEFAULT else ChromeRole.DRAFT)
        }

        return lines
    }

    private fun maxOfNullable(a: Int?, b: Int?): Int? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }
}
