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
import battletech.tui.hex.ammoIcon
import battletech.tui.hex.emptyCircleIcon
import battletech.tui.hex.filledCircleIcon
import battletech.tui.hex.infinityIcon
import battletech.tui.hex.pilotDeadIcon
import battletech.tui.screen.BoardRole
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.UiRole
import tenter.view.TextCursor
import tenter.view.GaugeBar
import tenter.view.View

internal class UnitStatusView(
    private val subject: VisibleUnit?,
    private val pendingHeat: List<HeatSource> = emptyList(),
) : View {

    override fun render(canvas: Canvas) {
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
            val hits = unit.pilotHits.coerceIn(0, PILOT_DEATH_THRESHOLD)
            content.write(0, hitsLabel, TEXT_PRIMARY_STYLE)
            var hitCol = hitsLabel.length
            for (i in 0 until hits) {
                // The 6th hit kills the pilot outright (PILOT_DEATH_THRESHOLD) — mark that
                // final box with a skull instead of a plain filled dot.
                val icon = if (i == PILOT_DEATH_THRESHOLD - 1) pilotDeadIcon() else filledCircleIcon()
                content.write(hitCol, icon, DANGER_STYLE)
                hitCol += 1
            }
            repeat(PILOT_DEATH_THRESHOLD - hits) { content.write(hitCol, emptyCircleIcon(), TEXT_PRIMARY_STYLE); hitCol += 1 }
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
            writeLine("Current")
            val heatBar = GaugeBar(barWidth = 20, maxValue = 30)
            heatBar.draw(content, 0, unit.currentHeat)

            val projection = projectHeat(unit, pendingHeat)

            for (source in projection.committed) {
                writeLine("  ${source.label} +${source.amount}")
            }
            for (source in projection.pending) {
                writeLine("  ${source.label} +${source.amount}", DRAFT_STYLE)
            }

            val sink = unit.heatSink
            val sinkSuffix =
                if (sink.type.sinkRatio == 1) "${sink.type.name} ${projection.dissipation}"
                else "${sink.type.name} ${sink.units}(${projection.dissipation})"
            GaugeBar(barWidth = 10, maxValue = projection.dissipation, suffix = sinkSuffix)
                .draw(content, 0, projection.dissipated)

            writeLine("Projected")
            heatBar.draw(content, 0, projection.projected)

            val penalties = penaltyLines(unit.currentHeat, projection.projected)
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
            writeLocation(9, "HD", armor.head, is_.head, UiRole.INFO)
            newLine()
            writeLocation(2, "LT", armor.leftTorso, is_.leftTorso, UiRole.SUCCESS)
            writeLocation(9, "CT", armor.centerTorso, is_.centerTorso, UiRole.ACCENT)
            writeLocation(16, "RT", armor.rightTorso, is_.rightTorso, UiRole.SUCCESS)
            newLine()
            write(3, "r:%2d".format(armor.leftTorsoRear), Cell.Style.DEFAULT)
            write(10, "r:%2d".format(armor.centerTorsoRear), Cell.Style.DEFAULT)
            write(17, "r:%2d".format(armor.rightTorsoRear), Cell.Style.DEFAULT)
            newLine()
            writeLocation(0, "LA", armor.leftArm, is_.leftArm, UiRole.SUCCESS)
            writeLocation(17, "RA", armor.rightArm, is_.rightArm, UiRole.SUCCESS)
            newLine()
            writeLocation(3, "LL", armor.leftLeg, is_.leftLeg, UiRole.SUCCESS)
            writeLocation(14, "RL", armor.rightLeg, is_.rightLeg, UiRole.SUCCESS)
            newLine()
            newLine()

            writeLine("Critical hit points", TEXT_PRIMARY_STYLE)
            for (status in unit.criticalDamageStatus()) {
                writeCritDots(content, status)
            }
            newLine()

            writeLine("Internal Structure", TEXT_PRIMARY_STYLE)
            writeLocation(9, "HD", is_.head, is_.head, UiRole.INFO)
            newLine()
            writeLocation(2, "LT", is_.leftTorso, is_.leftTorso, UiRole.SUCCESS)
            writeLocation(9, "CT", is_.centerTorso, is_.centerTorso, UiRole.ACCENT)
            writeLocation(16, "RT", is_.rightTorso, is_.rightTorso, UiRole.SUCCESS)
            newLine()
            writeLocation(0, "LA", is_.leftArm, is_.leftArm, UiRole.SUCCESS)
            writeLocation(17, "RA", is_.rightArm, is_.rightArm, UiRole.SUCCESS)
            newLine()
            writeLocation(3, "LL", is_.leftLeg, is_.leftLeg, UiRole.SUCCESS)
            writeLocation(14, "RL", is_.rightLeg, is_.rightLeg, UiRole.SUCCESS)
            repeat(2) { newLine() }
        }

        // WEAPONS
        with(content) {
            writeHeader("WEAPONS")
            for (weapon in unit.weapons) {
                val style = if (weapon.destroyed) DESTROYED_STYLE else TEXT_PRIMARY_STYLE
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
    private fun TextCursor.writeLocation(padding: Int, label: String, value: Int, structure: Int, intactColor: UiRole) {
        val style = if (structure == 0) DESTROYED_STYLE else Cell.Style(intactColor)
        write(padding, "%s:%2d".format(label, value), style)
    }

    /**
     * Single-worst-value-per-category heat penalty lines for [current] (applied baseline) vs
     * [projected] heat. A line is solid ([UiRole.DEFAULT]) when the worst value is already in
     * force at [current]; otherwise it is projection-only ([UiRole.DRAFT]).
     */
    internal fun penaltyLines(current: Int, projected: Int): List<Pair<String, UiRole>> {
        val lines = mutableListOf<Pair<String, UiRole>>()

        val mp = maxOf(HeatScale.movementPenalty(current), HeatScale.movementPenalty(projected))
        if (mp > 0) {
            val applied = HeatScale.movementPenalty(current) == mp
            lines += "-$mp MP" to (if (applied) UiRole.DEFAULT else UiRole.DRAFT)
        }

        val th = maxOf(HeatScale.toHitPenalty(current), HeatScale.toHitPenalty(projected))
        if (th > 0) {
            val applied = HeatScale.toHitPenalty(current) == th
            lines += "+$th To-Hit" to (if (applied) UiRole.DEFAULT else UiRole.DRAFT)
        }

        val currentAutoShutdown = HeatScale.isAutoShutdown(current)
        val projectedAutoShutdown = HeatScale.isAutoShutdown(projected)
        val currentShutdownTarget = HeatScale.shutdownAvoidTarget(current)
        val projectedShutdownTarget = HeatScale.shutdownAvoidTarget(projected)
        if (currentAutoShutdown || projectedAutoShutdown) {
            lines += "Shutdown AUTO" to (if (currentAutoShutdown) UiRole.DEFAULT else UiRole.DRAFT)
        } else {
            val target = maxOfNullable(currentShutdownTarget, projectedShutdownTarget)
            if (target != null) {
                val applied = currentShutdownTarget == target
                lines += "Shutdown $target+" to (if (applied) UiRole.DEFAULT else UiRole.DRAFT)
            }
        }

        val currentAmmoTarget = HeatScale.ammoExplosionAvoidTarget(current)
        val projectedAmmoTarget = HeatScale.ammoExplosionAvoidTarget(projected)
        val ammoTarget = maxOfNullable(currentAmmoTarget, projectedAmmoTarget)
        if (ammoTarget != null) {
            val applied = currentAmmoTarget == ammoTarget
            lines += "Ammo $ammoTarget+" to (if (applied) UiRole.DEFAULT else UiRole.DRAFT)
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
    private fun writeCritDots(content: TextCursor, status: ComponentCritStatus) {
        val label = componentLabel(status.component)
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

    private fun componentLabel(component: CriticalComponent): String = when (component) {
        CriticalComponent.ENGINE -> "Engine"
        CriticalComponent.GYRO -> "Gyro"
        CriticalComponent.SENSOR -> "Sensor"
        CriticalComponent.LIFE_SUPPORT -> "Support"
    }

    internal companion object {
        internal const val TITLE: String = "UNIT STATUS"

        private val TEXT_PRIMARY_STYLE = Cell.Style(UiRole.TEXT_PRIMARY)
        private val ACCENT_STYLE = Cell.Style(UiRole.ACCENT)
        private val DANGER_STYLE = Cell.Style(UiRole.DANGER)
        private val DRAFT_STYLE = Cell.Style(UiRole.DRAFT)
        private val DESTROYED_STYLE = Cell.Style(BoardRole.DESTROYED, strikethrough = true)
    }
}
