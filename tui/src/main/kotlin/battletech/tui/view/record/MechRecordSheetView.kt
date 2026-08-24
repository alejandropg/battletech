package battletech.tui.view.record

import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.ForeignUnit
import battletech.tactical.unit.HeatSource
import battletech.tactical.unit.VisibleUnit
import tenter.screen.Canvas
import tenter.view.TextCursor
import tenter.view.View
import kotlin.math.min

/**
 * The maximized UNIT STATUS panel: a wide, graphical 'Mech record sheet — armor and internal
 * structure as pip circles instead of bare numbers, the warrior data block, the heat ladder, the
 * full critical hit table, and the weapons & equipment inventory. [battletech.tui.view.UnitStatusView]
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
    private val pendingHeat: List<HeatSource> = emptyList(),
) : View {

    override fun draw(canvas: Canvas) {
        val sheet = canvas.region(0, 0, min(canvas.width, SheetLayout.SHEET_WIDTH), canvas.height)

        when (val unit = subject) {
            null -> TextCursor(sheet).writeLine("No unit selected", SheetStyles.TEXT_PRIMARY)
            is ForeignUnit -> ForeignRecordSheetView(unit).draw(sheet)
            is CombatUnit -> OwnRecordSheetView(unit, pendingHeat).draw(sheet)
        }
    }
}
