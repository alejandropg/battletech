package battletech.tui.view.record

import battletech.tactical.unit.ForeignUnit
import battletech.tui.view.UnitLabel
import tenter.screen.Canvas
import tenter.view.Columns
import tenter.view.TextCursor

/**
 * The maximized record sheet for a unit the viewer does NOT own: 'Mech data and the armor
 * diagram — both public on [ForeignUnit] — plus weapon *names* only. No warrior data, no heat,
 * no crit table, and no internal structure diagram: [ForeignUnit] carries none of that data, so
 * there is nothing to redact here, only nothing to draw — the same shape as
 * [battletech.tui.view.ForeignUnitPanel] for the compact panel.
 */
internal object ForeignRecordSheetView {

    fun render(canvas: Canvas, content: TextCursor, unit: ForeignUnit) {
        content.writeLine(UnitLabel.of(unit), SheetStyles.ACCENT)
        content.newLine()

        drawBand(
            canvas,
            content,
            Columns(
                listOf(
                    Columns.Child(SheetLayout.MECH_DATA_WIDTH, MechDataCard(unit)),
                    Columns.Child(SheetLayout.DIAGRAM_WIDTH, RecordSheetDiagrams.armor(unit)),
                ),
            ),
        )
        content.newLine()

        content.writeHeader("WEAPONS")
        for (weapon in unit.weapons) {
            content.writeLine("  ${weapon.name}", SheetStyles.TEXT_PRIMARY)
        }
    }
}
