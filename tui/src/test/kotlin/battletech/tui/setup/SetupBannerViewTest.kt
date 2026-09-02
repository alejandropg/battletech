package battletech.tui.setup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.screen.ScreenBuffer
import tenter.text.CellWidth
import tenter.view.render

internal class SetupBannerViewTest {

    @Test
    fun `leaves a blank row between the top border and wide banner`() {
        val width = 120
        val height = SetupBannerView.reservedHeight(width)

        val buffer = render(SetupBannerView("prompt"), width, height)

        assertEquals(BANNER_LINES.size + 4, height)
        assertEquals("│" + " ".repeat(width - 2) + "│", row(buffer, 1))
        val banner = BANNER_LINES.first()
        val left = 1 + 1 + ((width - 4 - CellWidth.of(banner)) / 2)
        assertEquals(banner, row(buffer, 2).substring(left, left + CellWidth.of(banner)))
        assertEquals("prompt", row(buffer, BANNER_LINES.size + 2).substring(2, 8))
        assertEquals("? : help", row(buffer, BANNER_LINES.size + 2).substring(width - 10, width - 2))
        assertEquals("╰" + "─".repeat(width - 2) + "╯", row(buffer, height - 1))
    }

    @Test
    fun `leaves a blank row between the top border and fallback title`() {
        val width = 20
        val height = SetupBannerView.reservedHeight(width)

        val buffer = render(SetupBannerView("go"), width, height)

        assertEquals(5, height)
        assertEquals("│" + " ".repeat(width - 2) + "│", row(buffer, 1))
        assertEquals(SetupBannerView.FALLBACK_TITLE, row(buffer, 2).substring(5, 15))
        assertEquals("go", row(buffer, 3).substring(2, 4))
        assertEquals("? : help", row(buffer, 3).substring(width - 10, width - 2))
        assertEquals("╰" + "─".repeat(width - 2) + "╯", row(buffer, height - 1))
    }

    private fun row(buffer: ScreenBuffer, index: Int): String =
        (0 until buffer.width).joinToString("") { buffer.get(it, index).char }
}
