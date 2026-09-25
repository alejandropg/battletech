package io.archinaut.tenter.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class CheckboxGlyphsTest {

    @Test
    fun `ships unicode ascii and nerd font sets`() {
        assertEquals("□", CheckboxGlyphs.DEFAULT.unchecked)
        assertEquals("x", CheckboxGlyphs.ASCII.checked)
        assertEquals(1, CheckboxGlyphs.NERD_FONT.checked.codePointCount(0, CheckboxGlyphs.NERD_FONT.checked.length))
    }

    @Test
    fun `rejects zero width wide control and multiple grapheme glyphs`() {
        assertThrows<IllegalArgumentException> { CheckboxGlyphs("\u0301", "x", "-") }
        assertThrows<IllegalArgumentException> { CheckboxGlyphs("界", "x", "-") }
        assertThrows<IllegalArgumentException> { CheckboxGlyphs("\u0007", "x", "-") }
        assertThrows<IllegalArgumentException> { CheckboxGlyphs("ab", "x", "-") }
    }
}
