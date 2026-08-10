package battletech.tui.game

import battletech.tui.view.FrameLayout
import battletech.tui.view.PanelSlotLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class PanelScrollTest {

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    fun `absent entry + wheelDown delta adds entry`() {
        val result = PanelScroll.update(
            offsets = emptyMap(),
            panelKey = PanelId.UNIT_STATUS,
            delta = PanelScroll.STEP,
            currentOffset = 0,
            maxOffset = 20,
        )
        assertEquals(mapOf(PanelId.UNIT_STATUS to PanelScroll.STEP), result)
    }

    @Test
    fun `scrolling back to 0 removes entry`() {
        val result = PanelScroll.update(
            offsets = mapOf(PanelId.UNIT_STATUS to PanelScroll.STEP),
            panelKey = PanelId.UNIT_STATUS,
            delta = -PanelScroll.STEP,
            currentOffset = PanelScroll.STEP,
            maxOffset = 20,
        )
        assertEquals(emptyMap<PanelId, Int>(), result)
    }

    @Test
    fun `offset clamps at maxOffset`() {
        val result = PanelScroll.update(
            offsets = mapOf(PanelId.UNIT_STATUS to 18),
            panelKey = PanelId.UNIT_STATUS,
            delta = PanelScroll.STEP,
            currentOffset = 18,
            maxOffset = 18,
        )
        // 18 + 2 would be 20, clamped to maxOffset=18 which IS the clamped value;
        // but 18 == maxOffset is NOT the anchor (anchor=0), so entry stays
        assertEquals(mapOf(PanelId.UNIT_STATUS to 18), result)
    }

    @Test
    fun `large delta clamps at maxOffset`() {
        val result = PanelScroll.update(
            offsets = emptyMap(),
            panelKey = PanelId.DECLARED_TARGETS,
            delta = 999,
            currentOffset = 0,
            maxOffset = 10,
        )
        assertEquals(mapOf(PanelId.DECLARED_TARGETS to 10), result)
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
