package io.archinaut.battletech.tui.game

import io.archinaut.battletech.tactical.model.HexCoordinates
import io.archinaut.battletech.tactical.model.HexDirection

public data class FacingSelection(
    val hex: HexCoordinates,
    val facings: Set<HexDirection>,
)
