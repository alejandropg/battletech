package battletech.tui.view

import battletech.tactical.unit.ForeignUnit
import tenter.screen.Cell
import tenter.screen.UiRole
import tenter.view.ContentWriter

/**
 * Renders a [ForeignUnit] — name, movement, armor (front + rear values only,
 * no internal structure), and weapon names — shared by [TargetStatusView]
 * and [UnitStatusView]. This is the redacted view a player sees of a unit he
 * does not own; there is no private field to omit here because [ForeignUnit]
 * doesn't carry one.
 */
internal object ForeignUnitPanel {

    private val ACCENT_STYLE = Cell.Style(UiRole.ACCENT)
    private val TEXT_PRIMARY_STYLE = Cell.Style(UiRole.TEXT_PRIMARY)
    private val INFO_STYLE = Cell.Style(UiRole.INFO)
    private val SUCCESS_STYLE = Cell.Style(UiRole.SUCCESS)

    fun render(content: ContentWriter, unit: ForeignUnit) {
        // UNIT
        with(content) {
            writeln(UnitLabel.of(unit), ACCENT_STYLE)
            newLine()
        }

        // MOVEMENT
        with(content) {
            writeHeader("MOVEMENT")
            writeln("Walk : ${unit.walkingMP}    Run : ${unit.runningMP}", TEXT_PRIMARY_STYLE)
            if (unit.jumpMP > 0) writeln("Jump : ${unit.jumpMP}", TEXT_PRIMARY_STYLE)
            newLine()
        }

        // ARMOR
        with(content) {
            val armor = unit.armor
            writeHeader("ARMOR")
            writeStr(9, "HD:%2d".format(armor.head), INFO_STYLE)
            newLine()
            writeStr(2, "LT:%2d".format(armor.leftTorso), SUCCESS_STYLE)
            writeStr(9, "CT:%2d".format(armor.centerTorso), ACCENT_STYLE)
            writeStr(16, "RT:%2d".format(armor.rightTorso), SUCCESS_STYLE)
            newLine()
            writeStr(3, "r:%2d".format(armor.leftTorsoRear), Cell.Style.DEFAULT)
            writeStr(10, "r:%2d".format(armor.centerTorsoRear), Cell.Style.DEFAULT)
            writeStr(17, "r:%2d".format(armor.rightTorsoRear), Cell.Style.DEFAULT)
            newLine()
            writeStr(0, "LA:%2d".format(armor.leftArm), SUCCESS_STYLE)
            writeStr(17, "RA:%2d".format(armor.rightArm), SUCCESS_STYLE)
            newLine()
            writeStr(3, "LL:%2d".format(armor.leftLeg), SUCCESS_STYLE)
            writeStr(14, "RL:%2d".format(armor.rightLeg), SUCCESS_STYLE)
            repeat(2) { newLine() }
        }

        // WEAPONS
        with(content) {
            writeHeader("WEAPONS")
            for (weapon in unit.weapons) {
                writeln("  ${weapon.name}", TEXT_PRIMARY_STYLE)
            }
        }
    }
}
