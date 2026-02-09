package battletech.tactical.action

public data class PhaseActionReport(
    val phase: TurnPhase,
    val unitId: UnitId,
    val actions: List<ActionOption>,
)
