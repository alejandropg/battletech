package battletech.tactical.action

public data class Impulse(
    val player: PlayerId,
    val unitCount: Int,
)

public fun calculateMovementOrder(
    loser: PlayerId,
    loserUnitCount: Int,
    winner: PlayerId,
    winnerUnitCount: Int,
): List<Impulse> {
    if (loserUnitCount == 0 && winnerUnitCount == 0) return emptyList()
    if (loserUnitCount == 0) return listOf(Impulse(winner, winnerUnitCount))
    if (winnerUnitCount == 0) return listOf(Impulse(loser, loserUnitCount))

    val rounds = loserUnitCount
    val winnerBase = winnerUnitCount / rounds
    val winnerExtra = winnerUnitCount % rounds

    return buildList {
        for (round in 0 until rounds) {
            add(Impulse(loser, 1))
            val winnerThisRound = winnerBase + if (round < winnerExtra) 1 else 0
            add(Impulse(winner, winnerThisRound))
        }
    }
}
