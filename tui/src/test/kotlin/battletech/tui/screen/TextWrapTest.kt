package battletech.tui.screen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TextWrapTest {

    @Test
    fun `empty text returns empty list`() {
        assertEquals(emptyList<String>(), TextWrap.wrap("", 10))
        assertEquals(emptyList<String>(), TextWrap.wrap("   ", 10))
    }

    @Test
    fun `short text fits on one line`() {
        assertEquals(listOf("hello world"), TextWrap.wrap("hello world", 20))
    }

    @Test
    fun `wraps on spaces when capacity is exceeded`() {
        assertEquals(listOf("hello", "world"), TextWrap.wrap("hello world", 5))
    }

    @Test
    fun `first line uses firstWidth, subsequent lines use continuationWidth`() {
        // firstWidth=10 → "hello" (5) + " " (1) + "world" (5) = 11 > 10 → "hello" only.
        // continuationWidth=5 → "world" (5) fits, "foo" goes on its own line.
        val out = TextWrap.wrap("hello world foo", firstWidth = 10, continuationWidth = 5)
        assertEquals(listOf("hello", "world", "foo"), out)
    }

    @Test
    fun `word visually longer than capacity is hard-split by codepoint`() {
        val out = TextWrap.wrap("supercalifragilistic", 8)
        // 20-char word, capacity 8 → "supercal", "ifragili", "stic"
        assertEquals(listOf("supercal", "ifragili", "stic"), out)
    }

    @Test
    fun `nerd-font icons count as one visual cell each`() {
        val die1 = String(Character.toChars(0xF01CA))
        val die2 = String(Character.toChars(0xF01CB))
        // "P1 ⚀⚁ 9" visual width = 2 + 1 + 1 + 1 + 1 + 1 = 7 cells.
        val out = TextWrap.wrap("P1 $die1$die2 9", 7)
        assertEquals(listOf("P1 $die1$die2 9"), out)
    }

    @Test
    fun `zero or negative capacity is coerced to one`() {
        // Must terminate; capacity 1 hard-splits one cell per line.
        val out = TextWrap.wrap("ab", 0)
        assertEquals(listOf("a", "b"), out)
    }
}
