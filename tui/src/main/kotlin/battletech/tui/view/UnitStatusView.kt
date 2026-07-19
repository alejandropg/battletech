package battletech.tui.view

import battletech.tactical.heat.HeatScale
import battletech.tactical.heat.projectHeat
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.ComponentCritStatus
import battletech.tactical.unit.CriticalComponent
import battletech.tactical.unit.ForeignUnit
import battletech.tactical.unit.HeatSource
import battletech.tactical.unit.PILOT_DEATH_THRESHOLD
import battletech.tactical.unit.VisibleUnit
import battletech.tactical.unit.availableAmmoBins
import battletech.tactical.unit.criticalDamageStatus
import battletech.tui.game.PanelId
import battletech.tui.hex.ammoIcon
import battletech.tui.hex.destroyedIcon
import battletech.tui.hex.emptyCircleIcon
import battletech.tui.hex.filledCircleIcon
import battletech.tui.hex.infinityIcon
import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.ContentWriter
import battletech.tui.screen.ScreenBuffer

public class UnitStatusView(
    private val subject: VisibleUnit?,
    private val pendingHeat: List<HeatSource> = emptyList(),
) : View {

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        // One blank row for pixel parity with the decorator's y+1 inner-content start
        val content = ContentWriter(buffer, x, y + 1, width)

        when (subject) {
            null -> {
                content.writeln("No unit selected", WHITE_STYLE)
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
            writeln(UnitLabel.of(unit), BRIGHT_YELLOW_STYLE)
            newLine()
        }

        // PILOT
        with(content) {
            writeHeader("PILOT")
            // Canonical 6-box "Hits" track (record sheet Pilot Data): filled = hits taken,
            // empty = remaining boxes. No "health" concept in the rules — hits accumulate
            // upward, each one forcing a Consciousness roll (PilotHits.kt).
            val hitsLabel = "Hits".padEnd(9) + ": "
            val hits = unit.pilotHits.coerceIn(0, PILOT_DEATH_THRESHOLD)
            content.writeStr(0, hitsLabel, WHITE_STYLE)
            var hitCol = hitsLabel.length
            for (i in 0 until hits) {
                // The 6th hit kills the pilot outright (PILOT_DEATH_THRESHOLD) — mark that
                // final box with a skull instead of a plain filled dot.
                val icon = if (i == PILOT_DEATH_THRESHOLD - 1) destroyedIcon() else filledCircleIcon()
                content.writeStr(hitCol, icon, RED_STYLE)
                hitCol += 1
            }
            repeat(PILOT_DEATH_THRESHOLD - hits) { content.writeStr(hitCol, emptyCircleIcon(), WHITE_STYLE); hitCol += 1 }
            content.newLine()
            writeln("Gunnery  : ${unit.gunnerySkill}", WHITE_STYLE)
            writeln("Piloting : ${unit.pilotingSkill}", WHITE_STYLE)
            newLine()
        }

        // MOVEMENT
        with(content) {
            writeHeader("MOVEMENT")
            writeln("Walk : ${unit.walkingMP}    Run : ${unit.runningMP}", WHITE_STYLE)
            if (unit.jumpMP > 0) writeln("Jump : ${unit.jumpMP}", WHITE_STYLE)
            newLine()
        }

        // HEAT
        with(content) {
            writeHeader("HEAT")
            writeln("Current")
            val heatBar = HeatBarWidget(barWidth = 20, maxValue = 30)
            content.cy = heatBar.draw(buffer, content.x, content.cy, unit.currentHeat)

            val projection = projectHeat(unit, pendingHeat)

            for (source in projection.committed) {
                writeln("  ${source.label} +${source.amount}")
            }
            for (source in projection.pending) {
                writeln("  ${source.label} +${source.amount}", DRAFT_STYLE)
            }

            val sink = unit.heatSink
            val sinkSuffix =
                if (sink.type.sinkRatio == 1) "${sink.type.name} ${projection.dissipation}"
                else "${sink.type.name} ${sink.units}(${projection.dissipation})"
            content.cy = HeatBarWidget(barWidth = 10, maxValue = projection.dissipation, suffix = sinkSuffix)
                .draw(buffer, content.x, content.cy, projection.dissipated)

            writeln("Projected")
            content.cy = heatBar.draw(buffer, content.x, content.cy, projection.projected)

            val penalties = penaltyLines(unit.currentHeat, projection.projected)
            if (penalties.isNotEmpty()) {
                writeln("Penalties")
                for ((text, fg) in penalties) {
                    writeln(text, Cell.Style(fg))
                }
            }
            newLine()
        }

        // ARMOR
        with(content) {
            val armor = unit.armor
            val is_ = unit.internalStructure
            writeHeader("ARMOR")
            writeLocation(9, "HD", armor.head, is_.head, Color.CYAN)
            newLine()
            writeLocation(2, "LT", armor.leftTorso, is_.leftTorso, Color.GREEN)
            writeLocation(9, "CT", armor.centerTorso, is_.centerTorso, Color.BRIGHT_YELLOW)
            writeLocation(16, "RT", armor.rightTorso, is_.rightTorso, Color.GREEN)
            newLine()
            writeStr(3, "r:%2d".format(armor.leftTorsoRear), Cell.Style.DEFAULT)
            writeStr(10, "r:%2d".format(armor.centerTorsoRear), Cell.Style.DEFAULT)
            writeStr(17, "r:%2d".format(armor.rightTorsoRear), Cell.Style.DEFAULT)
            newLine()
            writeLocation(0, "LA", armor.leftArm, is_.leftArm, Color.GREEN)
            writeLocation(17, "RA", armor.rightArm, is_.rightArm, Color.GREEN)
            newLine()
            writeLocation(3, "LL", armor.leftLeg, is_.leftLeg, Color.GREEN)
            writeLocation(14, "RL", armor.rightLeg, is_.rightLeg, Color.GREEN)
            newLine()
            newLine()

            writeln("Critical hit points", WHITE_STYLE)
            for (status in unit.criticalDamageStatus()) {
                writeCritDots(content, status)
            }
            newLine()

            writeln("Internal Structure", WHITE_STYLE)
            writeLocation(9, "HD", is_.head, is_.head, Color.CYAN)
            newLine()
            writeLocation(2, "LT", is_.leftTorso, is_.leftTorso, Color.GREEN)
            writeLocation(9, "CT", is_.centerTorso, is_.centerTorso, Color.BRIGHT_YELLOW)
            writeLocation(16, "RT", is_.rightTorso, is_.rightTorso, Color.GREEN)
            newLine()
            writeLocation(0, "LA", is_.leftArm, is_.leftArm, Color.GREEN)
            writeLocation(17, "RA", is_.rightArm, is_.rightArm, Color.GREEN)
            newLine()
            writeLocation(3, "LL", is_.leftLeg, is_.leftLeg, Color.GREEN)
            writeLocation(14, "RL", is_.rightLeg, is_.rightLeg, Color.GREEN)
            repeat(2) { newLine() }
        }

        // WEAPONS
        with(content) {
            writeHeader("WEAPONS")
            for (weapon in unit.weapons) {
                val style = if (weapon.destroyed) DESTROYED_STYLE else WHITE_STYLE
                val right = weapon.ammoType?.let { type ->
                    // Only count available ammo (bins in locations with IS > 0).
                    val remaining = unit.availableAmmoBins()
                        .filter { it.third.type == type }
                        .sumOf { it.third.shots }
                    "$remaining ${ammoIcon()}"
                } ?: infinityIcon()
                writeRow("  ${weapon.name}", right, style)
            }
        }
    }

    /**
     * Destroyed-vs-intact color rule shared by the ARMOR and Internal Structure sections: a
     * zero-[structure] location renders red with a strikethrough (it's gone); an intact location
     * renders in its normal [intactColor].
     */
    private fun ContentWriter.writeLocation(padding: Int, label: String, value: Int, structure: Int, intactColor: Color) {
        val style = if (structure == 0) DESTROYED_STYLE else Cell.Style(intactColor)
        writeStr(padding, "%s:%2d".format(label, value), style)
    }

    /**
     * Single-worst-value-per-category heat penalty lines for [current] (applied baseline) vs
     * [projected] heat. A line is solid ([Color.DEFAULT]) when the worst value is already in
     * force at [current]; otherwise it is projection-only ([Color.DRAFT]).
     */
    internal fun penaltyLines(current: Int, projected: Int): List<Pair<String, Color>> {
        val lines = mutableListOf<Pair<String, Color>>()

        val mp = maxOf(HeatScale.movementPenalty(current), HeatScale.movementPenalty(projected))
        if (mp > 0) {
            val applied = HeatScale.movementPenalty(current) == mp
            lines += "-$mp MP" to (if (applied) Color.DEFAULT else Color.DRAFT)
        }

        val th = maxOf(HeatScale.toHitPenalty(current), HeatScale.toHitPenalty(projected))
        if (th > 0) {
            val applied = HeatScale.toHitPenalty(current) == th
            lines += "+$th To-Hit" to (if (applied) Color.DEFAULT else Color.DRAFT)
        }

        val currentAutoShutdown = HeatScale.isAutoShutdown(current)
        val projectedAutoShutdown = HeatScale.isAutoShutdown(projected)
        val currentShutdownTarget = HeatScale.shutdownAvoidTarget(current)
        val projectedShutdownTarget = HeatScale.shutdownAvoidTarget(projected)
        if (currentAutoShutdown || projectedAutoShutdown) {
            lines += "Shutdown AUTO" to (if (currentAutoShutdown) Color.DEFAULT else Color.DRAFT)
        } else {
            val target = maxOfNullable(currentShutdownTarget, projectedShutdownTarget)
            if (target != null) {
                val applied = currentShutdownTarget == target
                lines += "Shutdown $target+" to (if (applied) Color.DEFAULT else Color.DRAFT)
            }
        }

        val currentAmmoTarget = HeatScale.ammoExplosionAvoidTarget(current)
        val projectedAmmoTarget = HeatScale.ammoExplosionAvoidTarget(projected)
        val ammoTarget = maxOfNullable(currentAmmoTarget, projectedAmmoTarget)
        if (ammoTarget != null) {
            val applied = currentAmmoTarget == ammoTarget
            lines += "Ammo $ammoTarget+" to (if (applied) Color.DEFAULT else Color.DRAFT)
        }

        return lines
    }

    private fun maxOfNullable(a: Int?, b: Int?): Int? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }

    /**
     * Renders a "  Label : ●● ○" row where [status].hits (coerced to [status].capacity) dots are
     * drawn filled/red and the remainder of capacity are drawn empty/white, reflecting the real
     * destroyed-slot count against the rules cap. Below the dot row, renders one indented red
     * line per entry in [status].penalties — both values come straight from
     * [battletech.tactical.unit.criticalDamageStatus]; the only thing decided here is the
     * column label for [status].component.
     */
    private fun writeCritDots(content: ContentWriter, status: ComponentCritStatus) {
        val label = componentLabel(status.component)
        val capacity = status.capacity
        val destroyedCount = status.hits.coerceIn(0, capacity)
        val label6 = label.padEnd(7)
        content.writeStr(2, "$label6: ", WHITE_STYLE)
        val dotsStart = 2 + "$label6: ".length
        var col = dotsStart
        repeat(destroyedCount) {
            content.writeStr(col, filledCircleIcon(), RED_STYLE)
            col += 2
        }
        repeat(capacity - destroyedCount) {
            content.writeStr(col, emptyCircleIcon(), WHITE_STYLE)
            col += 2
        }
        content.newLine()
        for (penalty in status.penalties) {
            content.writeStr(4, penalty, RED_STYLE)
            content.newLine()
        }
    }

    private fun componentLabel(component: CriticalComponent): String = when (component) {
        CriticalComponent.ENGINE -> "Engine"
        CriticalComponent.GYRO -> "Gyro"
        CriticalComponent.SENSOR -> "Sensor"
        CriticalComponent.LIFE_SUPPORT -> "Support"
    }

    internal companion object {
        internal val INDEX: Int = PanelId.UNIT_STATUS.index
        internal const val TITLE: String = "UNIT STATUS"

        private val WHITE_STYLE = Cell.Style(Color.WHITE)
        private val BRIGHT_YELLOW_STYLE = Cell.Style(Color.BRIGHT_YELLOW)
        private val RED_STYLE = Cell.Style(Color.RED)
        private val DRAFT_STYLE = Cell.Style(Color.DRAFT)
        private val DESTROYED_STYLE = Cell.Style(Color.RED, strikethrough = true)
    }
}
