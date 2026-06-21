package battletech.tui.view

import battletech.tui.hex.diceRoll

/** Right-aligned hit-chance label: needed 2d6 target roll + success probability, e.g. "<dice>7 58%". */
internal fun hitChanceLabel(targetDiceRoll: Int, successChance: Int): String =
    "${diceRoll()}$targetDiceRoll $successChance%"
