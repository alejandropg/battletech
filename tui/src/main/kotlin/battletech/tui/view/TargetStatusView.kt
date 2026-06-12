package battletech.tui.view

import battletech.tactical.query.PublicUnit
import battletech.tui.game.PanelId
import battletech.tui.screen.Color
import battletech.tui.screen.ContentWriter
import battletech.tui.screen.ScreenBuffer

public class TargetStatusView(private val unit: PublicUnit?) : View {

    public companion object {
        public val INDEX: Int = PanelId.TARGET_STATUS.index
        public const val TITLE: String = "TARGET STATUS"
        private const val PADDING = 2
    }

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        buffer.drawBox(x, y, width, height, "TARGET STATUS", index = INDEX)

        val content = ContentWriter(buffer, x + PADDING, y + PADDING, width - (PADDING * 2))

        if (unit == null) {
            content.writeln("No target selected", Color.WHITE)
            return
        }

        // UNIT
        with(content) {
            writeln(unit.name, Color.BRIGHT_YELLOW)
            newLine()
        }

        // MOVEMENT
        with(content) {
            writeHeader("MOVEMENT")
            writeln("Walk : ${unit.walkingMP}    Run : ${unit.runningMP}", Color.WHITE)
            if (unit.jumpMP > 0) writeln("Jump : ${unit.jumpMP}", Color.WHITE)
            newLine()
        }

        // ARMOR
        with(content) {
            val armor = unit.armor
            writeHeader("ARMOR")
            writeStr(9, "HD:%2d".format(armor.head), Color.CYAN)
            newLine()
            writeStr(2, "LT:%2d".format(armor.leftTorso), Color.GREEN)
            writeStr(9, "CT:%2d".format(armor.centerTorso), Color.BRIGHT_YELLOW)
            writeStr(16, "RT:%2d".format(armor.rightTorso), Color.GREEN)
            newLine()
            writeStr(3, "r:%2d".format(armor.leftTorsoRear), Color.DEFAULT)
            writeStr(10, "r:%2d".format(armor.centerTorsoRear), Color.DEFAULT)
            writeStr(17, "r:%2d".format(armor.rightTorsoRear), Color.DEFAULT)
            newLine()
            writeStr(0, "LA:%2d".format(armor.leftArm), Color.GREEN)
            writeStr(17, "RA:%2d".format(armor.rightArm), Color.GREEN)
            newLine()
            writeStr(3, "LL:%2d".format(armor.leftLeg), Color.GREEN)
            writeStr(14, "RL:%2d".format(armor.rightLeg), Color.GREEN)
            repeat(2) { newLine() }
        }

        // WEAPONS
        with(content) {
            writeHeader("WEAPONS")
            for (weapon in unit.weapons) {
                if (content.cy >= y + height - 1) break
                writeln("  ${weapon.name}", Color.WHITE)
            }
        }
    }
}
