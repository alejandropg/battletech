package battletech.tui.hex

import battletech.tactical.movement.MovementMode

// nf-md-walk, nf-md-run-fast, nf-md-rocket-launch
internal fun movementModeIcon(mode: MovementMode): String = when (mode) {
    MovementMode.WALK -> String(Character.toChars(0xF0583))
    MovementMode.RUN  -> String(Character.toChars(0xF046E))
    MovementMode.JUMP -> String(Character.toChars(0xF14DE))
}
