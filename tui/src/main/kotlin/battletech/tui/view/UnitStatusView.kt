package battletech.tui.view

import battletech.tactical.heat.projectHeat
import battletech.tactical.model.GameMap
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.ComponentCritStatus
import battletech.tactical.unit.ForeignUnit
import battletech.tactical.unit.HeatSource
import battletech.tactical.unit.VisibleUnit
import battletech.tactical.unit.criticalDamageStatus
import battletech.tactical.unit.remainingShots
import battletech.tui.icon.ammoIcon
import battletech.tui.icon.emptyCircleIcon
import battletech.tui.icon.filledCircleIcon
import battletech.tui.icon.infinityIcon
import battletech.tui.screen.BoardRole
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.view.TextCursor
import tenter.view.View

internal class UnitStatusView(
    private val subject: VisibleUnit?,
    private val map: GameMap,
    private val pendingHeat: List<HeatSource> = emptyList(),
) : View {

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)

        when (subject) {
            null -> {
                content.writeLine("No unit selected", TEXT_PRIMARY_STYLE)
                return
            }
            is ForeignUnit -> {
                ForeignUnitPanel.render(content, subject)
                return
            }
            is CombatUnit -> Unit
        }

        val unit = subject

        // UNIT
        with(content) {
            writeLine(UnitLabel.of(unit), ACCENT_STYLE)
            newLine()
        }

        // PILOT
        with(content) {
            writeHeader("PILOT")
            // Canonical 6-box "Hits" track (record sheet Pilot Data): filled = hits taken,
            // empty = remaining boxes. No "health" concept in the rules — hits accumulate
            // upward, each one forcing a Consciousness roll (PilotHits.kt).
            val hitsLabel = "Hits".padEnd(9) + ": "
            content.write(0, hitsLabel, TEXT_PRIMARY_STYLE)
            PilotHitsTrack.draw(
                content,
                column = hitsLabel.length,
                stride = 1,
                hits = unit.pilotHits,
                filledStyle = DANGER_STYLE,
                emptyStyle = TEXT_PRIMARY_STYLE,
            )
            content.newLine()
            writeLine("Gunnery  : ${unit.gunnerySkill}", TEXT_PRIMARY_STYLE)
            writeLine("Piloting : ${unit.pilotingSkill}", TEXT_PRIMARY_STYLE)
            newLine()
        }

        // MOVEMENT
        with(content) {
            writeHeader("MOVEMENT")
            writeLine("Walk : ${unit.walkingMP}    Run : ${unit.runningMP}", TEXT_PRIMARY_STYLE)
            if (unit.jumpMP > 0) writeLine("Jump : ${unit.jumpMP}", TEXT_PRIMARY_STYLE)
            newLine()
        }

        // HEAT
        with(content) {
            writeHeader("HEAT")
            draw(HeatGauges(unit, map, pendingHeat))

            val projection = projectHeat(unit, map, pendingHeat)
            val penalties = HeatPenalties.lines(unit.currentHeat, projection.projected)
            if (penalties.isNotEmpty()) {
                writeLine("Penalties")
                for ((text, fg) in penalties) {
                    writeLine(text, Cell.Style(fg))
                }
            }
            newLine()
        }

        // ARMOR
        with(content) {
            val armor = unit.armor
            val is_ = unit.internalStructure
            writeHeader("ARMOR")
            writeLocation(9, "HD", armor.head, is_.head, ChromeRole.INFO)
            newLine()
            writeLocation(2, "LT", armor.leftTorso, is_.leftTorso, ChromeRole.SUCCESS)
            writeLocation(9, "CT", armor.centerTorso, is_.centerTorso, ChromeRole.ACCENT)
            writeLocation(16, "RT", armor.rightTorso, is_.rightTorso, ChromeRole.SUCCESS)
            newLine()
            write(3, "r:%2d".format(armor.leftTorsoRear), Cell.Style.DEFAULT)
            write(10, "r:%2d".format(armor.centerTorsoRear), Cell.Style.DEFAULT)
            write(17, "r:%2d".format(armor.rightTorsoRear), Cell.Style.DEFAULT)
            newLine()
            writeLocation(0, "LA", armor.leftArm, is_.leftArm, ChromeRole.SUCCESS)
            writeLocation(17, "RA", armor.rightArm, is_.rightArm, ChromeRole.SUCCESS)
            newLine()
            writeLocation(3, "LL", armor.leftLeg, is_.leftLeg, ChromeRole.SUCCESS)
            writeLocation(14, "RL", armor.rightLeg, is_.rightLeg, ChromeRole.SUCCESS)
            newLine()
            newLine()

            writeLine("Critical hit points", TEXT_PRIMARY_STYLE)
            for (status in unit.criticalDamageStatus()) {
                writeCritDots(content, status)
            }
            newLine()

            writeLine("Internal Structure", TEXT_PRIMARY_STYLE)
            writeLocation(9, "HD", is_.head, is_.head, ChromeRole.INFO)
            newLine()
            writeLocation(2, "LT", is_.leftTorso, is_.leftTorso, ChromeRole.SUCCESS)
            writeLocation(9, "CT", is_.centerTorso, is_.centerTorso, ChromeRole.ACCENT)
            writeLocation(16, "RT", is_.rightTorso, is_.rightTorso, ChromeRole.SUCCESS)
            newLine()
            writeLocation(0, "LA", is_.leftArm, is_.leftArm, ChromeRole.SUCCESS)
            writeLocation(17, "RA", is_.rightArm, is_.rightArm, ChromeRole.SUCCESS)
            newLine()
            writeLocation(3, "LL", is_.leftLeg, is_.leftLeg, ChromeRole.SUCCESS)
            writeLocation(14, "RL", is_.rightLeg, is_.rightLeg, ChromeRole.SUCCESS)
            repeat(2) { newLine() }
        }

        // WEAPONS
        with(content) {
            writeHeader("WEAPONS")
            for (weapon in unit.weapons) {
                val style = if (weapon.destroyed) DESTROYED_STYLE else TEXT_PRIMARY_STYLE
                val right = weapon.ammoType?.let { type -> "${unit.remainingShots(type)} ${ammoIcon()}" }
                    ?: infinityIcon()
                writeRow("  ${weapon.name}", right, style)
            }
        }
    }

    /**
     * Destroyed-vs-intact color rule shared by the ARMOR and Internal Structure sections: a
     * zero-[structure] location renders red with a strikethrough (it's gone); an intact location
     * renders in its normal [intactColor].
     */
    private fun TextCursor.writeLocation(padding: Int, label: String, value: Int, structure: Int, intactColor: ChromeRole) {
        val style = if (structure == 0) DESTROYED_STYLE else Cell.Style(intactColor)
        write(padding, "%s:%2d".format(label, value), style)
    }

    /**
     * Renders a "  Label : ●● ○" row where [status].hits (coerced to [status].capacity) dots are
     * drawn filled/red and the remainder of capacity are drawn empty/white, reflecting the real
     * destroyed-slot count against the rules cap. Below the dot row, renders one indented red
     * line per entry in [status].penalties — both values come straight from
     * [battletech.tactical.unit.criticalDamageStatus]; the only thing decided here is the
     * column label for [status].component.
     */
    private fun writeCritDots(content: TextCursor, status: ComponentCritStatus) {
        val label = MechLabels.component(status.component)
        val capacity = status.capacity
        val destroyedCount = status.hits.coerceIn(0, capacity)
        val label6 = label.padEnd(7)
        content.write(2, "$label6: ", TEXT_PRIMARY_STYLE)
        val dotsStart = 2 + "$label6: ".length
        var col = dotsStart
        repeat(destroyedCount) {
            content.write(col, filledCircleIcon(), DANGER_STYLE)
            col += 2
        }
        repeat(capacity - destroyedCount) {
            content.write(col, emptyCircleIcon(), TEXT_PRIMARY_STYLE)
            col += 2
        }
        content.newLine()
        for (penalty in status.penalties) {
            content.write(4, penalty, DANGER_STYLE)
            content.newLine()
        }
    }

    internal companion object {
        internal const val TITLE: String = "UNIT STATUS"

        private val TEXT_PRIMARY_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)
        private val ACCENT_STYLE = Cell.Style(ChromeRole.ACCENT)
        private val DANGER_STYLE = Cell.Style(ChromeRole.DANGER)
        private val DESTROYED_STYLE = Cell.Style(BoardRole.DESTROYED, strikethrough = true)
    }
}
