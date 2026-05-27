package battletech.tui.hex

import battletech.tactical.movement.MovementMode

// nf-md-dice_1 through nf-md-dice_6
internal fun diceIcon(value: Int): String =
    when (value) {
        1 -> "\uDB80\uDDCA" // 0xF01CA
        2 -> "\uDB80\uDDCB" // 0xF01CB
        3 -> "\uDB80\uDDCC" // 0xF01CC
        4 -> "\uDB80\uDDCD" // 0xF01CD
        5 -> "\uDB80\uDDCE" // 0xF01CE
        6 -> "\uDB80\uDDCF" // 0xF01CF
        else -> error("Value must be from 1 to 6")
    }

// nf-md-walk, nf-md-run-fast, nf-md-rocket-launch
internal fun movementModeIcon(mode: MovementMode): String = when (mode) {
    MovementMode.WALK -> String(Character.toChars(0xF0583))
    MovementMode.RUN  -> String(Character.toChars(0xF046E))
    MovementMode.JUMP -> String(Character.toChars(0xF14DE))
}

internal fun targetIcon(): String = String(Character.toChars(0xF04FE))
