package battletech.tui.screen

import tenter.screen.ColorRole

/** Background colors for the current heat rung and the projected heating/cooling interval. */
internal enum class HeatScaleRole : ColorRole {
    CURRENT_BG,
    HEATING_BG,
    COOLING_BG,
}
