package battletech.tui.animation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.screen.Cell
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** A [WeaponAnimation] of a chosen length, so playback progress is easy to assert. */
private class FixedAnimation(
    override val frameCount: Int,
    override val frameDuration: Duration = 10.milliseconds,
) : WeaponAnimation {
    override val size: AnimationSize = AnimationSize(width = 5, height = 3)

    override fun frame(index: Int): Glyphs = Glyphs(size, { 0 }, { Cell.Style() })
}

internal class VolleyPlaybackTest {

    private fun volley(vararg frameCounts: Int): VolleyPlayback = checkNotNull(
        VolleyPlayback.start(
            animations = frameCounts.map { FixedAnimation(it) },
            placements = frameCounts.indices.map { PanelPlacement(it * 10, it) },
            generation = 1,
        ),
    )

    /** The slot each visible panel came from — placements were seeded as `x = slot * 10`. */
    private fun VolleyPlayback.visibleSlots(): List<Int> = visible().map { it.x / 10 }

    @Test
    fun `slot 0 is already playing so its first frame lands with the triggering event`() {
        val playback = volley(3, 3)

        assertTrue(playback.panels.getValue(0) is PanelState.Playing)
        assertEquals(1, playback.visible().size, "only slot 0 is on screen at the start")
        assertEquals(0, playback.visible().single().frameIndex)
    }

    @Test
    fun `a later slot waits out its stagger as Pending and is invisible until its first tick`() {
        val playback = volley(3, 3, 3)

        assertTrue(playback.panels.getValue(1) is PanelState.Pending)
        assertTrue(playback.panels.getValue(2) is PanelState.Pending)

        val started = checkNotNull(playback.advance(1))
        assertTrue(started.panels.getValue(1) is PanelState.Playing)
        assertEquals(listOf(0, 1), started.visibleSlots(), "slot 1 has joined slot 0")
    }

    @Test
    fun `advancing a playing slot steps one frame at a time`() {
        var playback = volley(3)
        assertEquals(0, playback.visible().single().frameIndex)

        playback = checkNotNull(playback.advance(0))
        assertEquals(1, playback.visible().single().frameIndex)

        playback = checkNotNull(playback.advance(0))
        assertEquals(2, playback.visible().single().frameIndex)
    }

    @Test
    fun `a panel is dropped when its animation ends, while longer panels keep playing`() {
        // Slot 0 runs 3 frames, slot 1 runs 1 — slot 1 finishes first and vanishes on its own.
        var playback = volley(3, 1)
        playback = checkNotNull(playback.advance(1)) // slot 1: Pending -> frame 0
        assertEquals(2, playback.visible().size)

        playback = checkNotNull(playback.advance(1)) // slot 1: past its last frame -> gone
        assertEquals(listOf(0), playback.visibleSlots(), "slot 1 vanished, slot 0 stayed")
        assertTrue(1 !in playback.panels)
    }

    @Test
    fun `the volley is over only once the last panel finishes`() {
        val playback = volley(2)

        val afterFirstFrame = playback.advance(0) // frame 0 -> 1
        assertNotNull(afterFirstFrame, "one frame left, the volley continues")

        assertNull(checkNotNull(afterFirstFrame).advance(0), "past the last frame, nothing remains")
    }

    @Test
    fun `a tick for an already-removed slot is ignored rather than resurrecting it`() {
        var playback = volley(3, 1)
        playback = checkNotNull(playback.advance(1))
        playback = checkNotNull(playback.advance(1)) // slot 1 gone

        assertEquals(playback, checkNotNull(playback.advance(1)))
    }

    @Test
    fun `visible panels are ordered by slot, which is paint order`() {
        var playback = volley(5, 5, 5)
        playback = checkNotNull(playback.advance(2)) // start slot 2 before slot 1
        playback = checkNotNull(playback.advance(1))

        assertEquals(listOf(0, 1, 2), playback.visibleSlots())
    }

    @Test
    fun `start refuses a mismatched or empty set of animations`() {
        assertNull(VolleyPlayback.start(emptyList(), emptyList(), generation = 1))
        assertNull(
            VolleyPlayback.start(listOf(FixedAnimation(1)), placements = emptyList(), generation = 1),
            "a missing placement must not silently drop a panel",
        )
    }
}
