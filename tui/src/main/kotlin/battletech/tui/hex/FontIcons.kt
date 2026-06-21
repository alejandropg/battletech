package battletech.tui.hex

import battletech.tactical.model.MovementMode
import battletech.tui.view.CheckState

// Nerd Fonts icons (https://www.nerdfonts.com/cheat-sheet)
private val NF_MD_DICE_1 = String(Character.toChars(0xF01CA))
private val NF_MD_DICE_2 = String(Character.toChars(0xF01CB))
private val NF_MD_DICE_3 = String(Character.toChars(0xF01CC))
private val NF_MD_DICE_4 = String(Character.toChars(0xF01CD))
private val NF_MD_DICE_5 = String(Character.toChars(0xF01CE))
private val NF_MD_DICE_6 = String(Character.toChars(0xF01CF))
private val NF_MD_WALK = String(Character.toChars(0xF0583))
private val NF_MD_RUN_FAST = String(Character.toChars(0xF046E))
private val NF_MD_ROCKET_LAUNCH = String(Character.toChars(0xF14DE))
private val NF_MD_TARGET = String(Character.toChars(0xF04FE))
private val NF_MD_DICE_MULTIPLE_OUTLINE = String(Character.toChars(0xF1156))
private val NF_MD_CHECKBOX_BLANK_OUTLINE = String(Character.toChars(0xF0131))
private val NF_MD_CHECKBOX_MARKED_OUTLINE = String(Character.toChars(0xF0135))
private val NF_MD_MINUS_BOX_OUTLINE = String(Character.toChars(0xF06F2))

internal fun diceIcon(value: Int): String =
    when (value) {
        1 -> NF_MD_DICE_1
        2 -> NF_MD_DICE_2
        3 -> NF_MD_DICE_3
        4 -> NF_MD_DICE_4
        5 -> NF_MD_DICE_5
        6 -> NF_MD_DICE_6
        else -> error("Value must be from 1 to 6")
    }

internal fun diceRoll(): String = NF_MD_DICE_MULTIPLE_OUTLINE

internal fun movementModeIcon(mode: MovementMode): String =
    when (mode) {
        MovementMode.WALK -> NF_MD_WALK
        MovementMode.RUN -> NF_MD_RUN_FAST
        MovementMode.JUMP -> NF_MD_ROCKET_LAUNCH
    }

internal fun targetIcon(): String = NF_MD_TARGET

internal fun checkboxIcon(state: CheckState): String =
    when (state) {
        CheckState.UNCHECKED -> NF_MD_CHECKBOX_BLANK_OUTLINE
        CheckState.CHECKED -> NF_MD_CHECKBOX_MARKED_OUTLINE
        CheckState.INDETERMINATE -> NF_MD_MINUS_BOX_OUTLINE
    }
