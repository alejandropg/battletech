package battletech.tui.animation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.screen.ChromeRole
import tenter.view.render
import kotlin.random.Random

internal class AnimationViewTest {

    @Test
    fun `every cell renders opaque with a hardcoded FixedColorRole, never a themed ChromeRole`() {
        val animation = LaserBurstAnimation(random = Random(11))
        val buffer = render(animation.frame(0), animation.size.width, animation.size.height)

        var sawNonSpace = false
        for (y in 0 until animation.size.height) {
            for (x in 0 until animation.size.width) {
                val cell = buffer.get(x, y)
                assertEquals(ANIMATION_BACKGROUND, cell.style.bg, "cell ($x,$y) background is not the fixed animation background")
                assertTrue(cell.style.fg is tenter.screen.FixedColorRole, "cell ($x,$y) fg is not a FixedColorRole")
                assertTrue(cell.style.fg !is ChromeRole, "cell ($x,$y) fg resolved through a themed ChromeRole")
                if (cell.char != " ") sawNonSpace = true
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
