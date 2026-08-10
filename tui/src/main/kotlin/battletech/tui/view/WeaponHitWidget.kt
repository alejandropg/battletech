package battletech.tui.view

import battletech.tui.screen.Cell
import battletech.tui.screen.CellWidth
import battletech.tui.screen.Color

/** Renders one weapon's to-hit summary: "<left> … ▨TN %" then one indented line per modifier. */
internal object WeaponHitWidget {
    fun draw(
        content: ContentWriter,
        left: String,
        targetDiceRoll: Int,
        successChance: Int,
        modifiers: List<String>,
        color: Color,
    ) {
        val right = hitChanceLabel(targetDiceRoll, successChance)
        val fill = (content.width - left.length - CellWidth.of(right)).coerceAtLeast(1)
        content.writeln("$left${" ".repeat(fill)}$right", Cell.Style(color))
        modifiers.forEach { content.writeln("    $it", Cell.Style(color)) }
    }
}
