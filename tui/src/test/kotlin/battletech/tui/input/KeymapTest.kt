package battletech.tui.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.input.KeyHint
import tenter.text.CellWidth

/**
 * Guards [HelpView][tenter.view.HelpView]'s column alignment: every hint's `keys`
 * label is measured with [CellWidth.of] to size the description column, so a label containing
 * a wide (2-cell) codepoint would silently misalign every row after it.
 */
internal class KeymapTest {

    private val allHints: List<KeyHint> =
        listOf(
            Keymap.MOVEMENT_IDLE,
            Keymap.BROWSING,
            Keymap.FACING,
            Keymap.ATTACK_IDLE,
            Keymap.WEAPON_DECLARING,
            Keymap.PHYSICAL_DECLARING,
            Keymap.GLOBAL.hints,
        ).flatten()

    @Test
    fun `every hint's keys label is exactly one cell per codepoint`() {
        for (hint in allHints) {
            assertEquals(
                hint.keys.codePointCount(0, hint.keys.length),
                CellWidth.of(hint.keys),
                "'${hint.keys}' (for '${hint.description}') contains a non-width-1 codepoint",
            )
        }
    }
}
