package battletech.tui.view.record

/**
 * Column-width budget for the maximized UNIT STATUS record sheet. [SHEET_WIDTH] is the hard cap;
 * the four-card top band exactly fills it once [tenter.view.Columns]' two-column gutters are
 * included. Narrower terminals reflow cards to later bands rather than clipping them.
 */
internal object SheetLayout {
    const val SHEET_WIDTH: Int = 150

    const val MECH_DATA_WIDTH: Int = 28
    const val WARRIOR_DATA_WIDTH: Int = 28
    const val WEAPON_INVENTORY_WIDTH: Int = 58
    const val ARMOR_DIAGRAM_WIDTH: Int = 58
    const val INTERNAL_STRUCTURE_DIAGRAM_WIDTH: Int = 58

    // Wide enough for the widest single rung [HeatLadder] ever prints — two categories'
    // thresholds land on the same heat level only once (heat 15: "-3 MP, Ammo 4+", 15 chars) —
    // plus the marker+heat-number column beside it.
    const val HEAT_WIDTH: Int = 30

    const val CRITICAL_HIT_TABLE_WIDTH: Int = 118
    const val SYSTEM_DAMAGE_WIDTH: Int = 118
    const val CRIT_COLUMN_WIDTH: Int = 28

    const val MAIN_CONTENT_WIDTH: Int = 118
}
