package battletech.tui.view

/**
 * Pure arithmetic description of one rendered frame, derived solely from
 * terminal dimensions and visibility state — no UI, no rendering, no I/O.
 *
 * The tactical board fills the leftmost portion; side panels are placed to its
 * right in [slots] order (matching [Panels.ordered]). The status bar occupies
 * the rows above the board and panels, so both start at [boardY] rather than 0.
 *
 * @param boardWidth  columns available for the hex board
 * @param boardHeight rows available for the hex board
 * @param boardY      first screen row of the board and side panels; the status
 *                     bar occupies the rows above it
 * @param slots       visible panel slots, left-to-right, each with its screen-x
 *                    position and allocated width
 */
internal data class PanelSlotLayout(
    /** Matches [battletech.tui.game.PanelId.index] of the corresponding panel. */
    val panelIndex: Int,
    /** Left edge (column) of this panel in screen coordinates. */
    val x: Int,
    /** Allocated width in columns; never zero (hidden panels are absent from the list). */
    val width: Int,
    /** True when the user has collapsed this panel to a narrow stub. */
    val collapsed: Boolean,
)
