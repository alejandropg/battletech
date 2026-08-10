package battletech.tui.view

import battletech.tui.game.PanelId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the pure frame-layout arithmetic extracted into [FrameLayout].
 *
 * All expected values are derived from the original [battletech.tui.TuiApp.renderFrame]
 * arithmetic: board fills leftover width, panels are placed left-to-right at x = boardWidth
 * then advance by each panel's allocated width.
 *
 * Descriptor order matches [Panels.ordered]:
 *   [(4,28), (3,28), (2,28), (5,34), (1,28), (0,28)]
 *   TARGET_STATUS / TARGETS / DECLARED_TARGETS / ATTACK_RESULTS / UNIT_STATUS / LOG
 */
internal class FrameLayoutTest {

    private fun stub(key: PanelId, expandedWidth: Int, collapsedWidth: Int = FrameLayout.COLLAPSED_STUB_WIDTH) =
        PanelMetrics(key, expandedWidth, collapsedWidth)

    private val allDescriptors: List<PanelMetrics> = listOf(
        stub(PanelId.TARGET_STATUS, 28),
        stub(PanelId.TARGETS, 28),
        stub(PanelId.DECLARED_TARGETS, 28),
        stub(PanelId.ATTACK_RESULTS, 34),
        stub(PanelId.UNIT_STATUS, 28),
        stub(PanelId.LOG, 28),
    )
    private val allKeys: Set<PanelId> = allDescriptors.map { it.key }.toSet()

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
            panelDescriptors = allDescriptors,
        )

        assertEquals(46, layout.boardWidth)
        assertEquals(expectedBoardHeight, layout.boardHeight)
        assertEquals(6, layout.slots.size)

        // Panels placed left-to-right starting at x = boardWidth
        val s = layout.slots
        assertEquals(PanelSlotLayout(panelKey = PanelId.TARGET_STATUS, x = 46,  width = 28, collapsed = false), s[0])
        assertEquals(PanelSlotLayout(panelKey = PanelId.TARGETS, x = 74,  width = 28, collapsed = false), s[1])
        assertEquals(PanelSlotLayout(panelKey = PanelId.DECLARED_TARGETS, x = 102, width = 28, collapsed = false), s[2])
        assertEquals(PanelSlotLayout(panelKey = PanelId.ATTACK_RESULTS, x = 130, width = 34, collapsed = false), s[3])
        assertEquals(PanelSlotLayout(panelKey = PanelId.UNIT_STATUS, x = 164, width = 28, collapsed = false), s[4])
        assertEquals(PanelSlotLayout(panelKey = PanelId.LOG, x = 192, width = 28, collapsed = false), s[5])
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
            panelDescriptors = allDescriptors,
        )

        assertEquals(67, layout.boardWidth)
        assertEquals(expectedBoardHeight, layout.boardHeight)
        assertEquals(6, layout.slots.size)

        // All preceding slots shift right by 21 (the space freed by the stub)
        val s = layout.slots
        assertEquals(PanelSlotLayout(panelKey = PanelId.TARGET_STATUS, x = 67,  width = 28, collapsed = false), s[0])
        assertEquals(PanelSlotLayout(panelKey = PanelId.TARGETS, x = 95,  width = 28, collapsed = false), s[1])
        assertEquals(PanelSlotLayout(panelKey = PanelId.DECLARED_TARGETS, x = 123, width = 28, collapsed = false), s[2])
        assertEquals(PanelSlotLayout(panelKey = PanelId.ATTACK_RESULTS, x = 151, width = 34, collapsed = false), s[3])
        assertEquals(PanelSlotLayout(panelKey = PanelId.UNIT_STATUS, x = 185, width = 28, collapsed = false), s[4])
        assertEquals(PanelSlotLayout(panelKey = PanelId.LOG, x = 213, width =  7, collapsed = true),  s[5])
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
            panelDescriptors = allDescriptors,
        )

        assertEquals(164, layout.boardWidth)
        assertEquals(expectedBoardHeight, layout.boardHeight)
        assertEquals(2, layout.slots.size)

        // Only UNIT_STATUS and LOG in render order, which appear
        // in positions 4 and 5 of allDescriptors
        val s = layout.slots
        assertEquals(PanelSlotLayout(panelKey = PanelId.UNIT_STATUS, x = 164, width = 28, collapsed = false), s[0])
        assertEquals(PanelSlotLayout(panelKey = PanelId.LOG, x = 192, width = 28, collapsed = false), s[1])
    }

    @Test
    fun `board height and y-offset account for status bar height`() {
        val layout = FrameLayout.compute(
            termWidth = termWidth,
            termHeight = termHeight,
            visiblePanels = emptySet(),
            collapsedPanels = emptySet(),
            panelDescriptors = allDescriptors,
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
        val descriptors = allDescriptors + stub(PanelId.HELP, 28, collapsedWidth = 0)
        val visible = allKeys + PanelId.HELP

        val layout = FrameLayout.compute(
            termWidth = termWidth,
            termHeight = termHeight,
            visiblePanels = visible,
            collapsedPanels = setOf(PanelId.HELP),
            panelDescriptors = descriptors,
        )

        // Same board width and slot count as if HELP were never visible at all.
        assertEquals(46, layout.boardWidth)
        assertEquals(6, layout.slots.size)
        assertTrue(layout.slots.none { it.panelKey == PanelId.HELP })
    }

    @Test
    fun `a panel with collapsedWidth 0 renders normally when not collapsed`() {
        val descriptors = allDescriptors + stub(PanelId.HELP, 28, collapsedWidth = 0)
        val visible = allKeys + PanelId.HELP

        val layout = FrameLayout.compute(
            termWidth = termWidth,
            termHeight = termHeight,
            visiblePanels = visible,
            collapsedPanels = emptySet(),
            panelDescriptors = descriptors,
        )

        assertEquals(7, layout.slots.size)
        val helpSlot = layout.slots.last()
        // boardWidth = 220 - (174 + 28) = 18; HELP is last, so its x is 18 + 174 (the six
        // preceding panels' combined width) = 192.
        assertEquals(PanelSlotLayout(panelKey = PanelId.HELP, x = 192, width = 28, collapsed = false), helpSlot)
    }
}
