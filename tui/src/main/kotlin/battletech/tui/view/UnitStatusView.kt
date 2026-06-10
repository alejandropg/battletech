package battletech.tui.view

import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.HeatSource
import battletech.tui.game.PanelId
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer

public class UnitStatusView(
    private val unit: CombatUnit?,
    private val pendingHeat: List<HeatSource> = emptyList(),
) : View {

    public companion object {
        public val INDEX: Int = PanelId.UNIT_STATUS.index
        public const val TITLE: String = "UNIT STATUS"
    }

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        buffer.drawBox(x, y, width, height, "UNIT STATUS", index = INDEX)

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
        // 30 is the canonical BattleTech heat scale. It can't be drawn 1-char-per-heat
        // in this panel, so the bar is proportionally scaled: a fixed 20-cell bar spans
        // the whole 0–30 range (each block ≈ 1.5 heat). The exact value sits below it.
        val maxHeat = 30
        val barWidth = 22
        val filled = (unit.currentHeat * barWidth / maxHeat).coerceIn(0, barWidth)
        val bar = "█".repeat(filled) + "░".repeat(barWidth - filled)
        val heatColor = when {
            unit.currentHeat >= maxHeat * 0.7 -> Color.RED
            unit.currentHeat >= maxHeat * 0.3 -> Color.YELLOW
            else -> Color.GREEN
        }
        // Bar indented two columns. The value below omits the constant max and is
        // right-aligned so its last digit sits under the last filled cell, tracking
        // the fill as heat rises.
        buffer.writeString(cx, cy, "[$bar]", heatColor)
        cy += 1
        val value = unit.currentHeat.toString()
        // First bar cell is at cx + 1 (the "[" prefix). Anchor on the last filled
        // cell, or the first cell when empty, then right-align the number to it.
        val anchorCol = cx + filled.coerceAtLeast(1)
        buffer.writeString(anchorCol - value.length + 1, cy, value, heatColor)
        cy += 1

        // Heat generated this turn: committed sources in the default colour, the
        // in-progress (hovered move / selected weapons) preview in gray.
        for (source in unit.heatGeneratedThisTurn) {
            buffer.writeString(cx, cy, "  ${source.label} +${source.amount}", Color.DEFAULT)
            cy += 1
        }
        for (source in pendingHeat) {
            buffer.writeString(cx, cy, "  ${source.label} +${source.amount}", Color.GRAY)
            cy += 1
        }
        // Projected end-of-turn heat: current + generated − dissipation. Gray
        // while a preview is pending, since it is hypothetical until committed.
        val committedGenerated = unit.heatGeneratedThisTurn.sumOf { it.amount }
        val previewGenerated = pendingHeat.sumOf { it.amount }
        val projected = (unit.currentHeat + committedGenerated + previewGenerated - unit.heatSinkCapacity)
            .coerceAtLeast(0)
        val projectedColor = if (pendingHeat.isEmpty()) Color.DEFAULT else Color.GRAY
        buffer.writeString(cx, cy, "  End: $projected", projectedColor)
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
