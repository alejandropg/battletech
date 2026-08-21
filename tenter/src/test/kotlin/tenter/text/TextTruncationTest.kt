package tenter.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TextTruncationTest {

    @Test
    fun `leaves text unchanged when it fits`() {
        assertEquals("hello", TextTruncation.ellipsize("hello", 5))
        assertEquals("hello", TextTruncation.ellipsize("hello", 8))
    }

    @Test
    fun `places ellipsis in final available cell when text is too wide`() {
        assertEquals("hell…", TextTruncation.ellipsize("hello!", 5))
    }

    @Test
    fun `does not split wide code points`() {
        assertEquals("中…", TextTruncation.ellipsize("中日A", 3))
    }

    @Test
    fun `handles zero and one-cell limits`() {
        assertEquals("", TextTruncation.ellipsize("hello", 0))
        assertEquals("…", TextTruncation.ellipsize("hello", 1))
    }
}
