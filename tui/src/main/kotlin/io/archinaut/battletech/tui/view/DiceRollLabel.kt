package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tactical.dice.DiceRoll
import io.archinaut.battletech.tui.icon.diceIcon

internal fun diceRollLabel(roll: DiceRoll): String =
    "${diceIcon(roll.d1)}+${diceIcon(roll.d2)}=${roll.total}"
