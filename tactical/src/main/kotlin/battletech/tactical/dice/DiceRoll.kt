package battletech.tactical.dice

import kotlinx.serialization.Serializable

@Serializable
public data class DiceRoll(val d1: Int, val d2: Int) {
    val total: Int get() = d1 + d2
}
