package battletech.tui.game

import battletech.tui.view.FrameLayout
import battletech.tui.view.PanelSlotLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class PanelScrollTest {

    // ── update: top-anchored ─────────────────────────────────────────────────

    @Test
    fun `top-anchored absent entry + wheelDown delta adds entry`() {
        val result = PanelScroll.update(
            offsets = emptyMap(),
            panelIndex = 1,
            delta = PanelScroll.STEP,
            maxOffset = 20,
            anchorBottom = false,
        )
        assertEquals(mapOf(1 to PanelScroll.STEP), result)
    }

    @Test
    fun `top-anchored scrolling back to 0 removes entry`() {
        val result = PanelScroll.update(
            offsets = mapOf(1 to PanelScroll.STEP),
            panelIndex = 1,
            delta = -PanelScroll.STEP,
            maxOffset = 20,
            anchorBottom = false,
        )
        assertEquals(emptyMap<Int, Int>(), result)
    }

    @Test
    fun `top-anchored offset clamps at maxOffset`() {
        val result = PanelScroll.update(
            offsets = mapOf(1 to 18),
            panelIndex = 1,
            delta = PanelScroll.STEP,
            maxOffset = 18,
            anchorBottom = false,
        )
        // 18 + 2 would be 20, clamped to maxOffset=18 which IS the clamped value;
        // but 18 == maxOffset for top-anchored is NOT the anchor (anchor=0), so entry stays
        assertEquals(mapOf(1 to 18), result)
    }

    @Test
    fun `top-anchored large delta clamps at maxOffset`() {
        val result = PanelScroll.update(
            offsets = emptyMap(),
            panelIndex = 2,
            delta = 999,
            maxOffset = 10,
            anchorBottom = false,
        )
        assertEquals(mapOf(2 to 10), result)
    }

    // ── update: bottom-anchored (LOG) ────────────────────────────────────────

    @Test
    fun `bottom-anchored absent entry + wheelUp scrolls away from bottom`() {
        val result = PanelScroll.update(
            offsets = emptyMap(),
            panelIndex = 5,
            delta = -PanelScroll.STEP,
            maxOffset = 20,
            anchorBottom = true,
        )
        // effective = maxOffset=20; 20 + (-2) = 18 — not equal to anchor(20), keep entry
        assertEquals(mapOf(5 to 18), result)
    }

    @Test
    fun `bottom-anchored scrolling back to maxOffset removes entry (re-stick)`() {
        val result = PanelScroll.update(
            offsets = mapOf(5 to 18),
            panelIndex = 5,
            delta = PanelScroll.STEP,
            maxOffset = 20,
            anchorBottom = true,
        )
        // 18 + 2 = 20 == maxOffset == anchor → remove entry
        assertEquals(emptyMap<Int, Int>(), result)
    }

    @Test
    fun `bottom-anchored large negative delta clamps at 0`() {
        val result = PanelScroll.update(
            offsets = emptyMap(),
            panelIndex = 5,
            delta = -999,
            maxOffset = 20,
            anchorBottom = true,
        )
        assertEquals(mapOf(5 to 0), result)
    }

    // ── update: maxOffset = 0 ────────────────────────────────────────────────

    @Test
    fun `maxOffset 0 leaves other panel entries untouched`() {
        val existing = mapOf(1 to 5, 3 to 8)
        val result = PanelScroll.update(
            offsets = existing,
            panelIndex = 3,
            delta = PanelScroll.STEP,
            maxOffset = 0,
            anchorBottom = false,
        )
        // Panel 3's stale entry is removed; panel 1 is unaffected
        assertEquals(mapOf(1 to 5), result)
    }

    @Test
    fun `maxOffset 0 removes stale entry for that panel`() {
        val result = PanelScroll.update(
            offsets = mapOf(3 to 5),
            panelIndex = 3,
            delta = 0,
            maxOffset = 0,
            anchorBottom = false,
        )
        assertEquals(emptyMap<Int, Int>(), result)
    }

    // ── slotAt ───────────────────────────────────────────────────────────────

    private fun layout(boardWidth: Int, boardHeight: Int, slots: List<PanelSlotLayout>) =
        FrameLayout(boardWidth, boardHeight, slots)

    @Test
    fun `slotAt returns the matching expanded slot`() {
        val slot = PanelSlotLayout(panelIndex = 1, x = 100, width = 28, collapsed = false)
        val layout = layout(boardWidth = 100, boardHeight = 40, slots = listOf(slot))

        assertEquals(slot, PanelScroll.slotAt(layout, x = 110, y = 10))
    }

    @Test
    fun `slotAt returns null when x is in board area`() {
        val slot = PanelSlotLayout(panelIndex = 1, x = 100, width = 28, collapsed = false)
        val layout = layout(boardWidth = 100, boardHeight = 40, slots = listOf(slot))

        assertNull(PanelScroll.slotAt(layout, x = 50, y = 10))
    }

    @Test
    fun `slotAt returns null when y is at or past boardHeight`() {
        val slot = PanelSlotLayout(panelIndex = 1, x = 100, width = 28, collapsed = false)
        val layout = layout(boardWidth = 100, boardHeight = 40, slots = listOf(slot))

        assertNull(PanelScroll.slotAt(layout, x = 110, y = 40))
    }

    @Test
    fun `slotAt returns null for collapsed slot`() {
        val slot = PanelSlotLayout(panelIndex = 1, x = 100, width = 7, collapsed = true)
        val layout = layout(boardWidth = 100, boardHeight = 40, slots = listOf(slot))

        assertNull(PanelScroll.slotAt(layout, x = 103, y = 10))
    }

    @Test
    fun `slotAt returns null when x is past the last panel`() {
        val slot = PanelSlotLayout(panelIndex = 1, x = 100, width = 28, collapsed = false)
        val layout = layout(boardWidth = 100, boardHeight = 40, slots = listOf(slot))

        assertNull(PanelScroll.slotAt(layout, x = 130, y = 10))
    }

    @Test
    fun `slotAt returns correct slot from multiple slots`() {
        val slot1 = PanelSlotLayout(panelIndex = 1, x = 100, width = 28, collapsed = false)
        val slot2 = PanelSlotLayout(panelIndex = 2, x = 128, width = 28, collapsed = false)
        val layout = layout(boardWidth = 100, boardHeight = 40, slots = listOf(slot1, slot2))

        assertEquals(slot2, PanelScroll.slotAt(layout, x = 128, y = 10))
        assertEquals(slot1, PanelScroll.slotAt(layout, x = 100, y = 10))
    }
}
