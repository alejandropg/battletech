package battletech.tui.view

import battletech.tactical.heat.HeatScale
import tenter.screen.ChromeRole

/**
 * Heat-scale penalty text, read off [HeatScale] in one place so [UnitStatusView] and the
 * maximized record sheet's [battletech.tui.view.record.HeatLadder] never restate it differently.
 */
internal object HeatPenalties {

    /**
     * The four heat-scale categories — movement, to-hit, shutdown, ammo explosion — active at
     * [heat], in that fixed order/size so two heat levels' results can be compared slot-by-slot
     * regardless of which categories are active at either (an absent category is `null`, not
     * simply missing from a shorter list). [HeatLadder] uses this per-rung, on the difference
     * between one heat level and the next; [lines] uses it to compare [current] against
     * projected.
     */
    fun categories(heat: Int): List<String?> {
        val movement = HeatScale.movementPenalty(heat).takeIf { it > 0 }?.let { "-$it MP" }
        val toHit = HeatScale.toHitPenalty(heat).takeIf { it > 0 }?.let { "+$it To-Hit" }
        val shutdown = when {
            HeatScale.isAutoShutdown(heat) -> "Shutdown AUTO"
            else -> HeatScale.shutdownAvoidTarget(heat)?.let { "Shutdown $it+" }
        }
        val ammo = HeatScale.ammoExplosionAvoidTarget(heat)?.let { "Ammo $it+" }
        return listOf(movement, toHit, shutdown, ammo)
    }

    /**
     * One line per active category, its text taken at whichever of [current]/[projected] is
     * worse (every category in [categories] is non-decreasing in heat, so the worse of two heat
     * levels' text is always one of the two, never a blend). A line is solid
     * ([ChromeRole.DEFAULT]) when that worst text is already in force at [current]; otherwise
     * it's projection-only ([ChromeRole.DRAFT]).
     */
    fun lines(current: Int, projected: Int): List<Pair<String, ChromeRole>> {
        val atCurrent = categories(current)
        val worst = categories(maxOf(current, projected))
        return worst.zip(atCurrent).mapNotNull { (worstLine, currentLine) ->
            worstLine?.let { it to (if (currentLine == worstLine) ChromeRole.DEFAULT else ChromeRole.DRAFT) }
        }
    }
}
