package tenter.screen

import com.github.ajalt.mordant.rendering.AnsiLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class MapRolePaletteTest {

    private data class Fixed(override val color: PaletteColor) : FixedColorRole

    @Test
    fun `a FixedColorRole resolves to its own color without any theme entry`() {
        val palette = MapRolePalette(
            name = "test",
            level = AnsiLevel.TRUECOLOR,
            defaultBackground = PaletteColor.TrueColor(0, 0, 0),
            colors = emptyMap(),
        )
        val role = Fixed(PaletteColor.TrueColor(255, 0, 0))

        assertEquals(PaletteColor.TrueColor(255, 0, 0), palette.foreground(role))
        // RolePalette.background delegates to foreground for any non-DEFAULT role.
        assertEquals(PaletteColor.TrueColor(255, 0, 0), palette.background(role))
    }

    @Test
    fun `a role absent from both the theme map and FixedColorRole still errors`() {
        val palette = MapRolePalette(
            name = "test",
            level = AnsiLevel.TRUECOLOR,
            defaultBackground = PaletteColor.TrueColor(0, 0, 0),
            colors = emptyMap(),
        )

        assertThrows<IllegalStateException> { palette.foreground(ChromeRole.ACCENT) }
    }
}
