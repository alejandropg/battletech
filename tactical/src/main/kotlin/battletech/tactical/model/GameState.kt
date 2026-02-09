package battletech.tactical.model

public data class GameState(
    public val units: List<battletech.tactical.action.Unit>,
    public val map: GameMap,
)
