package battletech.tui.icon

import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.model.Terrain
import battletech.tactical.unit.CriticalSlotContent

// Nerd Fonts icons (https://www.nerdfonts.com/cheat-sheet)
private val NF_MD_DICE_1: String = String(Character.toChars(0xF01CA))
private val NF_MD_DICE_2: String = String(Character.toChars(0xF01CB))
private val NF_MD_DICE_3: String = String(Character.toChars(0xF01CC))
private val NF_MD_DICE_4: String = String(Character.toChars(0xF01CD))
private val NF_MD_DICE_5: String = String(Character.toChars(0xF01CE))
private val NF_MD_DICE_6: String = String(Character.toChars(0xF01CF))
private val NF_MD_WALK: String = String(Character.toChars(0xF0583))
private val NF_MD_RUN_FAST: String = String(Character.toChars(0xF046E))
private val NF_MD_ROCKET_LAUNCH: String = String(Character.toChars(0xF14DE))
private val NF_MD_TARGET: String = String(Character.toChars(0xF04FE))
private val NF_MD_BULLSEYE_ARROW: String = String(Character.toChars(0xF08C9))
private val NF_MD_CROSSHAIRS_OFF: String = String(Character.toChars(0xF0F45))
private val NF_MD_DICE_MULTIPLE_OUTLINE: String = String(Character.toChars(0xF1156))
private val NF_MD_CHECKBOX_BLANK_CIRCLE_OUTLINE: String = String(Character.toChars(0xF0130))
private val NF_MD_AMMUNITION: String = String(Character.toChars(0xF0CE8))
private val NF_FA_INFINITY: String = String(Character.toChars(0xEDFE))
private val NF_MD_IMAGE_BROKEN: String = String(Character.toChars(0xF02ED))
private val NF_FA_CHAIN_BROKEN: String = String(Character.toChars(0xF127))
private val NF_FA_BOMB: String = String(Character.toChars(0xF1E2))
private val NF_MD_RADIOACTIVE_CIRCLE: String = String(Character.toChars(0xF185D))
private val NF_MD_SYNC_CIRCLE: String = String(Character.toChars(0xF1378))
private val NF_MD_EYE_CIRCLE: String = String(Character.toChars(0xF0B94))
private val NF_MD_ACCOUNT_CIRCLE: String = String(Character.toChars(0xF0009))
private val NF_MD_SKULL: String = String(Character.toChars(0xF068C))
private val NF_MD_ROBOT_DEAD: String = String(Character.toChars(0xF16A1))
private val NF_MD_LAN_CONNECT: String = String(Character.toChars(0xF0318))
private val NF_MD_MAP: String = String(Character.toChars(0xF034D))
private val NF_MD_ALERT: String = String(Character.toChars(0xF0026))
private val NF_MD_TRANSFER_DOWN: String = String(Character.toChars(0xF0DA1))
private val NF_MD_TRANSFER_UP: String = String(Character.toChars(0xF0DA3))
private val NF_MD_ACCOUNT_ALERT: String = String(Character.toChars(0xF0005))
private val NF_MD_SLEEP: String = String(Character.toChars(0xF04B2))
private val NF_MD_SLEEP_OFF: String = String(Character.toChars(0xF04B3))
private val NF_MD_THERMOMETER_CHEVRON_DOWN: String = String(Character.toChars(0xF0E02))
private val NF_MD_THERMOMETER_CHEVRON_UP: String = String(Character.toChars(0xF0E03))
private val NF_MD_POWER: String = String(Character.toChars(0xF0425))
private val NF_MD_RESTART: String = String(Character.toChars(0xF0709))
private val NF_MD_TROPHY: String = String(Character.toChars(0xF0538))
private val NF_MD_PISTOL: String = String(Character.toChars(0xF0703))
private val NF_MD_BOXING_GLOVE: String = String(Character.toChars(0xF0B65))

// Terrain icons (nf-md-tree_outline, nf-md-tree and another Nerd Fonts icons are above U+FFFF, need surrogate pairs)
private val NF_MD_TREE_OUTLINE: String = String(Character.toChars(0xF0E69))
private val NF_MD_PINE_TREE: String = String(Character.toChars(0xF0531))
private val NF_MD_WAVES: String = String(Character.toChars(0xF078D))
private val NF_MD_GRAIN: String = String(Character.toChars(0xF0D7C))

// Numeric badge icons (nf-md-numeric_N_box_multiple)
private val NF_MD_NUMERIC_1_BOX_MULTIPLE: String = String(Character.toChars(0xF0F0F))
private val NF_MD_NUMERIC_2_BOX_MULTIPLE: String = String(Character.toChars(0xF0F10))
private val NF_MD_NUMERIC_3_BOX_MULTIPLE: String = String(Character.toChars(0xF0F11))
private val NF_MD_NUMERIC_4_BOX_MULTIPLE: String = String(Character.toChars(0xF0F12))
private val NF_MD_NUMERIC_5_BOX_MULTIPLE: String = String(Character.toChars(0xF0F13))
private val NF_MD_NUMERIC_6_BOX_MULTIPLE: String = String(Character.toChars(0xF0F14))
private val NF_MD_NUMERIC_7_BOX_MULTIPLE: String = String(Character.toChars(0xF0F15))
private val NF_MD_NUMERIC_8_BOX_MULTIPLE: String = String(Character.toChars(0xF0F16))
private val NF_MD_NUMERIC_9_BOX_MULTIPLE: String = String(Character.toChars(0xF0F17))

// Numeric badge outline icons (nf-md-numeric_N_box_multiple_outline)
private val NF_MD_NUMERIC_1_BOX_MULTIPLE_OUTLINE: String = String(Character.toChars(0xF03A5))
private val NF_MD_NUMERIC_2_BOX_MULTIPLE_OUTLINE: String = String(Character.toChars(0xF03A8))
private val NF_MD_NUMERIC_3_BOX_MULTIPLE_OUTLINE: String = String(Character.toChars(0xF03AB))
private val NF_MD_NUMERIC_4_BOX_MULTIPLE_OUTLINE: String = String(Character.toChars(0xF03B2))
private val NF_MD_NUMERIC_5_BOX_MULTIPLE_OUTLINE: String = String(Character.toChars(0xF03AF))
private val NF_MD_NUMERIC_6_BOX_MULTIPLE_OUTLINE: String = String(Character.toChars(0xF03B4))
private val NF_MD_NUMERIC_7_BOX_MULTIPLE_OUTLINE: String = String(Character.toChars(0xF03B7))
private val NF_MD_NUMERIC_8_BOX_MULTIPLE_OUTLINE: String = String(Character.toChars(0xF03BA))
private val NF_MD_NUMERIC_9_BOX_MULTIPLE_OUTLINE: String = String(Character.toChars(0xF03BD))

// Thin arrow icons
private val NF_MD_ARROW_UP_THIN_N: String = String(Character.toChars(0xF09C7))
private val NF_MD_ARROW_UP_THIN_NE: String = String(Character.toChars(0xF09C5))
private val NF_MD_ARROW_UP_THIN_SE: String = String(Character.toChars(0xF09B9))
private val NF_MD_ARROW_UP_THIN_S: String = String(Character.toChars(0xF09BF))
private val NF_MD_ARROW_UP_THIN_SW: String = String(Character.toChars(0xF09B7))
private val NF_MD_ARROW_UP_THIN_NW: String = String(Character.toChars(0xF09C3))

// Bold outline arrow icons (larger arrows)
private val NF_MD_ARROW_UP_BOLD_OUTLINE: String = String(Character.toChars(0xF09C7))
private val NF_MD_ARROW_TOP_RIGHT_BOLD_OUTLINE: String = String(Character.toChars(0xF09C5))
private val NF_MD_ARROW_BOTTOM_RIGHT_BOLD_OUTLINE: String = String(Character.toChars(0xF09B9))
private val NF_MD_ARROW_DOWN_BOLD_OUTLINE: String = String(Character.toChars(0xF09BF))
private val NF_MD_ARROW_BOTTOM_LEFT_BOLD_OUTLINE: String = String(Character.toChars(0xF09B7))
private val NF_MD_ARROW_TOP_LEFT_BOLD_OUTLINE: String = String(Character.toChars(0xF09C3))

// Plain arrow icons (smaller arrows)
private val NF_MD_ARROW_UP: String = String(Character.toChars(0xF005D))
private val NF_MD_ARROW_TOP_RIGHT: String = String(Character.toChars(0xF005C))
private val NF_MD_ARROW_BOTTOM_RIGHT: String = String(Character.toChars(0xF0043))
private val NF_MD_ARROW_DOWN: String = String(Character.toChars(0xF0045))
private val NF_MD_ARROW_BOTTOM_LEFT: String = String(Character.toChars(0xF0042))
private val NF_MD_ARROW_TOP_LEFT: String = String(Character.toChars(0xF005B))

internal fun terrainIcon(terrain: Terrain): String = when (terrain) {
    Terrain.CLEAR       -> ""
    Terrain.LIGHT_WOODS -> NF_MD_TREE_OUTLINE
    Terrain.HEAVY_WOODS -> NF_MD_PINE_TREE
    Terrain.WATER       -> NF_MD_WAVES
    Terrain.ROUGH       -> NF_MD_GRAIN
}

internal fun elevationIcon(elevation: Int): String = when (elevation) {
    1 -> NF_MD_NUMERIC_1_BOX_MULTIPLE
    2 -> NF_MD_NUMERIC_2_BOX_MULTIPLE
    3 -> NF_MD_NUMERIC_3_BOX_MULTIPLE
    4 -> NF_MD_NUMERIC_4_BOX_MULTIPLE
    5 -> NF_MD_NUMERIC_5_BOX_MULTIPLE
    6 -> NF_MD_NUMERIC_6_BOX_MULTIPLE
    7 -> NF_MD_NUMERIC_7_BOX_MULTIPLE
    8 -> NF_MD_NUMERIC_8_BOX_MULTIPLE
    9 -> NF_MD_NUMERIC_9_BOX_MULTIPLE
    else -> error("No elevation icon for elevation: $elevation")
}

internal fun depthIcon(depth: Int): String = when (depth) {
    1 -> NF_MD_NUMERIC_1_BOX_MULTIPLE_OUTLINE
    2 -> NF_MD_NUMERIC_2_BOX_MULTIPLE_OUTLINE
    3 -> NF_MD_NUMERIC_3_BOX_MULTIPLE_OUTLINE
    4 -> NF_MD_NUMERIC_4_BOX_MULTIPLE_OUTLINE
    5 -> NF_MD_NUMERIC_5_BOX_MULTIPLE_OUTLINE
    6 -> NF_MD_NUMERIC_6_BOX_MULTIPLE_OUTLINE
    7 -> NF_MD_NUMERIC_7_BOX_MULTIPLE_OUTLINE
    8 -> NF_MD_NUMERIC_8_BOX_MULTIPLE_OUTLINE
    9 -> NF_MD_NUMERIC_9_BOX_MULTIPLE_OUTLINE
    else -> error("No depth icon for depth: $depth")
}

internal fun facingIcon(direction: HexDirection): String = when (direction) {
    HexDirection.N  -> NF_MD_ARROW_UP_THIN_N
    HexDirection.NE -> NF_MD_ARROW_UP_THIN_NE
    HexDirection.SE -> NF_MD_ARROW_UP_THIN_SE
    HexDirection.S  -> NF_MD_ARROW_UP_THIN_S
    HexDirection.SW -> NF_MD_ARROW_UP_THIN_SW
    HexDirection.NW -> NF_MD_ARROW_UP_THIN_NW
}

// Key mapping: q=NW, w=N, e=NE, a=SW, s=S, d=SE
internal fun facingKey(direction: HexDirection): String = when (direction) {
    HexDirection.NW -> "q"
    HexDirection.N  -> "w"
    HexDirection.NE -> "e"
    HexDirection.SW -> "a"
    HexDirection.S  -> "s"
    HexDirection.SE -> "d"
}

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

internal fun attackOutcomeIcon(hit: Boolean): String =
    if (hit) NF_MD_BULLSEYE_ARROW else NF_MD_CROSSHAIRS_OFF

/** Marker for a destroyed critical slot, with a distinct glyph for engine/gyro/sensor/life-support crits. */
internal fun criticalHitIcon(content: CriticalSlotContent): String =
    when (content) {
        is CriticalSlotContent.Engine -> NF_MD_RADIOACTIVE_CIRCLE
        is CriticalSlotContent.Gyro -> NF_MD_SYNC_CIRCLE
        is CriticalSlotContent.Sensors -> NF_MD_EYE_CIRCLE
        is CriticalSlotContent.LifeSupport -> NF_MD_ACCOUNT_CIRCLE
        else -> NF_MD_IMAGE_BROKEN
    }

/**
 * Marker for a critical hit on a foreign unit whose component is undisclosed
 * (`CriticalHit.Undisclosed`): the same glyph regardless of what was actually hit, so the
 * icon itself doesn't leak the component the way [criticalHitIcon] deliberately does for
 * an owned unit's [CriticalHit.Detailed].
 */
internal fun undisclosedCriticalHitIcon(): String = NF_MD_IMAGE_BROKEN

/** Marker for a log line where a mech location was blown off. */
internal fun locationDestroyedIcon(): String = NF_FA_CHAIN_BROKEN

/** Marker for an ammo explosion log line. */
internal fun ammoExplosionIcon(): String = NF_FA_BOMB

/** Marker for a destroyed unit (board marker + UnitDestroyed log line). */
internal fun destroyedIcon(): String = NF_MD_ROBOT_DEAD

/** Marker for [battletech.tactical.session.SessionNotice] log entries (network happenings). */
internal fun sessionNoticeIcon(): String = NF_MD_LAN_CONNECT

/** Marker for the "Map: <name>" line a [battletech.tactical.session.MapIdentified] event renders. */
internal fun mapNoticeIcon(): String = NF_MD_MAP

/** Marker for a [battletech.tactical.session.AssetConflict] on a MAP: a contributed map collided with the registered one. */
internal fun mapMismatchIcon(): String = NF_MD_ALERT

/** Marker for a [battletech.tactical.session.AssetConflict] on a MECH: a contributed mech model collided with the registered one. */
internal fun mechModelMismatchIcon(): String = NF_MD_ALERT

/** Marker for a unit that fell / was knocked prone. */
internal fun unitFellIcon(): String = NF_MD_TRANSFER_DOWN

/** Marker for a unit that attempted to stand (up whether or not the PSR succeeded). */
internal fun unitStoodUpIcon(): String = NF_MD_TRANSFER_UP

/** Marker for a pilot-wounded log line. */
internal fun pilotWoundedIcon(): String = NF_MD_ACCOUNT_ALERT

/** Marker for a pilot knocked unconscious. */
internal fun pilotUnconsciousIcon(): String = NF_MD_SLEEP

/** Marker for a pilot regaining consciousness. */
internal fun pilotConsciousIcon(): String = NF_MD_SLEEP_OFF

/** Marker for a pilot death: the pilot-hits track's final box and the "pilot killed" log line. */
internal fun pilotDeadIcon(): String = NF_MD_SKULL

/** Marker for the initiative-roll log line. Same glyph as [diceRoll] — it's the same kind of roll. */
internal fun initiativeIcon(): String = NF_MD_DICE_MULTIPLE_OUTLINE

/** Marker for the heat-dissipation log line; chevron direction follows net heat change. */
internal fun heatChangeIcon(wentUp: Boolean): String =
    if (wentUp) NF_MD_THERMOMETER_CHEVRON_UP else NF_MD_THERMOMETER_CHEVRON_DOWN

/** Marker for a unit shutting down (heat-forced or otherwise). */
internal fun unitShutdownIcon(): String = NF_MD_POWER

/** Marker for a unit restarting / powering back on. */
internal fun unitRestartedIcon(): String = NF_MD_RESTART

/** Marker for the match-over log line. */
internal fun matchEndedIcon(): String = NF_MD_TROPHY

/** Marker for a ranged-attack resolution summary that destroyed no location. */
internal fun attacksResolvedIcon(): String = NF_MD_PISTOL

/** Marker for a physical-attack resolution summary that destroyed no location. */
internal fun physicalAttacksResolvedIcon(): String = NF_MD_BOXING_GLOVE

internal fun ammoIcon(): String = NF_MD_AMMUNITION

internal fun infinityIcon(): String = NF_FA_INFINITY

internal fun emptyCircleIcon(): String = NF_MD_CHECKBOX_BLANK_CIRCLE_OUTLINE

/** Filled circle (destroyed-slot indicator) — plain Unicode, paired visually with [emptyCircleIcon]. */
internal fun filledCircleIcon(): String = "●"
