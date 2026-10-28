package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tactical.model.HexCoordinates
import io.archinaut.battletech.tui.aGameState
import io.archinaut.battletech.tui.game.AppState
import io.archinaut.battletech.tui.game.GamePanelId
import io.archinaut.battletech.tui.game.phase.MovementPhase
import io.archinaut.battletech.tui.input.Keybindings
import io.archinaut.tenter.color.StandardRole
import io.archinaut.tenter.widget.panel.Panel
import io.archinaut.tenter.widget.panel.PanelSet
import io.archinaut.tenter.widget.panel.PanelMode
import io.archinaut.tenter.widget.VerticalText
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.tenter.widget.Paragraph
import io.archinaut.tenter.widget.revealed
import io.archinaut.tenter.widget.panel.Panels
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/** Managed panel behavior is exercised through the public [PanelSet] seam. */
internal class PanelTest {

    private val inputs = PanelInputs(
        AppState(gameState = aGameState(), phase = MovementPhase.SelectingUnit, cursor = HexCoordinates(0, 0)),
        Keybindings.DEFAULT,
    )

    private fun stubContent(lines: Int): MeasurableWidget = MeasurableWidget { cursor ->
        repeat(lines) { row -> cursor.writeLine("row$row") }
    }

    private fun panel(
        normal: (PanelInputs) -> MeasurableWidget = { stubContent(3) },
        minimized: ((PanelInputs) -> MeasurableWidget)? = null,
        maximized: ((PanelInputs) -> MeasurableWidget)? = null,
    ): Panel<GamePanelId, PanelInputs> = Panel(
        id = GamePanelId.LOG,
        title = "T",
        badge = "9",
        normal = normal,
        minimized = minimized,
        maximized = maximized,
    )

    private fun renderPanel(
        panel: Panel<GamePanelId, PanelInputs>,
        width: Int = 30,
        height: Int = 10,
        configure: (PanelSet<GamePanelId, PanelInputs>) -> Unit = {},
    ): Canvas {
        val set = PanelSet(listOf(panel))
        configure(set)
        return render(set, width, height)
    }

    /** Renders LOG across the whole [width]x[height] canvas. */
    private fun render(set: PanelSet<GamePanelId, PanelInputs>, width: Int = 30, height: Int = 10): Canvas {
        val buffer = Canvas.offscreen(width, height)
        Panels(inputs, mapOf(GamePanelId.LOG to Rect(0, 0, width, height))).render(buffer, set)
        return buffer
    }

    @Test
    fun `area and mode are observed through the set`() {
        val set = PanelSet(listOf(panel()))
        render(set)

        assertEquals(PanelMode.NORMAL, set.modeOf(GamePanelId.LOG))
        assertEquals(30, checkNotNull(set.layout).slots.single().outer.width)
    }

    @Test
    fun `renders chrome even when content draws nothing`() {
        val buffer = renderPanel(panel(normal = { MeasurableWidget { } }))

        assertEquals("╭", buffer.get(0, 0).symbol)
        assertEquals("9", buffer.get(3, 0).symbol)
    }

    @Test
    fun `scroll offset persists and is only observed after rendering`() {
        val unscrolled = renderPanel(panel(normal = { stubContent(20) }))
        val panel = panel(normal = { stubContent(20) })
        val set = PanelSet(listOf(panel))

        val firstAfterScroll = run {
            set.scroll(set.focused, 0, 3)
            render(set)
        }
        val secondAfterScroll = render(set)

        assertNotEquals(unscrolled.text(), firstAfterScroll.text())
        assertEquals(firstAfterScroll.text(), secondAfterScroll.text())
    }

    @Test
    fun `resize re-follows a settled reveal without caller reset plumbing`() {
        val revealing = MeasurableWidget { cursor ->
            repeat(20) { row ->
                val line = Paragraph("row$row")
                cursor.draw(if (row == 15) line.revealed() else line)
            }
        }
        val panel = panel(normal = { revealing })
        val set = PanelSet(listOf(panel))

        render(set)
        set.scroll(set.focused, 0, -100)
        val stayedAway = render(set)
        val forced = render(set, height = 8)

        assertNotEquals(stayedAway.text(), forced.text())
    }

    @Test
    fun `focus controls border color through the set`() {
        val panel = panel()

        val focused = renderPanel(panel)
        assertEquals(StandardRole.PANEL_BORDER_FOCUSED, focused.get(0, 0).style.fg)

        val unfocusedPanel = panel()
        val unfocusedSet = PanelSet(
            listOf(
                unfocusedPanel,
                Panel(id = GamePanelId.BOARD, title = "B", normal = { MeasurableWidget { } }),
            ),
        )
        unfocusedSet.focus(GamePanelId.BOARD)
        val buffer = Canvas.offscreen(30, 10)
        Panels(inputs, mapOf(GamePanelId.LOG to Rect(0, 0, 15, 10), GamePanelId.BOARD to Rect(15, 0, 15, 10)))
            .render(buffer, unfocusedSet)
        assertEquals(StandardRole.PANEL_BORDER, buffer.get(0, 0).style.fg)
    }

    @Test
    fun `minimized content never builds normal content`() {
        var normalBuilds = 0
        val panel = panel(
            normal = {
                normalBuilds++
                stubContent(3)
            },
            minimized = { VerticalText("LOG") },
        )

        val buffer = renderPanel(panel, width = GamePanels.MINIMIZED_WIDTH, configure = { it.cycleFocusedMode(-1) })

        assertEquals("9", buffer.get(3, 0).symbol)
        assertEquals("L", buffer.get(3, 2).symbol)
        assertEquals(0, normalBuilds)
    }

    @Test
    fun `a focused panel's scrollbar thumb uses the focus color`() {
        val buffer = renderPanel(panel(normal = { stubContent(20) }))

        val thumbRow = (1..8).first { buffer.get(29, it).symbol == "▐" }
        assertEquals(StandardRole.PANEL_BORDER_FOCUSED, buffer.get(29, thumbRow).style.fg)
    }
}
