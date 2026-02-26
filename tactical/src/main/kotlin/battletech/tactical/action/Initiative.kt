package battletech.tactical.action

import kotlin.random.Random

public data class InitiativeResult(
    val rolls: Map<PlayerId, Int>,
    val loser: PlayerId,
    val winner: PlayerId,
)

public fun rollInitiative(random: Random = Random): InitiativeResult {
    while (true) {
        val roll1 = random.nextInt(1, 7) + random.nextInt(1, 7)
        val roll2 = random.nextInt(1, 7) + random.nextInt(1, 7)
        if (roll1 != roll2) {
            val (loser, winner) = if (roll1 < roll2) {
                PlayerId.PLAYER_1 to PlayerId.PLAYER_2
            } else {
                PlayerId.PLAYER_2 to PlayerId.PLAYER_1
            }
            return InitiativeResult(
                rolls = mapOf(PlayerId.PLAYER_1 to roll1, PlayerId.PLAYER_2 to roll2),
                loser = loser,
                winner = winner,
            )
        }
    }
}
