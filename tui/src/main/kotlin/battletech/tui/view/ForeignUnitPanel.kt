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

    private val BRIGHT_YELLOW_STYLE = Cell.Style(Color.BRIGHT_YELLOW)
    private val WHITE_STYLE = Cell.Style(Color.WHITE)
    private val CYAN_STYLE = Cell.Style(Color.CYAN)
    private val GREEN_STYLE = Cell.Style(Color.GREEN)

    fun render(content: ContentWriter, unit: ForeignUnit) {
        // UNIT
        with(content) {
            writeln(UnitLabel.of(unit), BRIGHT_YELLOW_STYLE)
            newLine()
        }

        // MOVEMENT
        with(content) {
            writeHeader("MOVEMENT")
            writeln("Walk : ${unit.walkingMP}    Run : ${unit.runningMP}", WHITE_STYLE)
            if (unit.jumpMP > 0) writeln("Jump : ${unit.jumpMP}", WHITE_STYLE)
            newLine()
        }

        // ARMOR
        with(content) {
            val armor = unit.armor
            writeHeader("ARMOR")
            writeStr(9, "HD:%2d".format(armor.head), CYAN_STYLE)
            newLine()
            writeStr(2, "LT:%2d".format(armor.leftTorso), GREEN_STYLE)
            writeStr(9, "CT:%2d".format(armor.centerTorso), BRIGHT_YELLOW_STYLE)
            writeStr(16, "RT:%2d".format(armor.rightTorso), GREEN_STYLE)
            newLine()
            writeStr(3, "r:%2d".format(armor.leftTorsoRear), Cell.Style.DEFAULT)
            writeStr(10, "r:%2d".format(armor.centerTorsoRear), Cell.Style.DEFAULT)
            writeStr(17, "r:%2d".format(armor.rightTorsoRear), Cell.Style.DEFAULT)
            newLine()
            writeStr(0, "LA:%2d".format(armor.leftArm), GREEN_STYLE)
            writeStr(17, "RA:%2d".format(armor.rightArm), GREEN_STYLE)
            newLine()
            writeStr(3, "LL:%2d".format(armor.leftLeg), GREEN_STYLE)
            writeStr(14, "RL:%2d".format(armor.rightLeg), GREEN_STYLE)
            repeat(2) { newLine() }
        }

        // WEAPONS
        with(content) {
            writeHeader("WEAPONS")
            for (weapon in unit.weapons) {
                writeln("  ${weapon.name}", WHITE_STYLE)
            }
        }
    }
}
