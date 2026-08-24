package battletech.tui.view.record

import battletech.tactical.heat.HeatScale
import battletech.tactical.heat.projectHeat
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.HeatSource
import battletech.tui.screen.HeatScaleRole
import tenter.screen.Canvas
import tenter.view.TextCursor
import tenter.view.View
import tenter.widget.Gauge

/**
 * The HEAT card: the same current/dissipation/projected gauges [battletech.tui.view.UnitStatusView]
 * shows, plus the record sheet's Heat Scale ladder — every level from 30 down to 0, `▶` marking
 * [CombatUnit.currentHeat] and [battletech.tactical.heat.HeatProjection.projected] separately.
 * Each of [HeatScale]'s four categories (movement, to-hit, shutdown, ammo explosion) is printed
 * on its own rung only where *that* category's value changes from one heat lower — the same
 * convention the printed sheet uses (e.g. "+1 To-Hit" appears once, at 8, never restated at 9
 * through 30) — rather than restating every currently-active category on every rung a *different*
 * category happens to change on, which would make most rows far wider than the category text
 * itself needs. The current rung has its own background; the current-exclusive, projected-inclusive
 * interval is tinted separately for heating and cooling while preserving the foreground hierarchy.
 */
internal class HeatLadder(
    private val unit: CombatUnit,
    private val pendingHeat: List<HeatSource>,
) : View {

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)
        content.writeHeader("HEAT")

        val projection = projectHeat(unit, pendingHeat)
        val heatBar = Gauge(barWidth = 20, maxValue = 30)

        content.writeLine("Current")
        heatBar.draw(content, 0, unit.currentHeat)
        for (source in projection.committed) content.writeLine("  ${source.label} +${source.amount}")
        for (source in projection.pending) content.writeLine("  ${source.label} +${source.amount}", SheetStyles.DRAFT)

        val sink = unit.heatSink
        val sinkSuffix =
            if (sink.type.sinkRatio == 1) "${sink.type.name} ${projection.dissipation}"
            else "${sink.type.name} ${sink.units}(${projection.dissipation})"
        Gauge(barWidth = 10, maxValue = projection.dissipation, suffix = sinkSuffix)
            .draw(content, 0, projection.dissipated)

        content.writeLine("Projected")
        heatBar.draw(content, 0, projection.projected)
        content.newLine()

        content.writeHeader("HEAT SCALE")
        for (heat in 30 downTo 0) {
            val slots = categorySlots(heat)
            val slotsBelow = if (heat > 0) categorySlots(heat - 1) else List(slots.size) { null }
            val changed = slots.zip(slotsBelow).mapNotNull { (current, below) -> current.takeIf { current != below } }

            val marker = when {
                heat == unit.currentHeat && heat == projection.projected -> "◀▶"
                heat == unit.currentHeat -> "◀ "
                heat == projection.projected -> " ▶"
                else -> "  "
            }
            val foregroundStyle = when {
                heat == unit.currentHeat -> SheetStyles.TEXT_PRIMARY
                heat == projection.projected -> SheetStyles.DRAFT
                else -> SheetStyles.TEXT_MUTED
            }
            val background = heatBackground(heat, unit.currentHeat, projection.projected)
            val style = background?.let { foregroundStyle.copy(bg = it) } ?: foregroundStyle
            if (background != null) canvas.writeString(0, content.row, " ".repeat(canvas.width), style)
            content.writeRow("$marker %2d".format(heat), changed.joinToString(", "), style)
        }
    }

    private fun heatBackground(heat: Int, current: Int, projected: Int): HeatScaleRole? = when {
        heat == current -> HeatScaleRole.CURRENT_BG
        projected > current && heat > current && heat <= projected -> HeatScaleRole.HEATING_BG
        projected < current && heat < current && heat >= projected -> HeatScaleRole.COOLING_BG
        else -> null
    }

    /**
     * [HeatScale]'s four penalty categories at [heat], in a fixed order/size so two heat levels'
     * results can be compared slot-by-slot regardless of which categories are active at either —
     * an absent category is `null`, not simply missing from a shorter list.
     */
    private fun categorySlots(heat: Int): List<String?> {
        val movement = HeatScale.movementPenalty(heat).takeIf { it > 0 }?.let { "-$it MP" }
        val toHit = HeatScale.toHitPenalty(heat).takeIf { it > 0 }?.let { "+$it To-Hit" }
        val shutdown = when {
            HeatScale.isAutoShutdown(heat) -> "Shutdown AUTO"
            else -> HeatScale.shutdownAvoidTarget(heat)?.let { "Shutdown $it+" }
        }
        val ammo = HeatScale.ammoExplosionAvoidTarget(heat)?.let { "Ammo $it+" }
        return listOf(movement, toHit, shutdown, ammo)
    }
}
