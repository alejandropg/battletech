package io.archinaut.battletech.tui.view.record

import io.archinaut.battletech.tui.icon.emptyCircleIcon
import io.archinaut.battletech.tui.icon.filledCircleIcon
import io.archinaut.battletech.tui.view.render
import io.archinaut.tenter.screen.Line
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.screen.Span
import io.archinaut.tenter.terminal.CompletedFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PipBoxViewTest {

    private val filled = filledCircleIcon()
    private val empty = emptyCircleIcon()

    @Test
    fun `boxes the damage taken first, then the points still available`() {
        val box = PipBoxView(LocationDiagram.Location(remaining = 2, max = 5), width = 3, rows = 2)

        assertEquals(
            CompletedFrame.of(
                Line("╭───╮", SheetStyles.TEXT_PRIMARY),
                Line(
                    Span("│", SheetStyles.TEXT_PRIMARY),
                    Span(filled + filled + filled, SheetStyles.DANGER),
                    Span("│", SheetStyles.TEXT_PRIMARY),
                ),
                Line(
                    Span("│", SheetStyles.TEXT_PRIMARY),
                    Span(empty + empty, SheetStyles.TEXT_PRIMARY),
                    Span(" "),
                    Span("│", SheetStyles.TEXT_PRIMARY),
                ),
                Line("╰───╯", SheetStyles.TEXT_PRIMARY),
            ),
            render(box, 5, 4),
        )
    }

    @Test
    fun `a destroyed location outlines in the destroyed style`() {
        val box = PipBoxView(LocationDiagram.Location(remaining = 0, max = 1, destroyed = true), width = 1, rows = 1)

        assertEquals(CompletedFrame.of(Line("╭─╮", SheetStyles.DESTROYED)), render(box, 3, 3).region(Rect(0, 0, 3, 1)))
    }
}
