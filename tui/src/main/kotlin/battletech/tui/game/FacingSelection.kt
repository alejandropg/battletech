package battletech.tui.game

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection

public data class FacingSelection(
    val hex: HexCoordinates,
    val facings: Set<HexDirection>,
)
