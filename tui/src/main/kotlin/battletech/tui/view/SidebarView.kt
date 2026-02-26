package battletech.tui.view

import battletech.tactical.action.CombatUnit
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer

public class SidebarView(
    private val unit: CombatUnit?,
) : View {

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        buffer.drawBox(x, y, width, height, "UNIT STATUS")

        val cx = x + 2
        var cy = y + 2

        if (unit == null) {
            buffer.writeString(cx, cy, "No unit selected", Color.WHITE)
            return
        }

        buffer.writeString(cx, cy, unit.name, Color.BRIGHT_YELLOW)
        cy += 2

        val innerWidth = width - 6
        fun sectionHeader(label: String): String {
            val dashes = (innerWidth - label.length - 1).coerceAtLeast(0)
            return "$label ${"─".repeat(dashes)}"
        }

        // PILOT
        buffer.writeString(cx, cy, sectionHeader("PILOT"), Color.CYAN)
        cy += 1
        buffer.writeString(cx, cy, "Gunnery  : ${unit.gunnerySkill}", Color.WHITE)
        cy += 1
        buffer.writeString(cx, cy, "Piloting : ${unit.pilotingSkill}", Color.WHITE)
        cy += 2

        // MOVEMENT
        buffer.writeString(cx, cy, sectionHeader("MOVEMENT"), Color.CYAN)
        cy += 1
        buffer.writeString(cx, cy, "Walk : ${unit.walkingMP}    Run : ${unit.runningMP}", Color.WHITE)
        cy += 1
        if (unit.jumpMP > 0) {
            buffer.writeString(cx, cy, "Jump : ${unit.jumpMP}", Color.WHITE)
            cy += 1
        }
        cy += 1

        // HEAT
        buffer.writeString(cx, cy, sectionHeader("HEAT"), Color.CYAN)
        cy += 1
        val barWidth = 10
        val filled = if (unit.heatSinkCapacity > 0)
            (unit.currentHeat * barWidth / unit.heatSinkCapacity).coerceAtMost(barWidth)
        else 0
        val bar = "█".repeat(filled) + "░".repeat(barWidth - filled)
        val heatColor = when {
            unit.heatSinkCapacity == 0 -> Color.WHITE
            unit.currentHeat >= unit.heatSinkCapacity * 0.7 -> Color.RED
            unit.currentHeat >= unit.heatSinkCapacity * 0.3 -> Color.YELLOW
            else -> Color.GREEN
        }
        buffer.writeString(cx, cy, "[$bar]  ${unit.currentHeat} / ${unit.heatSinkCapacity}", heatColor)
        cy += 2

        // ARMOR
        val armor = unit.armor
        buffer.writeString(cx, cy, sectionHeader("ARMOR"), Color.CYAN)
        cy += 1
        buffer.writeString(cx + 9, cy, "HD:%2d".format(armor.head), Color.CYAN)
        cy += 1
        buffer.writeString(cx + 2, cy, "LT:%2d".format(armor.leftTorso), Color.GREEN)
        buffer.writeString(cx + 9, cy, "CT:%2d".format(armor.centerTorso), Color.BRIGHT_YELLOW)
        buffer.writeString(cx + 16, cy, "RT:%2d".format(armor.rightTorso), Color.GREEN)
        cy += 1
        buffer.writeString(cx + 3, cy, "r:%2d".format(armor.leftTorsoRear), Color.DEFAULT)
        buffer.writeString(cx + 10, cy, "r:%2d".format(armor.centerTorsoRear), Color.DEFAULT)
        buffer.writeString(cx + 17, cy, "r:%2d".format(armor.rightTorsoRear), Color.DEFAULT)
        cy += 1
        buffer.writeString(cx + 0, cy, "LA:%2d".format(armor.leftArm), Color.GREEN)
        buffer.writeString(cx + 17, cy, "RA:%2d".format(armor.rightArm), Color.GREEN)
        cy += 1
        buffer.writeString(cx + 3, cy, "LL:%2d".format(armor.leftLeg), Color.GREEN)
        buffer.writeString(cx + 14, cy, "RL:%2d".format(armor.rightLeg), Color.GREEN)
        cy += 2

        // WEAPONS
        buffer.writeString(cx, cy, sectionHeader("WEAPONS"), Color.CYAN)
        cy += 1
        for (weapon in unit.weapons) {
            if (cy >= y + height - 1) break
            val ammoStr = weapon.ammo?.let { " [$it]" } ?: ""
            buffer.writeString(cx, cy, "  ${weapon.name}$ammoStr", Color.WHITE)
            cy += 1
        }
    }
}
