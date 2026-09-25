package tenter.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.input.KeyHint
import tenter.input.KeySection
import tenter.screen.ScreenBuffer

internal class HelpViewTest {

    private fun renderHelp(sections: List<KeySection>, width: Int = 28, height: Int = 20): ScreenBuffer =
        render(HelpView(sections), width, height)

    @Test
    fun `renders the KEYS header`() {
        val buffer = renderHelp(listOf(KeySection("MOVEMENT", listOf(KeyHint("Enter", "select unit")))))

        assert(buffer.line(0, width = 28).startsWith("── KEYS ")) {
            "Expected KEYS header on row 0, got: '${buffer.line(0, width = 28)}'"
        }
    }

    @Test
    fun `renders each section's title and key-description rows`() {
        val buffer = renderHelp(
            listOf(
                KeySection("MOVEMENT", listOf(KeyHint("Enter", "select unit"))),
                KeySection("GLOBAL", listOf(KeyHint("ctrl+c", "quit"))),
            ),
        )

        assertEquals("MOVEMENT", buffer.line(1, width = 28))
        val hintRow = buffer.line(2, width = 28)
        assertTrue(hintRow.contains("Enter")) { "Expected key label, got: '$hintRow'" }
        assertTrue(hintRow.contains("select unit")) { "Expected description, got: '$hintRow'" }

        assertEquals("GLOBAL", buffer.line(4, width = 28))
        val globalRow = buffer.line(5, width = 28)
        assertTrue(globalRow.contains("ctrl+c")) { "Expected key label, got: '$globalRow'" }
        assertTrue(globalRow.contains("quit")) { "Expected description, got: '$globalRow'" }
    }

    @Test
    fun `blank row separates two sections`() {
        val buffer = renderHelp(
            listOf(
                KeySection("A", listOf(KeyHint("x", "one"))),
                KeySection("B", listOf(KeyHint("y", "two"))),
            ),
        )

        assertEquals("", buffer.line(3, width = 28))
    }

    @Test
    fun `a single section renders with no trailing blank section gap`() {
        val buffer = renderHelp(listOf(KeySection("GLOBAL", listOf(KeyHint("ctrl+c", "quit")))))

        assertEquals("GLOBAL", buffer.line(1, width = 28))
        assertEquals("", buffer.line(3, width = 28))
    }

    @Test
    fun `long descriptions wrap with a hanging indent under the key column`() {
        val longDescription = "this description is deliberately long enough that it must wrap onto a second line"
        val buffer = renderHelp(
            listOf(KeySection("GLOBAL", listOf(KeyHint("alt+0-5", longDescription)))),
            width = 24,
        )

        val firstLine = buffer.line(2, width = 24)
        val secondLine = buffer.line(3, width = 24)
        assertTrue(firstLine.contains("alt+0-5")) { "Expected key label on first line, got: '$firstLine'" }
        assertTrue(secondLine.isNotEmpty()) { "Expected a wrapped continuation line" }
        // Continuation text starts under the key column (after the "key + 2 spaces" prefix),
        // not flush against the left edge.
        assertEquals("", secondLine.take(9).trimEnd())
    }
}
