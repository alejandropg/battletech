package battletech.tui.view.record

import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.ForeignUnit
import battletech.tactical.unit.HeatSource
import battletech.tactical.unit.VisibleUnit
import battletech.tui.view.UnitLabel
import tenter.screen.Canvas
import tenter.view.Columns
import tenter.view.Stack
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
 * is — a very wide terminal doesn't need an even wider sheet, just unused margin. Every card below
 * is composed through [Columns], so a narrower terminal reflows to fewer cards per row rather than
 * clipping; height is unlimited and left to the panel's own vertical scroll.
 */
internal class MechRecordSheetView(
    private val subject: VisibleUnit?,
    private val pendingHeat: List<HeatSource> = emptyList(),
) : View {

    override fun draw(canvas: Canvas) {
        val sheet = canvas.region(0, 0, min(canvas.width, SheetLayout.SHEET_WIDTH), canvas.height)
        val content = TextCursor(sheet)

        when (val unit = subject) {
            null -> content.writeLine("No unit selected", SheetStyles.TEXT_PRIMARY)
            is ForeignUnit -> ForeignRecordSheetView.render(sheet, content, unit)
            is CombatUnit -> drawOwnSheet(sheet, content, unit)
        }
    }

    private fun drawOwnSheet(canvas: Canvas, content: TextCursor, unit: CombatUnit) {
        content.writeLine(UnitLabel.of(unit), SheetStyles.ACCENT)
        content.newLine()

        drawBand(
            canvas,
            content,
            Columns(
                listOf(
                    Columns.Child(SheetLayout.MECH_DATA_WIDTH, Stack(listOf(MechDataCard(unit), WarriorDataCard(unit)))),
                    Columns.Child(
                        SheetLayout.DIAGRAM_WIDTH,
                        Stack(listOf(RecordSheetDiagrams.armor(unit), RecordSheetDiagrams.internalStructure(unit))),
                    ),
                    Columns.Child(SheetLayout.HEAT_WIDTH, HeatLadder(unit, pendingHeat)),
                ),
            ),
        )
        content.newLine()

        drawBand(canvas, content, WeaponInventoryTable(unit))
        content.newLine()

        drawBand(canvas, content, CriticalHitTable(unit))
    }
}
