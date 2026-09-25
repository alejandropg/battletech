package io.archinaut.battletech.tactical.session

import io.archinaut.battletech.tactical.dice.DiceRoll
import io.archinaut.battletech.tactical.dice.DiceRoller
import io.archinaut.battletech.tactical.dice.RandomDiceRoller
import io.archinaut.battletech.tactical.model.PlayerId
import kotlinx.serialization.Serializable

@Serializable
public data class Initiative(
    val rolls: Map<PlayerId, DiceRoll>,
    val loser: PlayerId,
    val winner: PlayerId,
)

public fun rollInitiative(roller: DiceRoller = RandomDiceRoller()): Initiative {
    while (true) {
        val roll1 = roller.roll2d6()
        val roll2 = roller.roll2d6()
        if (roll1.total == roll2.total) continue

        val (loser, winner) = if (roll1.total < roll2.total) {
            PlayerId.PLAYER_1 to PlayerId.PLAYER_2
        } else {
            PlayerId.PLAYER_2 to PlayerId.PLAYER_1
        }
        return Initiative(
            rolls = mapOf(PlayerId.PLAYER_1 to roll1, PlayerId.PLAYER_2 to roll2),
            loser = loser,
            winner = winner,
        )
    }
}
