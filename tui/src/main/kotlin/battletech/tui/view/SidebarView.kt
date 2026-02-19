package battletech.tui.view

import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import battletech.tactical.action.Unit

public class SidebarView(
    private val unit: Unit?,
) : View {

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        buffer.drawBox(x, y, width, height, "UNIT STATUS")

        val cx = x + 2
        val cy = y + 2

        if (unit == null) {
            buffer.writeString(cx, cy, "No unit selected", Color.WHITE)
            return
        }

        buffer.writeString(cx, cy, unit.name, Color.WHITE)
        buffer.writeString(cx, cy + 1, "Pilot: ${unit.gunnerySkill} / ${unit.pilotingSkill}", Color.WHITE)
        buffer.writeString(cx, cy + 2, "Heat: ${unit.currentHeat} / ${unit.heatSinkCapacity}", Color.WHITE)

        if (unit.weapons.isNotEmpty()) {
            buffer.writeString(cx, cy + 4, "Weapons:", Color.WHITE)
            for ((i, weapon) in unit.weapons.withIndex()) {
                val ammoStr = weapon.ammo?.let { " [$it]" } ?: ""
                buffer.writeString(cx, cy + 5 + i, "${weapon.name}$ammoStr", Color.WHITE)
            }
        }
    }
}
