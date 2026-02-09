package battletech.tactical.action

public data class PhaseActionReport(
    public val phase: TurnPhase,
    public val unitId: UnitId,
    public val actions: List<ActionOption>,
)
