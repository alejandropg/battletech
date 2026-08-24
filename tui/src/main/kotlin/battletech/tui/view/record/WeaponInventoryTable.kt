package battletech.tui.view.record

import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.Weapon
import battletech.tactical.unit.remainingShots
import battletech.tui.icon.ammoIcon
import battletech.tui.icon.infinityIcon
import battletech.tui.view.MechLabels
import tenter.screen.Canvas
import tenter.view.TextCursor
import tenter.view.TextCursor.Align.LEFT
import tenter.view.TextCursor.Align.RIGHT
import tenter.view.View

/**
 * The WEAPONS & EQUIPMENT INVENTORY card: the record sheet's Qty/Type/Loc/Ht/Dmg/Min/Sht/Med/Lng
 * table, one row per group of identical weapons in the same location, plus a right-hand ammo
 * column reusing [remainingShots] the same way [battletech.tui.view.UnitStatusView] does. Header
 * labels are always left-aligned at each column's start (matching the printed sheet), even for
 * the numeric columns whose values are right-aligned within their field.
 */
internal class WeaponInventoryTable(private val unit: CombatUnit) : View {

    private data class Row(val weapon: Weapon, val qty: Int)

    private data class Column(
        val label: String,
        val x: Int,
        val width: Int,
        val align: TextCursor.Align,
        val value: (Row) -> String,
    )

    private val columns: List<Column> = listOf(
        Column("Qty", 0, 4, LEFT) { it.qty.toString() },
        Column("Type", 4, 17, LEFT) { it.weapon.name },
        Column("Loc", 21, 4, LEFT) { MechLabels.abbreviation(it.weapon.location) },
        Column("Ht", 25, 2, RIGHT) { it.weapon.heat.toString() },
        Column("Dmg", 29, 3, RIGHT) { it.weapon.damage.toString() },
        Column("Min", 34, 3, RIGHT) { it.weapon.minimumRange.toString() },
        Column("Sht", 39, 3, RIGHT) { it.weapon.shortRange.toString() },
        Column("Med", 44, 3, RIGHT) { it.weapon.mediumRange.toString() },
        Column("Lng", 49, 3, RIGHT) { it.weapon.longRange.toString() },
        Column("Ammo", 54, 4, RIGHT) { ammoLabel(it.weapon) },
    )

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)
        content.writeHeader("WEAPONS & EQUIPMENT INVENTORY")

        for (column in columns) content.write(column.x, column.label, SheetStyles.TEXT_MUTED)
        content.newLine()

        for (row in groupedRows()) {
            val style = if (row.weapon.destroyed) SheetStyles.DESTROYED else SheetStyles.TEXT_PRIMARY
            for (column in columns) content.write(column.x, column.width, column.value(row), style, column.align)
            content.newLine()
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
