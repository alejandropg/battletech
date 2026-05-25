package battletech.tui.view

// nf-md-dice_1 through nf-md-dice_6
internal fun diceIcon(value: Int): String = String(Character.toChars(0xF1466 + value))
