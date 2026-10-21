package io.archinaut.battletech.tui.view.record

import io.archinaut.battletech.tui.view.SectionHeading
import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.battletech.tactical.unit.CombatUnit
import io.archinaut.battletech.tactical.unit.Weapon
import io.archinaut.battletech.tactical.unit.remainingShots
import io.archinaut.battletech.tui.icon.ammoIcon
import io.archinaut.battletech.tui.icon.infinityIcon
import io.archinaut.battletech.tui.view.MechLabels
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.text.CellWidth
import io.archinaut.tenter.widget.Paragraph
import io.archinaut.tenter.widget.RowWriter
import io.archinaut.tenter.widget.Widget
import io.archinaut.tenter.widget.fixedSize

/**
 * The WEAPONS & EQUIPMENT INVENTORY card: the record sheet's Qty/Type/Loc/Ht/Dmg/Min/Sht/Med/Lng
 * table, one row per group of identical weapons in the same location, plus a right-hand ammo
 * column reusing [remainingShots] the same way [io.archinaut.battletech.tui.view.UnitStatusView] does. Header
 * labels are always left-aligned at each column's start (matching the printed sheet), even for
 * the numeric columns whose values are right-aligned within their field.
 */
internal class WeaponInventoryTable(private val unit: CombatUnit) : MeasurableWidget {

    private data class Row(val weapon: Weapon, val qty: Int)

    private enum class Align { LEFT, RIGHT }

    private data class Column(
        val label: String,
        val x: Int,
        val width: Int,
        val align: Align,
        val value: (Row) -> String,
    )

    private val columns: List<Column> = listOf(
        Column("Qty", 0, 4, Align.LEFT) { it.qty.toString() },
        Column("Type", 4, 17, Align.LEFT) { it.weapon.name },
        Column("Loc", 21, 4, Align.LEFT) { MechLabels.abbreviation(it.weapon.location) },
        Column("Ht", 25, 2, Align.RIGHT) { it.weapon.heat.toString() },
        Column("Dmg", 29, 3, Align.RIGHT) { it.weapon.damage.toString() },
        Column("Min", 34, 3, Align.RIGHT) { it.weapon.minimumRange.toString() },
        Column("Sht", 39, 3, Align.RIGHT) { it.weapon.shortRange.toString() },
        Column("Med", 44, 3, Align.RIGHT) { it.weapon.mediumRange.toString() },
        Column("Lng", 49, 3, Align.RIGHT) { it.weapon.longRange.toString() },
        Column("Ammo", 54, 4, Align.RIGHT) { ammoLabel(it.weapon) },
    )

    override fun write(content: RowWriter) {
        content.draw(SectionHeading("WEAPONS & EQUIPMENT INVENTORY"))

        for (column in columns) content.write(column.x, column.label, SheetStyles.TEXT_MUTED)
        content.newLine()

        for (row in groupedRows()) {
            val style = if (row.weapon.destroyed) SheetStyles.DESTROYED else SheetStyles.TEXT_PRIMARY
            content.draw(TableRow(row, style).fixedSize(content.width, 1))
        }
    }

    /** One row's values, each fitted into its column's field and aligned within it. */
    private inner class TableRow(private val row: Row, private val style: Style) : Widget {
        override fun render(canvas: Canvas) {
            for (column in columns) {
                val text = column.value(row)
                val slack = (column.width - CellWidth.of(text)).coerceAtLeast(0)
                val x = if (column.align == Align.LEFT) column.x else column.x + slack
                // A field narrower than its value ellipsizes it, as Paragraph fits a line to its area.
                Paragraph(text, style).render(canvas.region(Rect(x, 0, column.width - (x - column.x), 1)))
            }
        }
    }

    /** Groups weapons of identical name/location/destroyed status into one Qty-tallied row. */
    private fun groupedRows(): List<Row> =
        unit.weapons
            .groupBy { Triple(it.name, it.location, it.destroyed) }
            .map { (_, weapons) -> Row(weapons.first(), weapons.size) }

    private fun ammoLabel(weapon: Weapon): String {
        val type = weapon.ammoType ?: return infinityIcon()
        return "${unit.remainingShots(type)} ${ammoIcon()}"
    }
}
