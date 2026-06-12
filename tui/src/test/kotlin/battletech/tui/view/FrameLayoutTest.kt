package battletech.tui.view

import org.junit.jupiter.api.Assertions.assertEquals
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

    private val allDescriptors: List<Pair<Int, Int>> = listOf(
        4 to 28,  // TARGET_STATUS
        3 to 28,  // TARGETS
        2 to 28,  // DECLARED_TARGETS
        5 to 34,  // ATTACK_RESULTS
        1 to 28,  // UNIT_STATUS
        0 to 28,  // LOG
    )
    private val allIndices: Set<Int> = allDescriptors.map { it.first }.toSet()

    private val termWidth = 220
    private val termHeight = 50

    // boardHeight = termHeight - STATUS_BAR_HEIGHT = 50 - 7 = 43
    private val expectedBoardHeight = termHeight - FrameLayout.STATUS_BAR_HEIGHT

    @Test
    fun `all panels visible and none collapsed`() {
        // totalPanelWidth = 28+28+28+34+28+28 = 174 => boardWidth = 220-174 = 46
        val layout = FrameLayout.compute(
            termWidth = termWidth,
            termHeight = termHeight,
            visiblePanels = allIndices,
            collapsedPanels = emptySet(),
            panelDescriptors = allDescriptors,
        )

        assertEquals(46, layout.boardWidth)
        assertEquals(expectedBoardHeight, layout.boardHeight)
        assertEquals(6, layout.slots.size)

        // Panels placed left-to-right starting at x = boardWidth
        val s = layout.slots
        assertEquals(PanelSlotLayout(panelIndex = 4, x = 46,  width = 28, collapsed = false), s[0]) // TARGET_STATUS
        assertEquals(PanelSlotLayout(panelIndex = 3, x = 74,  width = 28, collapsed = false), s[1]) // TARGETS
        assertEquals(PanelSlotLayout(panelIndex = 2, x = 102, width = 28, collapsed = false), s[2]) // DECLARED_TARGETS
        assertEquals(PanelSlotLayout(panelIndex = 5, x = 130, width = 34, collapsed = false), s[3]) // ATTACK_RESULTS
        assertEquals(PanelSlotLayout(panelIndex = 1, x = 164, width = 28, collapsed = false), s[4]) // UNIT_STATUS
        assertEquals(PanelSlotLayout(panelIndex = 0, x = 192, width = 28, collapsed = false), s[5]) // LOG
    }

    @Test
    fun `one panel collapsed — stub width 7, board absorbs freed space`() {
        // LOG (index=0) collapsed: its width becomes 7 instead of 28 (saves 21)
        // totalPanelWidth = 28+28+28+34+28+7 = 153 => boardWidth = 220-153 = 67
        val layout = FrameLayout.compute(
            termWidth = termWidth,
            termHeight = termHeight,
            visiblePanels = allIndices,
            collapsedPanels = setOf(0),
            panelDescriptors = allDescriptors,
        )

        assertEquals(67, layout.boardWidth)
        assertEquals(expectedBoardHeight, layout.boardHeight)
        assertEquals(6, layout.slots.size)

        // All preceding slots shift right by 21 (the space freed by the stub)
        val s = layout.slots
        assertEquals(PanelSlotLayout(panelIndex = 4, x = 67,  width = 28, collapsed = false), s[0])
        assertEquals(PanelSlotLayout(panelIndex = 3, x = 95,  width = 28, collapsed = false), s[1])
        assertEquals(PanelSlotLayout(panelIndex = 2, x = 123, width = 28, collapsed = false), s[2])
        assertEquals(PanelSlotLayout(panelIndex = 5, x = 151, width = 34, collapsed = false), s[3])
        assertEquals(PanelSlotLayout(panelIndex = 1, x = 185, width = 28, collapsed = false), s[4])
        assertEquals(PanelSlotLayout(panelIndex = 0, x = 213, width =  7, collapsed = true),  s[5])
    }

    @Test
    fun `hidden panels absent from slots — board absorbs their width`() {
        // Movement phase: only UNIT_STATUS (1) and LOG (0) visible
        // totalPanelWidth = 28+28 = 56 => boardWidth = 220-56 = 164
        val movementVisible = setOf(0, 1)

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

        // Only UNIT_STATUS and LOG in render order (indices 1 and 0, which appear
        // in positions 4 and 5 of allDescriptors)
        val s = layout.slots
        assertEquals(PanelSlotLayout(panelIndex = 1, x = 164, width = 28, collapsed = false), s[0])
        assertEquals(PanelSlotLayout(panelIndex = 0, x = 192, width = 28, collapsed = false), s[1])
    }

    @Test
    fun `board height accounts for status bar height`() {
        val layout = FrameLayout.compute(
            termWidth = termWidth,
            termHeight = termHeight,
            visiblePanels = emptySet(),
            collapsedPanels = emptySet(),
            panelDescriptors = allDescriptors,
        )

        assertEquals(termHeight - FrameLayout.STATUS_BAR_HEIGHT, layout.boardHeight)
        assertEquals(termWidth, layout.boardWidth) // no panels means full width goes to board
        assertEquals(0, layout.slots.size)
    }

    @Test
    fun `status bar height constant is 7 and collapsed stub width is 7`() {
        assertEquals(7, FrameLayout.STATUS_BAR_HEIGHT)
        assertEquals(7, FrameLayout.COLLAPSED_STUB_WIDTH)
    }
}
