package battletech.tactical.session
import battletech.tactical.model.PlayerId
import kotlinx.serialization.Serializable

@Serializable
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

    val winnerBase = winnerUnitCount / loserUnitCount
    val winnerExtra = winnerUnitCount % loserUnitCount

    return buildList {
        for (round in 0 until loserUnitCount) {
            add(Impulse(loser, 1))
            val winnerThisRound = winnerBase + if (round < winnerExtra) 1 else 0
            // Omit rather than emit Impulse(winner, 0): when winner < loser, most
            // rounds have nothing for the winner to activate, and a zero-unit
            // impulse can never be consumed (advance only fires from an actual move).
            if (winnerThisRound > 0) add(Impulse(winner, winnerThisRound))
        }
    }
}

public fun calculateAttackOrder(
    loser: PlayerId,
    loserUnitCount: Int,
    winner: PlayerId,
    winnerUnitCount: Int,
): List<Impulse> = listOfNotNull(
    if (loserUnitCount > 0) Impulse(loser, loserUnitCount) else null,
    if (winnerUnitCount > 0) Impulse(winner, winnerUnitCount) else null,
)
