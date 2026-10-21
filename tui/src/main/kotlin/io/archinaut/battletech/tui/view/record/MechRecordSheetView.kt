package io.archinaut.battletech.tui.view.record

import io.archinaut.battletech.tactical.model.GameMap
import io.archinaut.battletech.tactical.unit.CombatUnit
import io.archinaut.battletech.tactical.unit.ForeignUnit
import io.archinaut.battletech.tactical.unit.HeatSource
import io.archinaut.battletech.tactical.unit.VisibleUnit
import io.archinaut.tenter.screen.Padding
import io.archinaut.tenter.widget.Block
import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.tenter.widget.RowWriter
import io.archinaut.tenter.widget.block
import kotlin.math.min

/**
 * The maximized UNIT STATUS panel: a wide, graphical 'Mech record sheet — armor and internal
 * structure as pip circles instead of bare numbers, the warrior data block, the heat ladder, the
 * full critical hit table, and the weapons & equipment inventory. [io.archinaut.battletech.tui.view.UnitStatusView]
 * remains the compact NORMAL-state list; this is the same [subject]/[pendingHeat] data laid out
 * the way the printed record sheet lays it out.
 *
 * Clamped to [SheetLayout.SHEET_WIDTH] columns regardless of how wide the maximized panel actually
 * is — a very wide terminal doesn't need an even wider sheet, just unused margin. A thin dispatcher
 * over [OwnRecordSheetView]/[ForeignRecordSheetView]: which one draws is the type-enforced
 * redaction seam, same as [subject]'s own [VisibleUnit]/[ForeignUnit]/[CombatUnit] hierarchy.
 */
internal class MechRecordSheetView(
    private val subject: VisibleUnit?,
    private val map: GameMap,
    private val pendingHeat: List<HeatSource> = emptyList(),
) : MeasurableWidget {

    override fun write(writer: RowWriter) {
        val sheetWidth = min(writer.width, SheetLayout.SHEET_WIDTH)
        val content = when (val unit = subject) {
            null -> MeasurableWidget { it.writeLine("No unit selected", SheetStyles.TEXT_PRIMARY) }
            is ForeignUnit -> ForeignRecordSheetView(unit)
            is CombatUnit -> OwnRecordSheetView(unit, map, pendingHeat)
        }
        // The margin past the sheet's width keeps it from stretching across a very wide panel.
        writer.draw(content.block(Block(padding = Padding(right = writer.width - sheetWidth))))
    }
}
