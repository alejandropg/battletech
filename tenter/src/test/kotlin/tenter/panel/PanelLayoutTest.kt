package tenter.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import tenter.screen.Canvas
import tenter.view.View

private enum class LayoutPanelId : PanelId { MAIN, A, B }

internal class PanelLayoutTest {

    private fun stubView(): View = object : View {
        override fun draw(canvas: Canvas) = Unit
    }

    private fun mainPanel() = Panel<LayoutPanelId, Unit>(
        id = LayoutPanelId.MAIN,
        title = "MAIN",
        normalWidth = 0,
        normal = { stubView() },
    )

    private fun sidePanel(id: LayoutPanelId, width: Int = 20) = Panel<LayoutPanelId, Unit>(
        id = id,
        title = id.name,
        normalWidth = width,
        normal = { stubView() },
        minimized = { stubView() },
        maximized = { stubView() },
    )

    @Test
    fun `main slot width is the leftover after side panels`() {
        val main = mainPanel()
        val a = sidePanel(LayoutPanelId.A)
        val b = sidePanel(LayoutPanelId.B, width = 15)

        val layout = PanelLayout.compute(width = 100, height = 30, reservedTop = 4, main = main, sides = listOf(a, b))
        val mainSlot = layout.main!!

        assertEquals(65, mainSlot.width)
        assertEquals(0, mainSlot.x)
        assertEquals(4, mainSlot.y)
        assertEquals(20, layout.sides[0].width)
        assertEquals(65, layout.sides[0].x)
        assertEquals(15, layout.sides[1].width)
        assertEquals(85, layout.sides[1].x)
    }

    @Test
    fun `a maximized side panel becomes the sole slot with main null and the full content rect`() {
        val main = mainPanel()
        val a = sidePanel(LayoutPanelId.A)
        val b = sidePanel(LayoutPanelId.B)
        a.cycleState(1) // NORMAL -> MAXIMIZED

        val layout = PanelLayout.compute(width = 100, height = 30, reservedTop = 4, main = main, sides = listOf(a, b))

        assertNull(layout.main)
        assertEquals(1, layout.sides.size)
        assertSame(a, layout.sides.single().panel)
        assertEquals(0, layout.sides.single().x)
        assertEquals(4, layout.sides.single().y)
        assertEquals(100, layout.sides.single().width)
        assertEquals(26, layout.sides.single().height)
    }

    @Test
    fun `sideAt hit-tests side slots only`() {
        val main = mainPanel()
        val a = sidePanel(LayoutPanelId.A)

        val layout = PanelLayout.compute(width = 100, height = 30, reservedTop = 4, main = main, sides = listOf(a))

        assertNull(layout.sideAt(0, 10), "main region")
        assertSame(a, layout.sideAt(85, 10)?.panel)
        assertNull(layout.sideAt(85, 2), "above reservedTop")
    }

    @Test
    fun `computeUniform divides width evenly across four panels`() {
        val panels = listOf(
            sidePanel(LayoutPanelId.A),
            sidePanel(LayoutPanelId.B),
            sidePanel(LayoutPanelId.A),
            sidePanel(LayoutPanelId.B),
        )

        val layout = PanelLayout.computeUniform(width = 80, height = 30, reservedTop = 0, panels = panels)

        assertNull(layout.main)
        assertEquals(4, layout.sides.size)
        assertEquals(listOf(20, 20, 20, 20), layout.sides.map { it.width })
        assertEquals(listOf(0, 20, 40, 60), layout.sides.map { it.x })
    }

    @Test
    fun `computeUniform gives the remainder to the leftmost panels`() {
        val panels = listOf(sidePanel(LayoutPanelId.A), sidePanel(LayoutPanelId.B), sidePanel(LayoutPanelId.A), sidePanel(LayoutPanelId.B))

        val layout = PanelLayout.computeUniform(width = 82, height = 30, reservedTop = 0, panels = panels)

        assertEquals(listOf(21, 21, 20, 20), layout.sides.map { it.width })
        assertEquals(82, layout.sides.sumOf { it.width })
    }

    @Test
    fun `computeUniform with a single visible panel gives it the full width`() {
        val panels = listOf(sidePanel(LayoutPanelId.A))

        val layout = PanelLayout.computeUniform(width = 80, height = 30, reservedTop = 0, panels = panels)

        assertEquals(1, layout.sides.size)
        assertEquals(80, layout.sides.single().width)
    }

    @Test
    fun `computeUniform can reserve columns for hidden panels`() {
        val panels = listOf(sidePanel(LayoutPanelId.A))

        val layout = PanelLayout.computeUniform(width = 80, height = 30, reservedTop = 0, panels = panels, columnCount = 4)

        assertEquals(20, layout.sides.single().width)
    }

    @Test
    fun `computeUniform ignores a panel's declared normalWidth`() {
        val panels = listOf(sidePanel(LayoutPanelId.A, width = 5), sidePanel(LayoutPanelId.B, width = 99))

        val layout = PanelLayout.computeUniform(width = 40, height = 30, reservedTop = 0, panels = panels)

        assertEquals(listOf(20, 20), layout.sides.map { it.width })
    }

    @Test
    fun `computeUniform still gives a maximized panel the whole content region`() {
        val a = sidePanel(LayoutPanelId.A)
        val b = sidePanel(LayoutPanelId.B)
        b.cycleState(1) // NORMAL -> MAXIMIZED

        val layout = PanelLayout.computeUniform(width = 80, height = 30, reservedTop = 4, panels = listOf(a, b))

        assertNull(layout.main)
        assertEquals(1, layout.sides.size)
        assertSame(b, layout.sides.single().panel)
        assertEquals(0, layout.sides.single().x)
        assertEquals(4, layout.sides.single().y)
        assertEquals(80, layout.sides.single().width)
        assertEquals(26, layout.sides.single().height)
    }
}
