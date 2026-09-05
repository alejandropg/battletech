package battletech.tui.animation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.animation.Animation
import tenter.animation.AnimationSize
import tenter.animation.GlyphGrid
import tenter.screen.Cell
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private class SizedAnimation(override val size: AnimationSize) : Animation {
    override val frameCount: Int = 1
    override val frameDuration: Duration = 1.milliseconds

    override fun frame(index: Int): GlyphGrid = GlyphGrid(size, { 0 }, { Cell.Style() })
}

internal class AnimationLayoutTest {

    private val animation = LaserBurstAnimation(random = Random(1))
    private val panelWidth = animation.size.panelSize.width
    private val panelHeight = animation.size.panelSize.height

    private fun animations(count: Int): List<Animation> = List(count) { animation }

    private fun assertOnScreen(placement: PanelPlacement, width: Int, height: Int) {
        assertTrue(placement.x >= 0 && placement.x + panelWidth <= width, "x=${placement.x} off-screen on $width wide")
        assertTrue(placement.y >= 0 && placement.y + panelHeight <= height, "y=${placement.y} off-screen on $height tall")
    }

    // 240x80 has enough slack on every axis that no offset gets clamped, so these assert the raw
    // (dx, dy) offsets from AnimationLayout's KDoc directly: centre is (120, 40).

    @Test
    fun `one panel sits above centre, slightly left, at its raw offset`() {
        val placements = AnimationLayout.place(animations(1), 240, 80)

        assertEquals(1, placements.size)
        assertEquals(PanelPlacement(x = 71, y = 16), placements.single())
    }

    @Test
    fun `two panels are 'above centre' and 'lower-left of centre', in that order`() {
        val placements = AnimationLayout.place(animations(2), 240, 80)

        assertEquals(
            listOf(
                PanelPlacement(x = 71, y = 16),
                PanelPlacement(x = 47, y = 44),
            ),
            placements,
        )
    }

    @Test
    fun `three panels are 'above centre', 'lower-left', 'right of centre', in that order`() {
        val placements = AnimationLayout.place(animations(3), 240, 80)

        assertEquals(
            listOf(
                PanelPlacement(x = 71, y = 16),
                PanelPlacement(x = 47, y = 44),
                PanelPlacement(x = 127, y = 39),
            ),
            placements,
        )
    }

    @Test
    fun `each panel's offset from screen centre is fixed, regardless of screen size`() {
        fun offsetsFromCenter(width: Int, height: Int) =
            AnimationLayout.place(animations(3), width, height).map { PanelPlacement(it.x - width / 2, it.y - height / 2) }

        assertEquals(offsetsFromCenter(240, 80), offsetsFromCenter(300, 100))
    }

    @Test
    fun `every placement is fully on-screen, at every size and count`() {
        val sizes = listOf(72 to 22, 80 to 30, 100 to 30, 120 to 40, 160 to 45, 200 to 50, 240 to 60)
        for ((width, height) in sizes) {
            for (count in 1..3) {
                AnimationLayout.place(animations(count), width, height).forEach { assertOnScreen(it, width, height) }
            }
        }
    }

    @Test
    fun `panels keep the full 4-cell margin whenever the screen can spare it`() {
        // 80x30 has exactly panelWidth+8 columns and panelHeight+8 rows of room — the smallest
        // screen where a full 4-cell margin is still possible on every side.
        val placements = AnimationLayout.place(animations(3), 80, 30)

        placements.forEach { p ->
            assertTrue(p.x >= 4 && p.x + panelWidth <= 80 - 4, "x=${p.x} does not keep a 4-cell margin")
            assertTrue(p.y >= 4 && p.y + panelHeight <= 30 - 4, "y=${p.y} does not keep a 4-cell margin")
        }
    }

    @Test
    fun `the margin shrinks, never goes negative, on a screen too small to spare 4 cells`() {
        // Exactly panel-sized: no room for any margin at all — panels sit flush against the edge.
        val placements = AnimationLayout.place(animations(3), panelWidth, panelHeight)

        assertEquals(PanelPlacement(x = 0, y = 0), placements[0], "slot 1: clamped flush to the only cell that fits")
        assertEquals(PanelPlacement(x = 0, y = 0), placements[1], "slot 2: clamped flush to the only cell that fits")
        assertEquals(PanelPlacement(x = 0, y = 0), placements[2], "slot 3: clamped flush to the only cell that fits")
        placements.forEach { assertOnScreen(it, panelWidth, panelHeight) }
    }

    @Test
    fun `panels are free to overlap on a cramped screen — nothing prevents it`() {
        // 120x40 has no room to place slot 1 and slot 2 at their raw offsets without violating the
        // margin, so both clamp toward the same corner and end up sharing space — that's accepted,
        // not avoided.
        val placements = AnimationLayout.place(animations(2), 120, 40)

        val slot1 = placements[0]
        val slot2 = placements[1]
        val overlapsX = slot1.x < slot2.x + panelWidth && slot2.x < slot1.x + panelWidth
        val overlapsY = slot1.y < slot2.y + panelHeight && slot2.y < slot1.y + panelHeight
        assertTrue(overlapsX && overlapsY, "expected these two panels to overlap on a 120x40 screen")
    }

    @Test
    fun `nothing is placed when a single panel does not fit at all`() {
        assertTrue(AnimationLayout.place(animations(1), panelWidth - 1, 40).isEmpty())
        assertTrue(AnimationLayout.place(animations(1), 120, panelHeight - 1).isEmpty())
        assertTrue(AnimationLayout.place(animations(3), 10, 5).isEmpty())
    }

    @Test
    fun `placement is fully deterministic — same inputs, same output`() {
        assertEquals(AnimationLayout.place(animations(3), 173, 47), AnimationLayout.place(animations(3), 173, 47))
    }

    @Test
    fun `each animation is placed using its own bordered size`() {
        val small = SizedAnimation(AnimationSize(width = 10, height = 5))
        val large = SizedAnimation(AnimationSize(width = 100, height = 30))

        val placements = AnimationLayout.place(listOf(small, large), screenWidth = 120, screenHeight = 40)

        assertEquals(2, placements.size)
        assertTrue(placements[0].x + small.size.panelSize.width <= 120)
        assertTrue(placements[0].y + small.size.panelSize.height <= 40)
        assertTrue(placements[1].x + large.size.panelSize.width <= 120)
        assertTrue(placements[1].y + large.size.panelSize.height <= 40)
    }

    @Test
    fun `one oversized animation rejects the whole volley`() {
        val small = SizedAnimation(AnimationSize(width = 10, height = 5))
        val oversized = SizedAnimation(AnimationSize(width = 121, height = 5))

        assertTrue(AnimationLayout.place(listOf(small, oversized), 120, 40).isEmpty())
    }
}
