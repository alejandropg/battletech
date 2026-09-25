package io.archinaut.battletech.tui.view.record

import io.archinaut.battletech.tactical.heat.HeatScale
import io.archinaut.battletech.tactical.heat.projectHeat
import io.archinaut.battletech.tactical.model.GameMap
import io.archinaut.battletech.tactical.unit.CombatUnit
import io.archinaut.battletech.tactical.unit.HeatSource
import io.archinaut.battletech.tui.screen.HeatScaleRole
import io.archinaut.battletech.tui.view.HeatGauges
import io.archinaut.battletech.tui.view.HeatPenalties
import io.archinaut.tenter.view.TextCursor
import io.archinaut.battletech.tui.view.PreparedTextView

/**
 * The HEAT card: [HeatGauges] — the same current/dissipation/projected gauges
 * [io.archinaut.battletech.tui.view.UnitStatusView] shows — plus the record sheet's Heat Scale ladder: every
 * level from [HeatScale.MAX_HEAT] down to 0, `▶` marking [CombatUnit.currentHeat] and
 * [io.archinaut.battletech.tactical.heat.HeatProjection.projected] separately. Each of [HeatPenalties.categories]'
 * four entries (movement, to-hit, shutdown, ammo explosion) is printed on its own rung only where
 * *that* category's value changes from one heat lower — the same convention the printed sheet
 * uses (e.g. "+1 To-Hit" appears once, at 8, never restated at 9 through 30) — rather than
 * restating every currently-active category on every rung a *different* category happens to
 * change on, which would make most rows far wider than the category text itself needs. The
 * current rung has its own background; the current-exclusive, projected-inclusive interval is
 * tinted separately for heating and cooling while preserving the foreground hierarchy.
 */
internal class HeatLadder(
    private val unit: CombatUnit,
    private val map: GameMap,
    private val pendingHeat: List<HeatSource>,
) : PreparedTextView() {

    override fun render(content: TextCursor) {
        content.writeHeader("HEAT")
        content.draw(HeatGauges(unit, map, pendingHeat))
        content.newLine()

        val projection = projectHeat(unit, map, pendingHeat)
        content.writeHeader("HEAT SCALE")
        for (heat in HeatScale.MAX_HEAT downTo 0) {
            val slots = HeatPenalties.categories(heat)
            val slotsBelow = if (heat > 0) HeatPenalties.categories(heat - 1) else List(slots.size) { null }
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
            if (background != null) content.write(0, " ".repeat(content.width), style)
            content.writeRow("$marker %2d".format(heat), changed.joinToString(", "), style)
        }
    }

    private fun heatBackground(heat: Int, current: Int, projected: Int): HeatScaleRole? = when {
        heat == current -> HeatScaleRole.CURRENT_BG
        projected > current && heat > current && heat <= projected -> HeatScaleRole.HEATING_BG
        projected < current && heat < current && heat >= projected -> HeatScaleRole.COOLING_BG
        else -> null
    }
}
