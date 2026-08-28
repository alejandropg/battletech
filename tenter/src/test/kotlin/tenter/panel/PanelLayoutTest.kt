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
}
