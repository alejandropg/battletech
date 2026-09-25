package io.archinaut.battletech.tactical.model

import io.archinaut.battletech.tactical.unit.CombatUnit
import io.archinaut.battletech.tactical.unit.UnitRoster
import kotlinx.serialization.Serializable

@Serializable
public data class GameState(
    public val units: UnitRoster<CombatUnit>,
    public val map: GameMap,
)
