package battletech.tui.view.record

/**
 * Column-width budget for the maximized UNIT STATUS record sheet. [SHEET_WIDTH] is derived from
 * the widths below it, rather than restated as its own number, so the two can never drift apart:
 * it is exactly what the four-card top band needs once [tenter.view.Columns]' [GUTTER] is
 * included. Narrower terminals reflow cards to later bands rather than clipping them.
 */
internal object SheetLayout {
    /** [tenter.view.Columns]/[tenter.view.Stack] gutter used throughout the sheet. */
    const val GUTTER: Int = 2

    const val MECH_DATA_WIDTH: Int = 28
    const val WARRIOR_DATA_WIDTH: Int = 28
    const val WEAPON_INVENTORY_WIDTH: Int = 58

    // Wide enough for the widest single rung [HeatLadder] ever prints — two categories'
    // thresholds land on the same heat level only once (heat 15: "-3 MP, Ammo 4+", 15 chars) —
    // plus the marker+heat-number column beside it.
    const val HEAT_WIDTH: Int = 30

    const val MAIN_CONTENT_WIDTH: Int = MECH_DATA_WIDTH + GUTTER + WARRIOR_DATA_WIDTH + GUTTER + WEAPON_INVENTORY_WIDTH
    const val SHEET_WIDTH: Int = MAIN_CONTENT_WIDTH + GUTTER + HEAT_WIDTH

    const val ARMOR_DIAGRAM_WIDTH: Int = 58
    const val INTERNAL_STRUCTURE_DIAGRAM_WIDTH: Int = 58

    const val SYSTEM_DAMAGE_WIDTH: Int = 20

    /** Five lanes span the sheet's full width. */
    const val CRIT_COLUMN_WIDTH: Int = SHEET_WIDTH / 5
}
