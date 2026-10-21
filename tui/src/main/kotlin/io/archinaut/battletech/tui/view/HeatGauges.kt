package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tui.screen.UiRole
import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.battletech.tactical.heat.HeatScale
import io.archinaut.battletech.tactical.heat.projectHeat
import io.archinaut.battletech.tactical.model.GameMap
import io.archinaut.battletech.tactical.unit.CombatUnit
import io.archinaut.battletech.tactical.unit.HeatSource
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.widget.RowWriter

/**
 * The "Current" gauge, this-turn heat sources (committed solid, [pendingHeat] drafted), the sink
 * dissipation gauge, and the "Projected" gauge — the [io.archinaut.battletech.tactical.heat.HeatProjection]
 * preview shared by the NORMAL [UnitStatusView] and the maximized record sheet's
 * [io.archinaut.battletech.tui.view.record.HeatLadder]. Neither card's surrounding header/spacing is drawn
 * here — callers wrap this with their own [SectionHeading] and blank lines.
 */
internal class HeatGauges(
    private val unit: CombatUnit,
    private val map: GameMap,
    private val pendingHeat: List<HeatSource>,
) : MeasurableWidget {

    override fun write(content: RowWriter) {
        val projection = projectHeat(unit, map, pendingHeat)
        content.writeLine("Current")
        content.draw(heatBar(unit.currentHeat))
        for (source in projection.committed) content.writeLine("  ${source.label} +${source.amount}")
        for (source in projection.pending) content.writeLine("  ${source.label} +${source.amount}", DRAFT_STYLE)

        val sink = unit.heatSink
        val sinkSuffix =
            if (sink.type.sinkRatio == 1) "${sink.type.name} ${projection.dissipation}"
            else "${sink.type.name} ${sink.units}(${projection.dissipation})"
        content.draw(BarGauge(barWidth = 10, maxValue = projection.dissipation, value = projection.dissipated, suffix = sinkSuffix))

        content.writeLine("Projected")
        content.draw(heatBar(projection.projected))
    }

    private fun heatBar(heat: Int): BarGauge = BarGauge(barWidth = 20, maxValue = HeatScale.MAX_HEAT, value = heat)

    private companion object {
        private val DRAFT_STYLE = Style(UiRole.DRAFT)
    }
}
