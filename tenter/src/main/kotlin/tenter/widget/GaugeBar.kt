package tenter.widget

import tenter.screen.Cell
import tenter.screen.UiRole
import tenter.view.TextCursor

/** A proportional `[███░░░]value` bar, colored by how full it is (info/warning/danger tiers). */
public class GaugeBar(
    private val barWidth: Int,
    private val maxValue: Int,
    private val suffix: String = maxValue.toString(),
) {
    /** Bar on the current row, right-aligned value on the next; advances two rows. */
    public fun draw(content: TextCursor, x: Int, value: Int) {
        // maxValue <= 0 guard prevents division by zero. When maxValue == 0 the bar is all empty.
        // The bar is proportionally scaled: a fixed barWidth-cell bar spans the whole 0–maxValue
        // range (each block ≈ maxValue/barWidth units). The max sits inline after "]".
        val filled = if (maxValue <= 0) 0 else (value * barWidth / maxValue).coerceIn(0, barWidth)
        val bar = "█".repeat(filled) + "░".repeat(barWidth - filled)
        val color = colorFor(value)
        content.write(x, "[$bar]$suffix", Cell.Style(color))
        content.newLine()
        val valueStr = value.toString()
        // First bar cell is at x + 1 (the "[" prefix). Anchor on the last filled cell,
        // or the first cell when empty, then right-align the number to it.
        val anchorCol = x + filled.coerceAtLeast(1)
        content.write(anchorCol - valueStr.length + 1, valueStr, Cell.Style(color))
        content.newLine()
    }

    private fun colorFor(value: Int): UiRole = when {
        value >= maxValue * 0.7 -> UiRole.DANGER
        value >= maxValue * 0.3 -> UiRole.WARNING
        else -> UiRole.INFO
    }
}
