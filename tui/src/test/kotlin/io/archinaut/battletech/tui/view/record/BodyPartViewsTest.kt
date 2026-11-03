package io.archinaut.battletech.tui.view.record

import io.archinaut.battletech.tui.icon.emptyCircleIcon
import io.archinaut.battletech.tui.icon.filledCircleIcon
import io.archinaut.battletech.tui.view.render
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The paper doll's body-part outlines: [ArmView], [LegView], and [TorsoWithRearView]. */
internal class BodyPartViewsTest {

    private val o = emptyCircleIcon()
    private val x = filledCircleIcon()

    private fun intact(max: Int) = LocationDiagram.Location(remaining = max, max = max)

    @Test
    fun `a left arm narrows toward the hand on its outer side`() {
        assertEquals(
            listOf("╭───╮", "│$o$o$o│", "│  ╱", "│  │", "╰──╯").joinToString("\n"),
            render(ArmView(intact(3), BodySide.LEFT, width = 3, rows = 1), 5, 5).text(),
        )
    }

    @Test
    fun `a right arm narrows toward the hand on its outer side`() {
        assertEquals(
            listOf("╭───╮", "│$o$o$o│", " ╲  │", " │  │", " ╰──╯").joinToString("\n"),
            render(ArmView(intact(3), BodySide.RIGHT, width = 3, rows = 1), 5, 5).text(),
        )
    }

    @Test
    fun `a left leg's foot reaches out past its box on the left`() {
        assertEquals(
            listOf("  ╭───╮", "  │$o$o$o│", " ╱    │", "╰─────╯").joinToString("\n"),
            render(LegView(intact(3), BodySide.LEFT, width = 3, rows = 1), 7, 4).text(),
        )
    }

    @Test
    fun `a right leg's foot reaches out past its box on the right`() {
        assertEquals(
            listOf("╭───╮", "│$o$o$o│", "│    ╲", "╰─────╯").joinToString("\n"),
            render(LegView(intact(3), BodySide.RIGHT, width = 3, rows = 1), 7, 4).text(),
        )
    }

    @Test
    fun `the center torso opens into a REAR-labelled track with its caption centered below`() {
        val view = TorsoWithRearView(
            front = intact(4), frontWidth = 4, frontRows = 1,
            rear = LocationDiagram.Location(remaining = 2, max = 4), rearWidth = 4, rearRows = 1,
            side = BodySide.CENTER,
        )

        assertEquals(
            listOf("╭────╮", "│$o$o$o$o│", "│    │", "│REAR│", "│$x$x$o$o│", "╰────╯", " 2/4").joinToString("\n"),
            render(view, 6, 7).text(),
        )
    }

    @Test
    fun `a side torso's narrower rear track hugs its outer edge`() {
        val view = TorsoWithRearView(
            front = intact(4), frontWidth = 4, frontRows = 1,
            rear = intact(2), rearWidth = 2, rearRows = 1,
            side = BodySide.LEFT,
        )

        assertEquals(
            listOf("╭────╮", "│$o$o$o$o│", " ╲   │", "  │╌╌│", "  │$o$o│", "  ╰──╯", "   2/2").joinToString("\n"),
            render(view, 6, 7).text(),
        )
    }
}
