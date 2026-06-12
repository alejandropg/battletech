package battletech.tui.view

import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer

public class HeatBarWidget(
    private val barWidth: Int,
    private val maxValue: Int,
    private val suffix: String = maxValue.toString(),
) {
    /** Bar on `row`, right-aligned value on `row + 1`. Returns row + 2. */
    public fun draw(buffer: ScreenBuffer, x: Int, y: Int, value: Int): Int {
        // maxValue <= 0 guard prevents division by zero. When maxValue == 0 the bar is all empty.
        // The bar is proportionally scaled: a fixed barWidth-cell bar spans the whole 0–maxValue
        // range (each block ≈ maxValue/barWidth units). The max sits inline after "]".
        val filled = if (maxValue <= 0) 0 else (value * barWidth / maxValue).coerceIn(0, barWidth)
        val bar = "█".repeat(filled) + "░".repeat(barWidth - filled)
        val color = colorFor(value)
        buffer.writeString(x, y, "[$bar]$suffix", color)
        val valueStr = value.toString()
        // First bar cell is at x + 1 (the "[" prefix). Anchor on the last filled cell,
        // or the first cell when empty, then right-align the number to it.
        val anchorCol = x + filled.coerceAtLeast(1)
        buffer.writeString(anchorCol - valueStr.length + 1, y + 1, valueStr, color)
        return y + 2
    }

    // Color thresholds: red at 70 %+ of max, yellow at 30 %+, green below.
    private fun colorFor(value: Int): Color = when {
        value >= maxValue * 0.7 -> Color.RED
        value >= maxValue * 0.3 -> Color.YELLOW
        else -> Color.GREEN
    }
}
