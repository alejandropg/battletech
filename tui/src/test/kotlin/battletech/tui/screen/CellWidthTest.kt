package battletech.tui.screen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class CellWidthTest {

    @Test
    fun `ascii printable chars are width 1`() {
        assertEquals(1, CellWidth.of('A'.code))
        assertEquals(1, CellWidth.of(' '.code))
        assertEquals(1, CellWidth.of('~'.code))
    }

    @Test
    fun `control chars are width 0`() {
        assertEquals(0, CellWidth.of(0x00))
        assertEquals(0, CellWidth.of(0x1F))
        assertEquals(0, CellWidth.of(0x7F))
    }

    @Test
    fun `box drawing chars are width 1`() {
        assertEquals(1, CellWidth.of('│'.code))
        assertEquals(1, CellWidth.of('─'.code))
        assertEquals(1, CellWidth.of('╭'.code))
    }

    @Test
    fun `nerd-font dice icons in supplementary PUA are width 1`() {
        // nf-md-dice_1..6 → U+F01CA..U+F01CF
        for (cp in 0xF01CA..0xF01CF) {
            assertEquals(1, CellWidth.of(cp), "codepoint U+%04X".format(cp))
        }
    }

    @Test
    fun `CJK ideographs are width 2`() {
        assertEquals(2, CellWidth.of(0x4E2D)) // 中
        assertEquals(2, CellWidth.of(0x65E5)) // 日
    }

    @Test
    fun `emoji are width 2`() {
        assertEquals(2, CellWidth.of(0x1F600)) // 😀
    }

    @Test
    fun `string width sums codepoints, not UTF-16 chars`() {
        val dice = String(Character.toChars(0xF01CA)) + String(Character.toChars(0xF01CB))
        assertEquals(4, dice.length)               // sanity: 2 surrogate pairs = 4 UTF-16 chars
        assertEquals(2, CellWidth.of(dice))         // but only 2 visual cells
    }

    @Test
    fun `mixed ASCII and nerd-font string`() {
        val text = "P1 " + String(Character.toChars(0xF01CA)) + String(Character.toChars(0xF01CB)) + " 9"
        // "P1 " = 3, two dice = 2, " 9" = 2 → 7
        assertEquals(7, CellWidth.of(text))
    }
}
