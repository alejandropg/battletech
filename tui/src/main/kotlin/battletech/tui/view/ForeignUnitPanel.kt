package battletech.tui.view

import battletech.tactical.unit.ForeignUnit
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.view.TextCursor

/**
 * Renders a [ForeignUnit] — name, movement, armor (front + rear values only,
 * no internal structure), and weapon names — shared by [TargetStatusView]
 * and [UnitStatusView]. This is the redacted view a player sees of a unit he
 * does not own; there is no private field to omit here because [ForeignUnit]
 * doesn't carry one.
 */
internal object ForeignUnitPanel {

    private val ACCENT_STYLE = Cell.Style(ChromeRole.ACCENT)
    private val TEXT_PRIMARY_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)
    private val INFO_STYLE = Cell.Style(ChromeRole.INFO)
    private val SUCCESS_STYLE = Cell.Style(ChromeRole.SUCCESS)

    fun render(content: TextCursor, unit: ForeignUnit) {
        // UNIT
        with(content) {
            writeLine(UnitLabel.of(unit), ACCENT_STYLE)
            newLine()
        }

        // MOVEMENT
        with(content) {
            writeHeader("MOVEMENT")
            writeLine("Walk : ${unit.walkingMP}    Run : ${unit.runningMP}", TEXT_PRIMARY_STYLE)
            if (unit.jumpMP > 0) writeLine("Jump : ${unit.jumpMP}", TEXT_PRIMARY_STYLE)
            newLine()
        }

        // ARMOR
        with(content) {
            val armor = unit.armor
            writeHeader("ARMOR")
            write(9, "HD:%2d".format(armor.head), INFO_STYLE)
            newLine()
            write(2, "LT:%2d".format(armor.leftTorso), SUCCESS_STYLE)
            write(9, "CT:%2d".format(armor.centerTorso), ACCENT_STYLE)
            write(16, "RT:%2d".format(armor.rightTorso), SUCCESS_STYLE)
            newLine()
            write(3, "r:%2d".format(armor.leftTorsoRear), Cell.Style.DEFAULT)
            write(10, "r:%2d".format(armor.centerTorsoRear), Cell.Style.DEFAULT)
            write(17, "r:%2d".format(armor.rightTorsoRear), Cell.Style.DEFAULT)
            newLine()
            write(0, "LA:%2d".format(armor.leftArm), SUCCESS_STYLE)
            write(17, "RA:%2d".format(armor.rightArm), SUCCESS_STYLE)
            newLine()
            write(3, "LL:%2d".format(armor.leftLeg), SUCCESS_STYLE)
            write(14, "RL:%2d".format(armor.rightLeg), SUCCESS_STYLE)
            repeat(2) { newLine() }
        }

        // WEAPONS
        content.writeHeader("WEAPONS")
        content.draw(ForeignWeaponList(unit))
    }
}
