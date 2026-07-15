package battletech.tui.view

import battletech.tactical.heat.HeatScale
import battletech.tactical.heat.projectHeat
import battletech.tactical.query.ForeignUnit
import battletech.tactical.query.OwnUnit
import battletech.tactical.query.VisibleUnit
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.ComponentCritStatus
import battletech.tactical.unit.CriticalComponent
import battletech.tactical.unit.HeatSource
import battletech.tactical.unit.availableAmmoBins
import battletech.tactical.unit.criticalDamageStatus
import battletech.tui.game.PanelId
import battletech.tui.hex.ammoIcon
import battletech.tui.hex.emptyCircleIcon
import battletech.tui.hex.filledCircleIcon
import battletech.tui.hex.infinityIcon
import battletech.tui.screen.Color
import battletech.tui.screen.ContentWriter
import battletech.tui.screen.ScreenBuffer

public class UnitStatusView(
    private val subject: VisibleUnit?,
    private val pendingHeat: List<HeatSource> = emptyList(),
) : View {

    public constructor(
        unit: CombatUnit?,
        pendingHeat: List<HeatSource> = emptyList(),
    ) : this(unit?.let { OwnUnit(it) }, pendingHeat)

    public companion object {
        public val INDEX: Int = PanelId.UNIT_STATUS.index
        public const val TITLE: String = "UNIT STATUS"
    }

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        // One blank row for pixel parity with the decorator's y+1 inner-content start
        val content = ContentWriter(buffer, x, y + 1, width)

        when (subject) {
            null -> {
                content.writeln("No unit selected", Color.WHITE)
                return
            }
            is ForeignUnit -> {
                ForeignUnitPanel.render(content, subject)
                return
            }
            is OwnUnit -> Unit
        }

        val unit = subject.unit

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

            val projection = projectHeat(unit, pendingHeat)

            for (source in projection.committed) {
                writeln("  ${source.label} +${source.amount}")
            }
            for (source in projection.pending) {
                writeln("  ${source.label} +${source.amount}", Color.DRAFT)
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
                    writeln(text, fg)
                }
            }
            newLine()
        }

        // ARMOR
        with(content) {
            val armor = unit.armor
            val is_ = unit.internalStructure
            writeHeader("ARMOR")
            writeStr(9, "HD:%2d".format(armor.head), if (is_.head == 0) Color.RED else Color.CYAN)
            newLine()
            writeStr(2, "LT:%2d".format(armor.leftTorso), if (is_.leftTorso == 0) Color.RED else Color.GREEN)
            writeStr(9, "CT:%2d".format(armor.centerTorso), if (is_.centerTorso == 0) Color.RED else Color.BRIGHT_YELLOW)
            writeStr(16, "RT:%2d".format(armor.rightTorso), if (is_.rightTorso == 0) Color.RED else Color.GREEN)
            newLine()
            writeStr(3, "r:%2d".format(armor.leftTorsoRear), Color.DEFAULT)
            writeStr(10, "r:%2d".format(armor.centerTorsoRear), Color.DEFAULT)
            writeStr(17, "r:%2d".format(armor.rightTorsoRear), Color.DEFAULT)
            newLine()
            writeStr(0, "LA:%2d".format(armor.leftArm), if (is_.leftArm == 0) Color.RED else Color.GREEN)
            writeStr(17, "RA:%2d".format(armor.rightArm), if (is_.rightArm == 0) Color.RED else Color.GREEN)
            newLine()
            writeStr(3, "LL:%2d".format(armor.leftLeg), if (is_.leftLeg == 0) Color.RED else Color.GREEN)
            writeStr(14, "RL:%2d".format(armor.rightLeg), if (is_.rightLeg == 0) Color.RED else Color.GREEN)
            newLine()
            newLine()

            writeln("Critical hit points", Color.WHITE)
            for (status in unit.criticalDamageStatus()) {
                writeCritDots(content, status)
            }
            newLine()

            writeln("Internal Structure", Color.WHITE)
            writeStr(9, "HD:%2d".format(is_.head), if (is_.head == 0) Color.RED else Color.CYAN)
            newLine()
            writeStr(2, "LT:%2d".format(is_.leftTorso), if (is_.leftTorso == 0) Color.RED else Color.GREEN)
            writeStr(9, "CT:%2d".format(is_.centerTorso), if (is_.centerTorso == 0) Color.RED else Color.BRIGHT_YELLOW)
            writeStr(16, "RT:%2d".format(is_.rightTorso), if (is_.rightTorso == 0) Color.RED else Color.GREEN)
            newLine()
            writeStr(0, "LA:%2d".format(is_.leftArm), if (is_.leftArm == 0) Color.RED else Color.GREEN)
            writeStr(17, "RA:%2d".format(is_.rightArm), if (is_.rightArm == 0) Color.RED else Color.GREEN)
            newLine()
            writeStr(3, "LL:%2d".format(is_.leftLeg), if (is_.leftLeg == 0) Color.RED else Color.GREEN)
            writeStr(14, "RL:%2d".format(is_.rightLeg), if (is_.rightLeg == 0) Color.RED else Color.GREEN)
            repeat(2) { newLine() }
        }

        // WEAPONS
        with(content) {
            writeHeader("WEAPONS")
            for (weapon in unit.weapons) {
                val color = if (weapon.destroyed) Color.RED else Color.WHITE
                val right = weapon.ammoType?.let { type ->
                    // Only count available ammo (bins in locations with IS > 0).
                    val remaining = unit.availableAmmoBins()
                        .filter { it.third.type == type }
                        .sumOf { it.third.shots }
                    "$remaining ${ammoIcon()}"
                } ?: infinityIcon()
                writeRow("  ${weapon.name}", right, color)
            }
        }
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
        content.writeStr(2, "$label6: ", Color.WHITE)
        val dotsStart = 2 + "$label6: ".length
        var col = dotsStart
        repeat(destroyedCount) {
            content.writeStr(col, filledCircleIcon(), Color.RED)
            col += 2
        }
        repeat(capacity - destroyedCount) {
            content.writeStr(col, emptyCircleIcon(), Color.WHITE)
            col += 2
        }
        content.newLine()
        for (penalty in status.penalties) {
            content.writeStr(4, penalty, Color.RED)
            content.newLine()
        }
    }

    private fun componentLabel(component: CriticalComponent): String = when (component) {
        CriticalComponent.ENGINE -> "Engine"
        CriticalComponent.GYRO -> "Gyro"
        CriticalComponent.SENSOR -> "Sensor"
        CriticalComponent.LIFE_SUPPORT -> "Support"
    }
}
