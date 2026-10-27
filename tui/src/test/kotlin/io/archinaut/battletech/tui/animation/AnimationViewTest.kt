package io.archinaut.battletech.tui.animation

import io.archinaut.battletech.tui.view.render
import io.archinaut.tenter.color.StandardRole
import io.archinaut.tenter.color.TerminalColor
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AnimationViewTest {

    @Test
    fun `every cell renders opaque with a hardcoded TerminalColor, never a themed StandardRole`() {
        val animation = LaserBurstAnimation(random = Random(11))
        val buffer = render(animation.frame(0), animation.size.width, animation.size.height)

        var sawNonSpace = false
        for (y in 0 until animation.size.height) {
            for (x in 0 until animation.size.width) {
                val cell = buffer.get(x, y)
                assertEquals(ANIMATION_BACKGROUND, cell.style.bg, "cell ($x,$y) background is not the fixed animation background")
                assertTrue(cell.style.fg is TerminalColor, "cell ($x,$y) fg is not a fixed TerminalColor")
                assertTrue(cell.style.fg !is StandardRole, "cell ($x,$y) fg resolved through a themed StandardRole")
                if (cell.symbol != " ") sawNonSpace = true
            }
        }
        assertTrue(sawNonSpace, "expected the frame to draw at least one visible glyph")
    }

    @Test
    fun `a frame matches its animation's intrinsic size`() {
        val animation = MissileSalvoAnimation(random = Random(5))
        val frame = animation.frame(0)

        assertEquals(animation.size.width, frame.width)
        assertEquals(animation.size.height, frame.height)
    }
}
