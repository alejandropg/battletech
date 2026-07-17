package battletech.tui.view

import battletech.tactical.unit.ForeignUnit
import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.ContentWriter

/**
 * Renders a [ForeignUnit] — name, movement, armor (front + rear values only,
 * no internal structure), and weapon names — shared by [TargetStatusView]
 * and [UnitStatusView]. This is the redacted view a player sees of a unit he
 * does not own; there is no private field to omit here because [ForeignUnit]
 * doesn't carry one.
 */
internal object ForeignUnitPanel {
    fun render(content: ContentWriter, unit: ForeignUnit) {
        // UNIT
        with(content) {
            writeln(unit.name, Cell.Style(Color.BRIGHT_YELLOW))
            newLine()
        }

        // MOVEMENT
        with(content) {
            writeHeader("MOVEMENT")
            writeln("Walk : ${unit.walkingMP}    Run : ${unit.runningMP}", Cell.Style(Color.WHITE))
            if (unit.jumpMP > 0) writeln("Jump : ${unit.jumpMP}", Cell.Style(Color.WHITE))
            newLine()
        }

        // ARMOR
        with(content) {
            val armor = unit.armor
            writeHeader("ARMOR")
            writeStr(9, "HD:%2d".format(armor.head), Cell.Style(Color.CYAN))
            newLine()
            writeStr(2, "LT:%2d".format(armor.leftTorso), Cell.Style(Color.GREEN))
            writeStr(9, "CT:%2d".format(armor.centerTorso), Cell.Style(Color.BRIGHT_YELLOW))
            writeStr(16, "RT:%2d".format(armor.rightTorso), Cell.Style(Color.GREEN))
            newLine()
            writeStr(3, "r:%2d".format(armor.leftTorsoRear), Cell.Style(Color.DEFAULT))
            writeStr(10, "r:%2d".format(armor.centerTorsoRear), Cell.Style(Color.DEFAULT))
            writeStr(17, "r:%2d".format(armor.rightTorsoRear), Cell.Style(Color.DEFAULT))
            newLine()
            writeStr(0, "LA:%2d".format(armor.leftArm), Cell.Style(Color.GREEN))
            writeStr(17, "RA:%2d".format(armor.rightArm), Cell.Style(Color.GREEN))
            newLine()
            writeStr(3, "LL:%2d".format(armor.leftLeg), Cell.Style(Color.GREEN))
            writeStr(14, "RL:%2d".format(armor.rightLeg), Cell.Style(Color.GREEN))
            repeat(2) { newLine() }
        }

        // WEAPONS
        with(content) {
            writeHeader("WEAPONS")
            for (weapon in unit.weapons) {
                writeln("  ${weapon.name}", Cell.Style(Color.WHITE))
            }
        }
    }
}
