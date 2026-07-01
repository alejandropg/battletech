package battletech.tui.view

import battletech.tactical.dice.DiceRoll
import battletech.tui.hex.diceIcon

internal fun diceRollLabel(roll: DiceRoll): String =
    "${diceIcon(roll.d1)}+${diceIcon(roll.d2)}=${roll.total}"
