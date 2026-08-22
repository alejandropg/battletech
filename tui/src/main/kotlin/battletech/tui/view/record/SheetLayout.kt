package battletech.tui.view.record

/**
 * Column-width budget for the maximized UNIT STATUS record sheet. [SHEET_WIDTH] is the hard cap
 * (175, including the panel's own inner padding); every card width below is chosen so the widest
 * band ('MECH DATA + DIAGRAM + HEAT, or the crit table's 4 location columns) stays under it with
 * its gutters. [tenter.view.Columns] reflows to fewer cards per row on a narrower terminal on its
 * own — these widths only need to fit the *widest* case.
 */
internal object SheetLayout {
    const val SHEET_WIDTH: Int = 175

    /** 'MECH DATA + WARRIOR DATA stack in one column of this width. */
    const val MECH_DATA_WIDTH: Int = 36

    /**
     * ARMOR DIAGRAM + INTERNAL STRUCTURE DIAGRAM stack in one column of this width — each a
     * 5-wide body silhouette (LA | LT | CT | RT | RA), so this needs to comfortably fit 5 pip
     * blocks side by side, not just the widest single label.
     */
    const val DIAGRAM_WIDTH: Int = 80

    // Wide enough for the widest single rung [HeatLadder] ever prints — two categories'
    // thresholds land on the same heat level only once (heat 15: "-3 MP, Ammo 4+", 15 chars) —
    // plus the marker+heat-number column beside it.
    const val HEAT_WIDTH: Int = 30

    const val CRIT_COLUMN_WIDTH: Int = 42

    /** Pips per row inside a [tenter.widget.PipTrack] location block on this sheet. */
    const val PIPS_PER_ROW: Int = 10
}
