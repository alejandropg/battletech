package battletech.tui.view

import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.HeatSource
import battletech.tui.game.PanelId
import battletech.tui.screen.Color
import battletech.tui.screen.ContentWriter
import battletech.tui.screen.ScreenBuffer

public class UnitStatusView(
    private val unit: CombatUnit?,
    private val pendingHeat: List<HeatSource> = emptyList(),
) : View {

    public companion object {
        public val INDEX: Int = PanelId.UNIT_STATUS.index
        public const val TITLE: String = "UNIT STATUS"
        private const val PADDING = 2
    }

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        buffer.drawBox(x, y, width, height, "UNIT STATUS", index = INDEX)

        val content = ContentWriter(buffer, x + PADDING, y + PADDING, width - (PADDING * 2))

        if (unit == null) {
            content.writeln("No unit selected", Color.WHITE)
            return
        }

        // UNIT
        with(content) {
            writeln(unit.name, Color.BRIGHT_YELLOW)
            newLine()
        }

        // PILOT
        with(content) {
            writeHeader("PILOT")
            writeln("Gunnery  : ${unit.gunnerySkill}", Color.WHITE)
            writeln("Piloting : ${unit.pilotingSkill}", Color.WHITE)
            newLine()
        }

        // MOVEMENT
        with(content) {
            writeHeader("MOVEMENT")
            writeln("Walk : ${unit.walkingMP}    Run : ${unit.runningMP}", Color.WHITE)
            if (unit.jumpMP > 0) writeln("Jump : ${unit.jumpMP}", Color.WHITE)
            newLine()
        }

        // HEAT
        with(content) {
            writeHeader("HEAT")
            writeln("Current")
            val heatBar = HeatBarWidget(barWidth = 20, maxValue = 30)
            content.cy = heatBar.draw(buffer, content.x, content.cy, unit.currentHeat)

            // Heat generated this turn: committed sources in the default colour, the
            // in-progress (hovered move / selected weapons) preview in gray.
            for (source in unit.heatGeneratedThisTurn) {
                writeln("  ${source.label} +${source.amount}")
            }
            for (source in pendingHeat) {
                writeln("  ${source.label} +${source.amount}", Color.GRAY)
            }

            val committedGenerated = unit.heatGeneratedThisTurn.sumOf { it.amount }
            val previewGenerated = pendingHeat.sumOf { it.amount }

            // Dissipation bar: shows how much heat will be removed this turn, capped at
            // the sink's capacity. Includes the pending preview so the player sees the
            // full dissipation picture before committing.
            val sink = unit.heatSink
            val dissipation = sink.dissipation()
            val sinkSuffix =
                if (sink.type.sinkRatio == 1) "${sink.type.name} $dissipation"
                else "${sink.type.name} ${sink.units}($dissipation)"
            val dissipated = minOf(unit.currentHeat + committedGenerated + previewGenerated, dissipation)
            content.cy = HeatBarWidget(barWidth = 10, maxValue = dissipation, suffix = sinkSuffix)
                .draw(buffer, content.x, content.cy, dissipated)

            // Projected end-of-turn heat: current + generated − dissipation, coloured
            // by the same thresholds as the current bar regardless of pending state.
            writeln("Projected")
            val projected = (unit.currentHeat + committedGenerated + previewGenerated - dissipation).coerceAtLeast(0)
            content.cy = heatBar.draw(buffer, content.x, content.cy, projected)
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
                val ammoStr = weapon.ammo?.let { " [$it]" } ?: ""
                writeln("  ${weapon.name}$ammoStr", Color.WHITE)
            }
        }
    }
}
