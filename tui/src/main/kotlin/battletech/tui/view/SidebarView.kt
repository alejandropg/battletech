package battletech.tui.view

import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import battletech.tactical.action.Unit

public class SidebarView(
    private val unit: Unit?,
) : View {

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        if (unit == null) {
            buffer.writeString(x, y, "No unit selected", Color.WHITE)
            return
        }

        buffer.writeString(x, y, unit.name, Color.WHITE)
        buffer.writeString(x, y + 1, "Pilot: ${unit.gunnerySkill} / ${unit.pilotingSkill}", Color.WHITE)
        buffer.writeString(x, y + 2, "Heat: ${unit.currentHeat} / ${unit.heatSinkCapacity}", Color.WHITE)

        if (unit.weapons.isNotEmpty()) {
            buffer.writeString(x, y + 4, "Weapons:", Color.WHITE)
            for ((i, weapon) in unit.weapons.withIndex()) {
                val ammoStr = weapon.ammo?.let { " [$it]" } ?: ""
                buffer.writeString(x, y + 5 + i, "${weapon.name}$ammoStr", Color.WHITE)
            }
        }
    }
}
