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
            panelKey = PanelId.UNIT_STATUS,
            delta = PanelScroll.STEP,
            currentOffset = 0,
            maxOffset = 20,
            anchorBottom = false,
        )
        assertEquals(mapOf(PanelId.UNIT_STATUS to PanelScroll.STEP), result)
    }

    @Test
    fun `top-anchored scrolling back to 0 removes entry`() {
        val result = PanelScroll.update(
            offsets = mapOf(PanelId.UNIT_STATUS to PanelScroll.STEP),
            panelKey = PanelId.UNIT_STATUS,
            delta = -PanelScroll.STEP,
            currentOffset = PanelScroll.STEP,
            maxOffset = 20,
            anchorBottom = false,
        )
        assertEquals(emptyMap<PanelId, Int>(), result)
    }

    @Test
    fun `top-anchored offset clamps at maxOffset`() {
        val result = PanelScroll.update(
            offsets = mapOf(PanelId.UNIT_STATUS to 18),
            panelKey = PanelId.UNIT_STATUS,
            delta = PanelScroll.STEP,
            currentOffset = 18,
            maxOffset = 18,
            anchorBottom = false,
        )
        // 18 + 2 would be 20, clamped to maxOffset=18 which IS the clamped value;
        // but 18 == maxOffset for top-anchored is NOT the anchor (anchor=0), so entry stays
        assertEquals(mapOf(PanelId.UNIT_STATUS to 18), result)
    }

    @Test
    fun `top-anchored large delta clamps at maxOffset`() {
        val result = PanelScroll.update(
            offsets = emptyMap(),
            panelKey = PanelId.DECLARED_TARGETS,
            delta = 999,
            currentOffset = 0,
            maxOffset = 10,
            anchorBottom = false,
        )
        assertEquals(mapOf(PanelId.DECLARED_TARGETS to 10), result)
    }

    // ── update: bottom-anchored (LOG) ────────────────────────────────────────

    @Test
    fun `bottom-anchored absent entry + wheelUp scrolls away from bottom`() {
        val result = PanelScroll.update(
            offsets = emptyMap(),
            panelKey = PanelId.ATTACK_RESULTS,
            delta = -PanelScroll.STEP,
            currentOffset = 20,
            maxOffset = 20,
            anchorBottom = true,
        )
        // effective = maxOffset=20; 20 + (-2) = 18 — not equal to anchor(20), keep entry
        assertEquals(mapOf(PanelId.ATTACK_RESULTS to 18), result)
    }

    @Test
    fun `bottom-anchored scrolling back to maxOffset removes entry (re-stick)`() {
        val result = PanelScroll.update(
            offsets = mapOf(PanelId.ATTACK_RESULTS to 18),
            panelKey = PanelId.ATTACK_RESULTS,
            delta = PanelScroll.STEP,
            currentOffset = 18,
            maxOffset = 20,
            anchorBottom = true,
        )
        // 18 + 2 = 20 == maxOffset == anchor → remove entry
        assertEquals(emptyMap<PanelId, Int>(), result)
    }

    @Test
    fun `bottom-anchored large negative delta clamps at 0`() {
        val result = PanelScroll.update(
            offsets = emptyMap(),
            panelKey = PanelId.ATTACK_RESULTS,
            delta = -999,
            currentOffset = 20,
            maxOffset = 20,
            anchorBottom = true,
        )
        assertEquals(mapOf(PanelId.ATTACK_RESULTS to 0), result)
    }

    // ── update: maxOffset = 0 ────────────────────────────────────────────────

    @Test
    fun `maxOffset 0 leaves other panel entries untouched`() {
        val existing = mapOf(PanelId.UNIT_STATUS to 5, PanelId.TARGETS to 8)
        val result = PanelScroll.update(
            offsets = existing,
            panelKey = PanelId.TARGETS,
            delta = PanelScroll.STEP,
            currentOffset = 8,
            maxOffset = 0,
            anchorBottom = false,
        )
        // TARGETS' stale entry is removed; UNIT_STATUS is unaffected
        assertEquals(mapOf(PanelId.UNIT_STATUS to 5), result)
    }

    @Test
    fun `maxOffset 0 removes stale entry for that panel`() {
        val result = PanelScroll.update(
            offsets = mapOf(PanelId.TARGETS to 5),
            panelKey = PanelId.TARGETS,
            delta = 0,
            currentOffset = 5,
            maxOffset = 0,
            anchorBottom = false,
        )
        assertEquals(emptyMap<PanelId, Int>(), result)
    }

    @Test
    fun `wheel delta is based on currentOffset, not the stale stored entry`() {
        // Simulates a panel that auto-followed away from its stored (absent = anchored-top)
        // entry: the map still has no entry for TARGETS, but the panel's true on-screen offset
        // (as reported back from the last render) is 15. A wheel-up tick must nudge from 15,
        // not from the stale anchor.
        val result = PanelScroll.update(
            offsets = emptyMap(),
            panelKey = PanelId.TARGETS,
            delta = -PanelScroll.STEP,
            currentOffset = 15,
            maxOffset = 30,
            anchorBottom = false,
        )
        assertEquals(mapOf(PanelId.TARGETS to 13), result)
    }

    // ── slotAt ───────────────────────────────────────────────────────────────

    private fun layout(
        boardWidth: Int,
        boardHeight: Int,
        slots: List<PanelSlotLayout>,
        boardY: Int = FrameLayout.STATUS_BAR_HEIGHT,
    ) = FrameLayout(boardWidth, boardHeight, boardY, slots)

    @Test
    fun `slotAt returns the matching expanded slot`() {
        val slot = PanelSlotLayout(panelKey = PanelId.UNIT_STATUS, x = 100, width = 28, collapsed = false)
        val layout = layout(boardWidth = 100, boardHeight = 40, slots = listOf(slot))

        assertEquals(slot, PanelScroll.slotAt(layout, x = 110, y = layout.boardY + 10))
    }

    @Test
    fun `slotAt returns null when x is in board area`() {
        val slot = PanelSlotLayout(panelKey = PanelId.UNIT_STATUS, x = 100, width = 28, collapsed = false)
        val layout = layout(boardWidth = 100, boardHeight = 40, slots = listOf(slot))

        assertNull(PanelScroll.slotAt(layout, x = 50, y = layout.boardY + 10))
    }

    @Test
    fun `slotAt returns null when y is above boardY (status bar)`() {
        val slot = PanelSlotLayout(panelKey = PanelId.UNIT_STATUS, x = 100, width = 28, collapsed = false)
        val layout = layout(boardWidth = 100, boardHeight = 40, slots = listOf(slot))

        assertNull(PanelScroll.slotAt(layout, x = 110, y = 0))
    }

    @Test
    fun `slotAt returns null when y is at or past boardY + boardHeight`() {
        val slot = PanelSlotLayout(panelKey = PanelId.UNIT_STATUS, x = 100, width = 28, collapsed = false)
        val layout = layout(boardWidth = 100, boardHeight = 40, slots = listOf(slot))

        assertNull(PanelScroll.slotAt(layout, x = 110, y = layout.boardY + 40))
    }

    @Test
    fun `slotAt returns null for collapsed slot`() {
        val slot = PanelSlotLayout(panelKey = PanelId.UNIT_STATUS, x = 100, width = 7, collapsed = true)
        val layout = layout(boardWidth = 100, boardHeight = 40, slots = listOf(slot))

        assertNull(PanelScroll.slotAt(layout, x = 103, y = layout.boardY + 10))
    }

    @Test
    fun `slotAt returns null when x is past the last panel`() {
        val slot = PanelSlotLayout(panelKey = PanelId.UNIT_STATUS, x = 100, width = 28, collapsed = false)
        val layout = layout(boardWidth = 100, boardHeight = 40, slots = listOf(slot))

        assertNull(PanelScroll.slotAt(layout, x = 130, y = layout.boardY + 10))
    }

    @Test
    fun `slotAt returns correct slot from multiple slots`() {
        val slot1 = PanelSlotLayout(panelKey = PanelId.UNIT_STATUS, x = 100, width = 28, collapsed = false)
        val slot2 = PanelSlotLayout(panelKey = PanelId.DECLARED_TARGETS, x = 128, width = 28, collapsed = false)
        val layout = layout(boardWidth = 100, boardHeight = 40, slots = listOf(slot1, slot2))

        assertEquals(slot2, PanelScroll.slotAt(layout, x = 128, y = layout.boardY + 10))
        assertEquals(slot1, PanelScroll.slotAt(layout, x = 100, y = layout.boardY + 10))
    }
}
