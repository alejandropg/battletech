package battletech.tactical.action

public data class MovementImpulse(
    val player: PlayerId,
    val unitCount: Int,
)

public fun calculateMovementOrder(
    loser: PlayerId,
    loserUnitCount: Int,
    winner: PlayerId,
    winnerUnitCount: Int,
): List<MovementImpulse> {
    if (loserUnitCount == 0 && winnerUnitCount == 0) return emptyList()
    if (loserUnitCount == 0) return listOf(MovementImpulse(winner, winnerUnitCount))
    if (winnerUnitCount == 0) return listOf(MovementImpulse(loser, loserUnitCount))

    val rounds = loserUnitCount
    val winnerBase = winnerUnitCount / rounds
    val winnerExtra = winnerUnitCount % rounds

    return buildList {
        for (round in 0 until rounds) {
            add(MovementImpulse(loser, 1))
            val winnerThisRound = winnerBase + if (round < winnerExtra) 1 else 0
            add(MovementImpulse(winner, winnerThisRound))
        }
    }
}
