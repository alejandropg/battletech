package battletech.tui.view.record

import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.Weapon
import battletech.tactical.unit.availableAmmoBins
import battletech.tui.hex.ammoIcon
import battletech.tui.hex.infinityIcon
import battletech.tui.view.MechLabels
import tenter.screen.Canvas
import tenter.view.TextCursor
import tenter.view.View

/**
 * The WEAPONS & EQUIPMENT INVENTORY card: the record sheet's Qty/Type/Loc/Ht/Dmg/Min/Sht/Med/Lng
 * table, one row per group of identical weapons in the same location, plus a right-hand ammo
 * column reusing [availableAmmoBins] the same way [battletech.tui.view.UnitStatusView] does.
 */
internal class WeaponInventoryTable(private val unit: CombatUnit) : View {

    private data class Row(val weapon: Weapon, val qty: Int)

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)
        content.writeHeader("WEAPONS & EQUIPMENT INVENTORY")

        content.write(COL_QTY, "Qty", SheetStyles.TEXT_MUTED)
        content.write(COL_TYPE, "Type", SheetStyles.TEXT_MUTED)
        content.write(COL_LOC, "Loc", SheetStyles.TEXT_MUTED)
        content.write(COL_HEAT, "Ht", SheetStyles.TEXT_MUTED)
        content.write(COL_DAMAGE, "Dmg", SheetStyles.TEXT_MUTED)
        content.write(COL_MIN, "Min", SheetStyles.TEXT_MUTED)
        content.write(COL_SHORT, "Sht", SheetStyles.TEXT_MUTED)
        content.write(COL_MEDIUM, "Med", SheetStyles.TEXT_MUTED)
        content.write(COL_LONG, "Lng", SheetStyles.TEXT_MUTED)
        content.write(COL_AMMO, "Ammo", SheetStyles.TEXT_MUTED)
        content.newLine()

        for (row in groupedRows()) {
            val style = if (row.weapon.destroyed) SheetStyles.DESTROYED else SheetStyles.TEXT_PRIMARY
            content.write(COL_QTY, row.qty.toString(), style)
            content.write(COL_TYPE, row.weapon.name, style)
            content.write(COL_LOC, MechLabels.abbreviation(row.weapon.location), style)
            content.write(COL_HEAT, row.weapon.heat.toString(), style)
            content.write(COL_DAMAGE, row.weapon.damage.toString(), style)
            content.write(COL_MIN, row.weapon.minimumRange.toString(), style)
            content.write(COL_SHORT, row.weapon.shortRange.toString(), style)
            content.write(COL_MEDIUM, row.weapon.mediumRange.toString(), style)
            content.write(COL_LONG, row.weapon.longRange.toString(), style)
            content.write(COL_AMMO, ammoLabel(row.weapon), style)
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
        val remaining = unit.availableAmmoBins().filter { it.third.type == type }.sumOf { it.third.shots }
        return "$remaining ${ammoIcon()}"
    }

    private companion object {
        const val COL_QTY = 0
        const val COL_TYPE = 4
        const val COL_LOC = 21
        const val COL_HEAT = 25
        const val COL_DAMAGE = 29
        const val COL_MIN = 34
        const val COL_SHORT = 39
        const val COL_MEDIUM = 44
        const val COL_LONG = 49
        const val COL_AMMO = 54
    }
}
