package io.archinaut.tenter.view

import io.archinaut.tenter.screen.Cell
import io.archinaut.tenter.screen.RevealRect

internal class RecordingTextSink(override val width: Int) : TextSink {
    private val instructions = mutableListOf<ContentLayout.Instruction>()

    override fun write(column: Int, row: Int, text: String, style: Cell.Style) {
        instructions += ContentLayout.Instruction.Text(column, row, text, style)
    }

    override fun reveal(x: Int, y: Int, width: Int, height: Int) {
        instructions += ContentLayout.Instruction.Reveal(RevealRect(x, y, width, height))
    }

    override fun place(layout: ContentLayout, x: Int, y: Int) {
        instructions += ContentLayout.Instruction.Child(x, y, layout)
    }

    internal fun snapshot(): List<ContentLayout.Instruction> = instructions.toList()
}
