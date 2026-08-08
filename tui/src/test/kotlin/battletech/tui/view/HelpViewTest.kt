package battletech.tui.view

import battletech.tui.input.KeyHint
import battletech.tui.input.KeySection
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class HelpViewTest {

    private fun readLine(buffer: ScreenBuffer, x: Int, y: Int, width: Int): String =
        (x until x + width).joinToString("") { buffer.get(it, y).char }.trimEnd()

    private fun render(sections: List<KeySection>, width: Int = 28, height: Int = 20): ScreenBuffer {
        val view = HelpView(sections)
        val buffer = ScreenBuffer(width, height)
        view.render(buffer, 0, 0, width, height)
        return buffer
    }

    @Test
    fun `renders the KEYS header`() {
        val buffer = render(listOf(KeySection("MOVEMENT", listOf(KeyHint("Enter", "select unit")))))

        assert(readLine(buffer, 0, 0, 28).startsWith("── KEYS ")) {
            "Expected KEYS header on row 0, got: '${readLine(buffer, 0, 0, 28)}'"
        }
    }

    @Test
    fun `renders each section's title and key-description rows`() {
        val buffer = render(
            listOf(
                KeySection("MOVEMENT", listOf(KeyHint("Enter", "select unit"))),
                KeySection("GLOBAL", listOf(KeyHint("ctrl+c", "quit"))),
            ),
        )

        assertEquals("MOVEMENT", readLine(buffer, 0, 1, 28))
        val hintRow = readLine(buffer, 0, 2, 28)
        assertTrue(hintRow.contains("Enter")) { "Expected key label, got: '$hintRow'" }
        assertTrue(hintRow.contains("select unit")) { "Expected description, got: '$hintRow'" }

        assertEquals("GLOBAL", readLine(buffer, 0, 4, 28))
        val globalRow = readLine(buffer, 0, 5, 28)
        assertTrue(globalRow.contains("ctrl+c")) { "Expected key label, got: '$globalRow'" }
        assertTrue(globalRow.contains("quit")) { "Expected description, got: '$globalRow'" }
    }

    @Test
    fun `blank row separates two sections`() {
        val buffer = render(
            listOf(
                KeySection("A", listOf(KeyHint("x", "one"))),
                KeySection("B", listOf(KeyHint("y", "two"))),
            ),
        )

        assertEquals("", readLine(buffer, 0, 3, 28))
    }

    @Test
    fun `a single section renders with no trailing blank section gap`() {
        val buffer = render(listOf(KeySection("GLOBAL", listOf(KeyHint("ctrl+c", "quit")))))

        assertEquals("GLOBAL", readLine(buffer, 0, 1, 28))
        assertEquals("", readLine(buffer, 0, 3, 28))
    }

    @Test
    fun `long descriptions wrap with a hanging indent under the key column`() {
        val longDescription = "this description is deliberately long enough that it must wrap onto a second line"
        val buffer = render(
            listOf(KeySection("GLOBAL", listOf(KeyHint("alt+0-5", longDescription)))),
            width = 24,
        )

        val firstLine = readLine(buffer, 0, 2, 24)
        val secondLine = readLine(buffer, 0, 3, 24)
        assertTrue(firstLine.contains("alt+0-5")) { "Expected key label on first line, got: '$firstLine'" }
        assertTrue(secondLine.isNotEmpty()) { "Expected a wrapped continuation line" }
        // Continuation text starts under the key column (after the "key + 2 spaces" prefix),
        // not flush against the left edge.
        assertEquals("", secondLine.take(9).trimEnd())
    }
}
