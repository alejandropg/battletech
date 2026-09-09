package tenter.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import tenter.view.contentView

private enum class LayoutPanelId : PanelId { MAIN, A, B, C, FIXED }

internal class PanelLayoutTest {

    private fun panel(
        id: LayoutPanelId,
        width: Int = 20,
        minimizedWidth: Int = Panel.MINIMIZED_WIDTH,
    ): Panel<LayoutPanelId, Unit> = Panel(
        id = id,
        title = id.name,
        normal = { Panel.Presentation(contentView { }, width) },
        minimized = { Panel.Presentation(contentView { }, minimizedWidth) },
        maximized = { Panel.Presentation(contentView { }, width) },
    )

    private fun main() = Panel<LayoutPanelId, Unit>(
        id = LayoutPanelId.MAIN,
        title = "MAIN",
        normal = { Panel.Presentation(contentView { }, 0) },
    )

    private fun widthOf(widths: Map<LayoutPanelId, Int>): (Panel<LayoutPanelId, Unit>) -> Int =
        { widths.getValue(it.id) }

    @Test
    fun `main layout allocates the remaining width to the main panel`() {
        val a = panel(LayoutPanelId.A, 20)
        val b = panel(LayoutPanelId.B, 15)

        val layout = PanelLayout.compute(
            width = 100,
            height = 30,
            reservedTop = 4,
            main = main(),
            sides = listOf(a, b),
            widthOf = widthOf(mapOf(LayoutPanelId.A to 20, LayoutPanelId.B to 15)),
        )

        assertEquals(65, layout.main!!.width)
        assertEquals(20, layout.sides[0].width)
        assertEquals(65, layout.sides[0].x)
        assertEquals(15, layout.sides[1].width)
        assertEquals(85, layout.sides[1].x)
    }

    @Test
    fun `a maximized side panel owns the whole content region`() {
        val a = panel(LayoutPanelId.A)
        val b = panel(LayoutPanelId.B)
        val set = PanelSet.mainAndSides(main(), listOf(a, b))
        set.focus(LayoutPanelId.A)
        set.cycleFocusedState(1)

        val layout = set.render(
            tenter.screen.Canvas.of(tenter.screen.ScreenBuffer(100, 30)),
            Unit,
            setOf(LayoutPanelId.A, LayoutPanelId.B),
            reservedTop = 4,
        )

        assertNull(layout.main)
        assertSame(a, layout.sides.single().panel)
        assertEquals(100, layout.sides.single().width)
        assertEquals(26, layout.sides.single().height)
    }

    @Test
    fun `side hit testing excludes the main region`() {
        val a = panel(LayoutPanelId.A)
        val layout = PanelLayout.compute(
            width = 100,
            height = 30,
            reservedTop = 4,
            main = main(),
            sides = listOf(a),
            widthOf = widthOf(mapOf(LayoutPanelId.A to 20)),
        )

        assertNull(layout.sideAt(0, 10))
        assertSame(a, layout.sideAt(85, 10)?.panel)
        assertNull(layout.sideAt(85, 2))
    }

    @Test
    fun `uniform layout divides columns and reserves hidden columns`() {
        val a = panel(LayoutPanelId.A)
        val b = panel(LayoutPanelId.B)
        val c = panel(LayoutPanelId.C)
        val width = widthOf(mapOf(LayoutPanelId.A to 20, LayoutPanelId.B to 20, LayoutPanelId.C to 20))

        val layout = PanelLayout.computeUniform(82, 30, 0, listOf(a, b, c), columnCount = 4, widthOf = width)

        assertEquals(listOf(21, 21, 20), layout.sides.map { it.width })
        assertEquals(listOf(0, 21, 42), layout.sides.map { it.x })
    }

    @Test
    fun `uniform layout places fixed panels after proportional columns`() {
        val a = panel(LayoutPanelId.A)
        val b = panel(LayoutPanelId.B)
        val c = panel(LayoutPanelId.C)
        val fixed = panel(LayoutPanelId.FIXED, width = 28)
        val widths = widthOf(
            mapOf(LayoutPanelId.A to 20, LayoutPanelId.B to 20, LayoutPanelId.C to 20, LayoutPanelId.FIXED to 28),
        )

        val layout = PanelLayout.computeUniform(
            width = 120,
            height = 30,
            reservedTop = 0,
            panels = listOf(a, b, c, fixed),
            columnCount = 3,
            fixedWidthPanels = setOf(LayoutPanelId.FIXED),
            widthOf = widths,
        )

        assertEquals(listOf(31, 31, 30, 28), layout.sides.map { it.width })
        assertEquals(listOf(0, 31, 62, 92), layout.sides.map { it.x })
    }

    @Test
    fun `empty uniform frame has an empty layout`() {
        val layout = PanelLayout.computeUniform(
            width = 80,
            height = 24,
            reservedTop = 3,
            panels = emptyList(),
            widthOf = widthOf(emptyMap()),
        )

        assertNull(layout.main)
        assertEquals(emptyList<PanelLayout.Slot<LayoutPanelId, Unit>>(), layout.sides)
        assertEquals(21, layout.contentHeight)
    }
}
