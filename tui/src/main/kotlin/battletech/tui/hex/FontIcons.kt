package battletech.tui.hex

import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.unit.CriticalSlotContent
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
private val NF_MD_CHECKBOX_BLANK_CIRCLE_OUTLINE = String(Character.toChars(0xF0130))
private val NF_MD_MINUS_BOX_OUTLINE = String(Character.toChars(0xF06F2))
private val NF_MD_AMMUNITION = String(Character.toChars(0xF0CE8))
private val NF_FA_INFINITY = String(Character.toChars(0xEDFE))
private val NF_MD_IMAGE_BROKEN = String(Character.toChars(0xF02ED))
private val NF_FA_CHAIN_BROKEN = String(Character.toChars(0xF127))
private val NF_FA_BOMB = String(Character.toChars(0xF1E2))
private val NF_MD_RADIOACTIVE_CIRCLE = String(Character.toChars(0xF185D))
private val NF_MD_SYNC_CIRCLE = String(Character.toChars(0xF1378))
private val NF_MD_EYE_CIRCLE = String(Character.toChars(0xF0B94))
private val NF_MD_ACCOUNT_CIRCLE = String(Character.toChars(0xF0009))
private val NF_MD_SKULL = String(Character.toChars(0xF068C))

// Leg facing arrows (larger arrows)
private val NF_MD_ARROW_UP_BOLD_OUTLINE = String(Character.toChars(0xF09C7))
private val NF_MD_ARROW_TOP_RIGHT_BOLD_OUTLINE = String(Character.toChars(0xF09C5))
private val NF_MD_ARROW_BOTTOM_RIGHT_BOLD_OUTLINE = String(Character.toChars(0xF09B9))
private val NF_MD_ARROW_DOWN_BOLD_OUTLINE  = String(Character.toChars(0xF09BF))
private val NF_MD_ARROW_BOTTOM_LEFT_BOLD_OUTLINE = String(Character.toChars(0xF09B7))
private val NF_MD_ARROW_TOP_LEFT_BOLD_OUTLINE = String(Character.toChars(0xF09C3))

// Torso twist arrows (smaller arrows per design doc)
private val NF_MD_ARROW_UP  = String(Character.toChars(0xF005D))
private val NF_MD_ARROW_TOP_RIGHT = String(Character.toChars(0xF005C))
private val NF_MD_ARROW_BOTTOM_RIGHT = String(Character.toChars(0xF0043))
private val NF_MD_ARROW_DOWN  = String(Character.toChars(0xF0045))
private val NF_MD_ARROW_BOTTOM_LEFT = String(Character.toChars(0xF0042))
private val NF_MD_ARROW_TOP_LEFT = String(Character.toChars(0xF005B))

internal fun facingArrowIcon(direction: HexDirection): Pair<String, Int> = when (direction) {
    HexDirection.N  -> NF_MD_ARROW_UP_BOLD_OUTLINE  to 4
    HexDirection.NE -> NF_MD_ARROW_TOP_RIGHT_BOLD_OUTLINE to 5
    HexDirection.SE -> NF_MD_ARROW_BOTTOM_RIGHT_BOLD_OUTLINE to 5
    HexDirection.S  -> NF_MD_ARROW_DOWN_BOLD_OUTLINE  to 4
    HexDirection.SW -> NF_MD_ARROW_BOTTOM_LEFT_BOLD_OUTLINE to 3
    HexDirection.NW -> NF_MD_ARROW_TOP_LEFT_BOLD_OUTLINE to 3
}

internal fun torsoArrowIcon(direction: HexDirection): Pair<String, Int> = when (direction) {
    HexDirection.N  -> NF_MD_ARROW_UP  to 4
    HexDirection.NE -> NF_MD_ARROW_TOP_RIGHT to 5
    HexDirection.SE -> NF_MD_ARROW_BOTTOM_RIGHT to 5
    HexDirection.S  -> NF_MD_ARROW_DOWN  to 4
    HexDirection.SW -> NF_MD_ARROW_BOTTOM_LEFT to 3
    HexDirection.NW -> NF_MD_ARROW_TOP_LEFT to 3
}

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

/** Marker for a destroyed critical slot, with a distinct glyph for engine/gyro/sensor/life-support crits. */
internal fun criticalHitIcon(content: CriticalSlotContent): String =
    when (content) {
        is CriticalSlotContent.Engine -> NF_MD_RADIOACTIVE_CIRCLE
        is CriticalSlotContent.Gyro -> NF_MD_SYNC_CIRCLE
        is CriticalSlotContent.Sensors -> NF_MD_EYE_CIRCLE
        is CriticalSlotContent.LifeSupport -> NF_MD_ACCOUNT_CIRCLE
        else -> NF_MD_IMAGE_BROKEN
    }

/** Marker for a log line where a mech location was blown off. */
internal fun locationDestroyedIcon(): String = NF_FA_CHAIN_BROKEN

/** Marker for an ammo explosion log line. */
internal fun ammoExplosionIcon(): String = NF_FA_BOMB

/** Marker for a destroyed unit, rendered alongside its initial glyph. */
internal fun destroyedIcon(): String = NF_MD_SKULL

internal fun checkboxIcon(state: CheckState): String =
    when (state) {
        CheckState.UNCHECKED -> NF_MD_CHECKBOX_BLANK_OUTLINE
        CheckState.CHECKED -> NF_MD_CHECKBOX_MARKED_OUTLINE
        CheckState.INDETERMINATE -> NF_MD_MINUS_BOX_OUTLINE
    }

internal fun ammoIcon(): String = NF_MD_AMMUNITION

internal fun infinityIcon(): String = NF_FA_INFINITY

internal fun emptyCircleIcon(): String = NF_MD_CHECKBOX_BLANK_CIRCLE_OUTLINE

/** Filled circle (destroyed-slot indicator) — plain Unicode, paired visually with [emptyCircleIcon]. */
internal fun filledCircleIcon(): String = "●"
