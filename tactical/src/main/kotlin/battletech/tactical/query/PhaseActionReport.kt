package battletech.tactical.query
import battletech.tactical.model.TurnPhase
import battletech.tactical.model.UnitId

public data class PhaseActionReport(
    public val phase: TurnPhase,
    public val unitId: UnitId,
    public val actions: List<ActionOption>,
)
