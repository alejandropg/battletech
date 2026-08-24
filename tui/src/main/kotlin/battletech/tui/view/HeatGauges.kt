package battletech.tui.view

import battletech.tactical.heat.HeatScale
import battletech.tactical.heat.projectHeat
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.HeatSource
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.view.TextCursor
import tenter.view.View
import tenter.widget.Gauge

/**
 * The "Current" gauge, this-turn heat sources (committed solid, [pendingHeat] drafted), the sink
 * dissipation gauge, and the "Projected" gauge — the [battletech.tactical.heat.HeatProjection]
 * preview shared by the NORMAL [UnitStatusView] and the maximized record sheet's
 * [battletech.tui.view.record.HeatLadder]. Neither card's surrounding header/spacing is drawn
 * here — callers wrap this with their own [TextCursor.writeHeader] and blank lines.
 */
internal class HeatGauges(
    private val unit: CombatUnit,
    private val pendingHeat: List<HeatSource>,
) : View {

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)
        val projection = projectHeat(unit, pendingHeat)
        val heatBar = Gauge(barWidth = 20, maxValue = HeatScale.MAX_HEAT)

        content.writeLine("Current")
        heatBar.draw(content, 0, unit.currentHeat)
        for (source in projection.committed) content.writeLine("  ${source.label} +${source.amount}")
        for (source in projection.pending) content.writeLine("  ${source.label} +${source.amount}", DRAFT_STYLE)

        val sink = unit.heatSink
        val sinkSuffix =
            if (sink.type.sinkRatio == 1) "${sink.type.name} ${projection.dissipation}"
            else "${sink.type.name} ${sink.units}(${projection.dissipation})"
        Gauge(barWidth = 10, maxValue = projection.dissipation, suffix = sinkSuffix)
            .draw(content, 0, projection.dissipated)

        content.writeLine("Projected")
        heatBar.draw(content, 0, projection.projected)
    }

    private companion object {
        private val DRAFT_STYLE = Cell.Style(ChromeRole.DRAFT)
    }
}
