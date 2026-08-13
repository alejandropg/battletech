package battletech.tui.input

import tenter.input.KeyGlyph
import tenter.input.KeyHint
import tenter.input.KeySection

/**
 * The keybinding hints shown in the HELP panel, kept next to the `InputMapper.map*Event`
 * function each list documents so a binding change and its hint change together.
 */
internal object Keymap {

    /** Mirrors [InputMapper.mapIdleEvent] as used by [battletech.tui.game.phase.MovementPhase.SelectingUnit]. */
    val MOVEMENT_IDLE: List<KeyHint> = listOf(
        KeyHint("←→↑↓/wasd", "move cursor"),
        KeyHint(KeyGlyph.ENTER, "select unit"),
        KeyHint(KeyGlyph.TAB, "cycle unit"),
        KeyHint("c", "commit declarations"),
    )

    /** Mirrors [InputMapper.mapBrowsingEvent]. */
    val BROWSING: List<KeyHint> = listOf(
        KeyHint("←→↑↓/wasd", "move cursor"),
        KeyHint("1-6", "select facing"),
        KeyHint(KeyGlyph.ENTER, "confirm path"),
        KeyHint(KeyGlyph.ESC, "back"),
        KeyHint(KeyGlyph.TAB, "cycle unit"),
        KeyHint("x", "cycle movement mode"),
    )

    /** Mirrors [InputMapper.mapFacingEvent]. */
    val FACING: List<KeyHint> = listOf(
        KeyHint("1-6", "select facing"),
        KeyHint(KeyGlyph.ESC, "back"),
        KeyHint(KeyGlyph.TAB, "cycle unit"),
    )

    /** Mirrors [InputMapper.mapIdleEvent] as used by the attack phases' attacker selection. */
    val ATTACK_IDLE: List<KeyHint> = listOf(
        KeyHint("←→↑↓/wasd", "move cursor"),
        KeyHint(KeyGlyph.ENTER, "select unit"),
        KeyHint(KeyGlyph.TAB, "cycle unit"),
        KeyHint("c", "commit"),
    )

    /** Mirrors [InputMapper.mapAttackEvent] as used by weapon-attack declaration. */
    val WEAPON_DECLARING: List<KeyHint> = listOf(
        KeyHint("←→/ad", "twist torso"),
        KeyHint("↑↓/ws", "navigate weapons"),
        KeyHint(KeyGlyph.SPACE, "toggle weapon"),
        KeyHint(KeyGlyph.ESC, "back"),
        KeyHint(KeyGlyph.TAB, "next attacker"),
        KeyHint("c", "commit"),
    )

    /** Mirrors [InputMapper.mapAttackEvent] as used by physical-attack declaration (no torso twist). */
    val PHYSICAL_DECLARING: List<KeyHint> = listOf(
        KeyHint("↑↓", "navigate"),
        KeyHint(KeyGlyph.SPACE, "toggle punch/kick"),
        KeyHint(KeyGlyph.ESC, "back"),
        KeyHint(KeyGlyph.TAB, "next attacker"),
        KeyHint("c", "commit"),
    )

    /** Keys that work regardless of the current phase. */
    val GLOBAL: KeySection = KeySection(
        title = "GLOBAL",
        hints = listOf(
            KeyHint("${KeyGlyph.ALT}h", "toggle HELP"),
            KeyHint("${KeyGlyph.ALT}0-5", "collapse/expand a panel"),
            KeyHint("wheel", "scroll a panel"),
            KeyHint("hjkl/${KeyGlyph.CTRL}←→↑↓", "pan board"),
            KeyHint("Home", "recenter board on cursor"),
            KeyHint("${KeyGlyph.CTRL}c", "quit"),
        ),
    )
}
