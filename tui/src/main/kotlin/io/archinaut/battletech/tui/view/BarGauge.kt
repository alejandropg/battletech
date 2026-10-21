package io.archinaut.battletech.tui.view

import io.archinaut.tenter.palette.ChromeRole
import io.archinaut.tenter.palette.ColorRole
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.text.CellWidth
import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.tenter.widget.RowWriter

/**
 * A proportional `[███░░░]suffix` bar for [value] out of [maxValue], with [value] right-aligned
 * under the bar's last filled cell on the next row. [colorFor] picks both rows' color for the
 * current value.
 */
internal class BarGauge(
    private val barWidth: Int,
    private val maxValue: Int,
    private val value: Int,
    private val suffix: String = maxValue.toString(),
    private val colorFor: (value: Int) -> ColorRole = { defaultColor(it, maxValue) },
) : MeasurableWidget {

    init {
        require(barWidth >= 0) { "barWidth must not be negative, was $barWidth" }
        require(maxValue >= 0) { "maxValue must not be negative, was $maxValue" }
    }

    override fun write(writer: RowWriter) {
        // A fixed barWidth-cell bar spans the whole 0..maxValue range; maxValue == 0 is all empty.
        val filled = if (maxValue <= 0) 0 else {
            (value.toLong() * barWidth / maxValue).coerceIn(0L, barWidth.toLong()).toInt()
        }
        val style = Style(colorFor(value))
        writer.writeLine("[" + "█".repeat(filled) + "░".repeat(barWidth - filled) + "]" + suffix, style)
        // The first bar cell is at column 1, after "[". Anchor on the last filled cell, or the
        // first cell when empty, then right-align the number to it.
        val valueText = value.toString()
        writer.write(filled.coerceAtLeast(1) - CellWidth.of(valueText) + 1, valueText, style)
        writer.newLine()
    }

    private companion object {
        fun defaultColor(value: Int, maxValue: Int): ChromeRole = when {
            value.toDouble() >= maxValue * 0.7 -> ChromeRole.DANGER
            value.toDouble() >= maxValue * 0.3 -> ChromeRole.WARNING
            else -> ChromeRole.INFO
        }
    }
}
