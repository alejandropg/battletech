package battletech.tui.hex

import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.model.Terrain
import battletech.tactical.unit.CriticalSlotContent
import tenter.icons.NF_FA_BOMB
import tenter.icons.NF_FA_CHAIN_BROKEN
import tenter.icons.NF_FA_INFINITY
import tenter.icons.NF_MD_ACCOUNT_ALERT
import tenter.icons.NF_MD_ACCOUNT_CIRCLE
import tenter.icons.NF_MD_AMMUNITION
import tenter.icons.NF_MD_ARROW_BOTTOM_LEFT
import tenter.icons.NF_MD_ARROW_BOTTOM_LEFT_BOLD_OUTLINE
import tenter.icons.NF_MD_ARROW_BOTTOM_RIGHT
import tenter.icons.NF_MD_ARROW_BOTTOM_RIGHT_BOLD_OUTLINE
import tenter.icons.NF_MD_ARROW_DOWN
import tenter.icons.NF_MD_ARROW_DOWN_BOLD_OUTLINE
import tenter.icons.NF_MD_ARROW_TOP_LEFT
import tenter.icons.NF_MD_ARROW_TOP_LEFT_BOLD_OUTLINE
import tenter.icons.NF_MD_ARROW_TOP_RIGHT
import tenter.icons.NF_MD_ARROW_TOP_RIGHT_BOLD_OUTLINE
import tenter.icons.NF_MD_ARROW_UP
import tenter.icons.NF_MD_ARROW_UP_BOLD_OUTLINE
import tenter.icons.NF_MD_ARROW_UP_THIN_N
import tenter.icons.NF_MD_ARROW_UP_THIN_NE
import tenter.icons.NF_MD_ARROW_UP_THIN_NW
import tenter.icons.NF_MD_ARROW_UP_THIN_S
import tenter.icons.NF_MD_ARROW_UP_THIN_SE
import tenter.icons.NF_MD_ARROW_UP_THIN_SW
import tenter.icons.NF_MD_BOXING_GLOVE
import tenter.icons.NF_MD_BULLSEYE_ARROW
import tenter.icons.NF_MD_CHECKBOX_BLANK_CIRCLE_OUTLINE
import tenter.icons.NF_MD_CROSSHAIRS_OFF
import tenter.icons.NF_MD_DICE_1
import tenter.icons.NF_MD_DICE_2
import tenter.icons.NF_MD_DICE_3
import tenter.icons.NF_MD_DICE_4
import tenter.icons.NF_MD_DICE_5
import tenter.icons.NF_MD_DICE_6
import tenter.icons.NF_MD_DICE_MULTIPLE_OUTLINE
import tenter.icons.NF_MD_EYE_CIRCLE
import tenter.icons.NF_MD_GRAIN
import tenter.icons.NF_MD_IMAGE_BROKEN
import tenter.icons.NF_MD_LAN_CONNECT
import tenter.icons.NF_MD_NUMERIC_1_BOX_MULTIPLE
import tenter.icons.NF_MD_NUMERIC_1_BOX_MULTIPLE_OUTLINE
import tenter.icons.NF_MD_NUMERIC_2_BOX_MULTIPLE
import tenter.icons.NF_MD_NUMERIC_2_BOX_MULTIPLE_OUTLINE
import tenter.icons.NF_MD_NUMERIC_3_BOX_MULTIPLE
import tenter.icons.NF_MD_NUMERIC_3_BOX_MULTIPLE_OUTLINE
import tenter.icons.NF_MD_NUMERIC_4_BOX_MULTIPLE
import tenter.icons.NF_MD_NUMERIC_4_BOX_MULTIPLE_OUTLINE
import tenter.icons.NF_MD_NUMERIC_5_BOX_MULTIPLE
import tenter.icons.NF_MD_NUMERIC_5_BOX_MULTIPLE_OUTLINE
import tenter.icons.NF_MD_NUMERIC_6_BOX_MULTIPLE
import tenter.icons.NF_MD_NUMERIC_6_BOX_MULTIPLE_OUTLINE
import tenter.icons.NF_MD_NUMERIC_7_BOX_MULTIPLE
import tenter.icons.NF_MD_NUMERIC_7_BOX_MULTIPLE_OUTLINE
import tenter.icons.NF_MD_NUMERIC_8_BOX_MULTIPLE
import tenter.icons.NF_MD_NUMERIC_8_BOX_MULTIPLE_OUTLINE
import tenter.icons.NF_MD_NUMERIC_9_BOX_MULTIPLE
import tenter.icons.NF_MD_NUMERIC_9_BOX_MULTIPLE_OUTLINE
import tenter.icons.NF_MD_PINE_TREE
import tenter.icons.NF_MD_PISTOL
import tenter.icons.NF_MD_POWER
import tenter.icons.NF_MD_RADIOACTIVE_CIRCLE
import tenter.icons.NF_MD_RESTART
import tenter.icons.NF_MD_ROBOT_DEAD
import tenter.icons.NF_MD_ROCKET_LAUNCH
import tenter.icons.NF_MD_RUN_FAST
import tenter.icons.NF_MD_SKULL
import tenter.icons.NF_MD_SLEEP
import tenter.icons.NF_MD_SLEEP_OFF
import tenter.icons.NF_MD_SYNC_CIRCLE
import tenter.icons.NF_MD_TARGET
import tenter.icons.NF_MD_THERMOMETER_CHEVRON_DOWN
import tenter.icons.NF_MD_THERMOMETER_CHEVRON_UP
import tenter.icons.NF_MD_TRANSFER_DOWN
import tenter.icons.NF_MD_TRANSFER_UP
import tenter.icons.NF_MD_TREE_OUTLINE
import tenter.icons.NF_MD_TROPHY
import tenter.icons.NF_MD_WALK
import tenter.icons.NF_MD_WAVES

// Nerd Font glyph codepoints live in tenter.icons.NerdFont; this file only maps them onto
// BattleTech domain concepts (terrain, facing, criticals, log events, ...).

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

// Number mapping: 1=N, 2=NE, 3=SE, 4=S, 5=SW, 6=NW
internal fun facingNumber(direction: HexDirection): String = when (direction) {
    HexDirection.N  -> "1"
    HexDirection.NE -> "2"
    HexDirection.SE -> "3"
    HexDirection.S  -> "4"
    HexDirection.SW -> "5"
    HexDirection.NW -> "6"
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
