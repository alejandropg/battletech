package tenter.widget

import tenter.screen.Cell
import tenter.palette.ChromeRole
import tenter.palette.ColorRole
import tenter.text.CellWidth
import tenter.view.TextCursor

/** A proportional `[███░░░]value` bar with caller-selectable styling for its current value. */
public class Gauge(
    private val barWidth: Int,
    private val maxValue: Int,
    private val suffix: String = maxValue.toString(),
    private val colorFor: (value: Int) -> ColorRole = { value -> defaultColor(value, maxValue) },
) {
    init {
        require(barWidth >= 0) { "barWidth must not be negative, was $barWidth" }
        require(maxValue >= 0) { "maxValue must not be negative, was $maxValue" }
    }

    /** Bar on the current row, right-aligned value on the next; advances two rows. */
    public fun draw(content: TextCursor, x: Int, value: Int) {
        // maxValue <= 0 guard prevents division by zero. When maxValue == 0 the bar is all empty.
        // The bar is proportionally scaled: a fixed barWidth-cell bar spans the whole 0–maxValue
        // range (each block ≈ maxValue/barWidth units). The max sits inline after "]".
        val filled = if (maxValue <= 0) 0 else {
            (value.toLong() * barWidth / maxValue).coerceIn(0L, barWidth.toLong()).toInt()
        }
        val bar = "█".repeat(filled) + "░".repeat(barWidth - filled)
        val color = colorFor(value)
        content.write(x, "[$bar]$suffix", Cell.Style(color))
        content.newLine()
        val valueStr = value.toString()
        // First bar cell is at x + 1 (the "[" prefix). Anchor on the last filled cell,
        // or the first cell when empty, then right-align the number to it.
        val anchorCol = x + filled.coerceAtLeast(1)
        content.write(anchorCol - CellWidth.of(valueStr) + 1, valueStr, Cell.Style(color))
        content.newLine()
    }

    private companion object {
        fun defaultColor(value: Int, maxValue: Int): ChromeRole = when {
            value.toDouble() >= maxValue * 0.7 -> ChromeRole.DANGER
            value.toDouble() >= maxValue * 0.3 -> ChromeRole.WARNING
            else -> ChromeRole.INFO
        }
    }
}
