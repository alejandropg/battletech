package battletech.tui.view

import battletech.tui.game.PanelId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the pure frame-layout arithmetic extracted into [FrameLayout].
 *
 * All expected values are derived from the original [battletech.tui.TuiApp.renderFrame]
 * arithmetic: board fills leftover width, panels are placed left-to-right at x = boardWidth
 * then advance by each panel's allocated width.
 *
 * [PlacedPanel] equality compares the [Panel] it wraps by reference (it isn't a data class), so
 * expected [PlacedPanel]s below always reuse the exact [Panel] instance the input list carries —
 * never a freshly-built lookalike.
 *
 * Descriptor order matches [Panels.ordered]:
 *   [(4,28), (3,28), (2,28), (5,34), (1,28), (0,28)]
 *   TARGET_STATUS / TARGETS / DECLARED_TARGETS / ATTACK_RESULTS / UNIT_STATUS / LOG
 */
internal class FrameLayoutTest {

    private fun stub(id: PanelId, expandedWidth: Int, collapsedWidth: Int = FrameLayout.COLLAPSED_STUB_WIDTH) =
        Panel(id, "T", expandedWidth, collapsedWidth) { null }

    private val targetStatus = stub(PanelId.TARGET_STATUS, 28)
    private val targets = stub(PanelId.TARGETS, 28)
    private val declaredTargets = stub(PanelId.DECLARED_TARGETS, 28)
    private val attackResults = stub(PanelId.ATTACK_RESULTS, 34)
    private val unitStatus = stub(PanelId.UNIT_STATUS, 28)
    private val log = stub(PanelId.LOG, 28)

    private val allDescriptors: List<Panel> = listOf(targetStatus, targets, declaredTargets, attackResults, unitStatus, log)
    private val allKeys: Set<PanelId> = allDescriptors.map { it.id }.toSet()

    private val termWidth = 220
    private val termHeight = 50

    // boardHeight = termHeight - STATUS_BAR_HEIGHT = 50 - 4 = 46
    private val expectedBoardHeight = termHeight - FrameLayout.STATUS_BAR_HEIGHT

    @Test
    fun `all panels visible and none collapsed`() {
        // totalPanelWidth = 28+28+28+34+28+28 = 174 => boardWidth = 220-174 = 46
        val layout = FrameLayout.compute(
            termWidth = termWidth,
            termHeight = termHeight,
            visiblePanels = allKeys,
            collapsedPanels = emptySet(),
            panels = allDescriptors,
        )

        assertEquals(46, layout.boardWidth)
        assertEquals(expectedBoardHeight, layout.boardHeight)
        assertEquals(6, layout.slots.size)

        // Panels placed left-to-right starting at x = boardWidth
        val s = layout.slots
        assertEquals(PlacedPanel(targetStatus, x = 46,  width = 28, collapsed = false), s[0])
        assertEquals(PlacedPanel(targets, x = 74,  width = 28, collapsed = false), s[1])
        assertEquals(PlacedPanel(declaredTargets, x = 102, width = 28, collapsed = false), s[2])
        assertEquals(PlacedPanel(attackResults, x = 130, width = 34, collapsed = false), s[3])
        assertEquals(PlacedPanel(unitStatus, x = 164, width = 28, collapsed = false), s[4])
        assertEquals(PlacedPanel(log, x = 192, width = 28, collapsed = false), s[5])
    }

    @Test
    fun `one panel collapsed — stub width 7, board absorbs freed space`() {
        // LOG collapsed: its width becomes 7 instead of 28 (saves 21)
        // totalPanelWidth = 28+28+28+34+28+7 = 153 => boardWidth = 220-153 = 67
        val layout = FrameLayout.compute(
            termWidth = termWidth,
            termHeight = termHeight,
            visiblePanels = allKeys,
            collapsedPanels = setOf(PanelId.LOG),
            panels = allDescriptors,
        )

        assertEquals(67, layout.boardWidth)
        assertEquals(expectedBoardHeight, layout.boardHeight)
        assertEquals(6, layout.slots.size)

        // All preceding slots shift right by 21 (the space freed by the stub)
        val s = layout.slots
        assertEquals(PlacedPanel(targetStatus, x = 67,  width = 28, collapsed = false), s[0])
        assertEquals(PlacedPanel(targets, x = 95,  width = 28, collapsed = false), s[1])
        assertEquals(PlacedPanel(declaredTargets, x = 123, width = 28, collapsed = false), s[2])
        assertEquals(PlacedPanel(attackResults, x = 151, width = 34, collapsed = false), s[3])
        assertEquals(PlacedPanel(unitStatus, x = 185, width = 28, collapsed = false), s[4])
        assertEquals(PlacedPanel(log, x = 213, width =  7, collapsed = true),  s[5])
    }

    @Test
    fun `panels not in visiblePanels are absent from slots — board absorbs their width`() {
        // Movement phase: only UNIT_STATUS and LOG visible
        // totalPanelWidth = 28+28 = 56 => boardWidth = 220-56 = 164
        val movementVisible = setOf(PanelId.LOG, PanelId.UNIT_STATUS)

        val layout = FrameLayout.compute(
            termWidth = termWidth,
            termHeight = termHeight,
            visiblePanels = movementVisible,
            collapsedPanels = emptySet(),
            panels = allDescriptors,
        )

        assertEquals(164, layout.boardWidth)
        assertEquals(expectedBoardHeight, layout.boardHeight)
        assertEquals(2, layout.slots.size)

        // Only UNIT_STATUS and LOG in render order, which appear
        // in positions 4 and 5 of allDescriptors
        val s = layout.slots
        assertEquals(PlacedPanel(unitStatus, x = 164, width = 28, collapsed = false), s[0])
        assertEquals(PlacedPanel(log, x = 192, width = 28, collapsed = false), s[1])
    }

    @Test
    fun `board height and y-offset account for status bar height`() {
        val layout = FrameLayout.compute(
            termWidth = termWidth,
            termHeight = termHeight,
            visiblePanels = emptySet(),
            collapsedPanels = emptySet(),
            panels = allDescriptors,
        )

        assertEquals(termHeight - FrameLayout.STATUS_BAR_HEIGHT, layout.boardHeight)
        assertEquals(FrameLayout.STATUS_BAR_HEIGHT, layout.boardY)
        assertEquals(termWidth, layout.boardWidth) // no panels means full width goes to board
        assertEquals(0, layout.slots.size)
    }

    @Test
    fun `status bar height constant is 4 and collapsed stub width is 7`() {
        assertEquals(4, FrameLayout.STATUS_BAR_HEIGHT)
        assertEquals(7, FrameLayout.COLLAPSED_STUB_WIDTH)
    }

    @Test
    fun `a panel with collapsedWidth 0 disappears entirely when collapsed — no stub, board absorbs its width`() {
        // Mirrors HELP: it reports collapsedWidth = 0 instead of the stub width.
        val help = stub(PanelId.HELP, 28, collapsedWidth = 0)
        val descriptors = allDescriptors + help
        val visible = allKeys + PanelId.HELP

        val layout = FrameLayout.compute(
            termWidth = termWidth,
            termHeight = termHeight,
            visiblePanels = visible,
            collapsedPanels = setOf(PanelId.HELP),
            panels = descriptors,
        )

        // Same board width and slot count as if HELP were never visible at all.
        assertEquals(46, layout.boardWidth)
        assertEquals(6, layout.slots.size)
        assertTrue(layout.slots.none { it.id == PanelId.HELP })
    }

    @Test
    fun `a panel with collapsedWidth 0 renders normally when not collapsed`() {
        val help = stub(PanelId.HELP, 28, collapsedWidth = 0)
        val descriptors = allDescriptors + help
        val visible = allKeys + PanelId.HELP

        val layout = FrameLayout.compute(
            termWidth = termWidth,
            termHeight = termHeight,
            visiblePanels = visible,
            collapsedPanels = emptySet(),
            panels = descriptors,
        )

        assertEquals(7, layout.slots.size)
        val helpSlot = layout.slots.last()
        // boardWidth = 220 - (174 + 28) = 18; HELP is last, so its x is 18 + 174 (the six
        // preceding panels' combined width) = 192.
        assertEquals(PlacedPanel(help, x = 192, width = 28, collapsed = false), helpSlot)
    }

    // ── slotAt ───────────────────────────────────────────────────────────────

    private fun layout(
        boardWidth: Int,
        boardHeight: Int,
        slots: List<PlacedPanel>,
        boardY: Int = FrameLayout.STATUS_BAR_HEIGHT,
    ) = FrameLayout(boardWidth, boardHeight, boardY, slots)

    @Test
    fun `slotAt returns the matching expanded slot`() {
        val slot = PlacedPanel(unitStatus, x = 100, width = 28, collapsed = false)
        val layout = layout(boardWidth = 100, boardHeight = 40, slots = listOf(slot))

        assertEquals(slot, layout.slotAt(x = 110, y = layout.boardY + 10))
    }

    @Test
    fun `slotAt returns null when x is in board area`() {
        val slot = PlacedPanel(unitStatus, x = 100, width = 28, collapsed = false)
        val layout = layout(boardWidth = 100, boardHeight = 40, slots = listOf(slot))

        assertNull(layout.slotAt(x = 50, y = layout.boardY + 10))
    }

    @Test
    fun `slotAt returns null when y is above boardY (status bar)`() {
        val slot = PlacedPanel(unitStatus, x = 100, width = 28, collapsed = false)
        val layout = layout(boardWidth = 100, boardHeight = 40, slots = listOf(slot))

        assertNull(layout.slotAt(x = 110, y = 0))
    }

    @Test
    fun `slotAt returns null when y is at or past boardY + boardHeight`() {
        val slot = PlacedPanel(unitStatus, x = 100, width = 28, collapsed = false)
        val layout = layout(boardWidth = 100, boardHeight = 40, slots = listOf(slot))

        assertNull(layout.slotAt(x = 110, y = layout.boardY + 40))
    }

    @Test
    fun `slotAt returns null for collapsed slot`() {
        val slot = PlacedPanel(unitStatus, x = 100, width = 7, collapsed = true)
        val layout = layout(boardWidth = 100, boardHeight = 40, slots = listOf(slot))

        assertNull(layout.slotAt(x = 103, y = layout.boardY + 10))
    }

    @Test
    fun `slotAt returns null when x is past the last panel`() {
        val slot = PlacedPanel(unitStatus, x = 100, width = 28, collapsed = false)
        val layout = layout(boardWidth = 100, boardHeight = 40, slots = listOf(slot))

        assertNull(layout.slotAt(x = 130, y = layout.boardY + 10))
    }

    @Test
    fun `slotAt returns correct slot from multiple slots`() {
        val slot1 = PlacedPanel(unitStatus, x = 100, width = 28, collapsed = false)
        val slot2 = PlacedPanel(declaredTargets, x = 128, width = 28, collapsed = false)
        val layout = layout(boardWidth = 100, boardHeight = 40, slots = listOf(slot1, slot2))

        assertEquals(slot2, layout.slotAt(x = 128, y = layout.boardY + 10))
        assertEquals(slot1, layout.slotAt(x = 100, y = layout.boardY + 10))
    }
}
