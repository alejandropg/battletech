package battletech.tui.input

/** One key (or chord) and what it does, as shown in the HELP panel. */
internal data class KeyHint(val keys: String, val description: String)

/** A titled group of [hints] — one "local" section (current phase) or the "global" section. */
internal data class KeySection(val title: String, val hints: List<KeyHint>)

/**
 * Single-cell glyphs standing in for special keys in [KeyHint] labels, matching the arrow
 * glyphs already used for cursor movement. Each codepoint is confirmed width-1 by
 * [battletech.tui.screen.CellWidth] (none fall in its wide-glyph ranges), which is what keeps
 * the HELP panel's key column aligned.
 */
internal object KeyGlyph {
    const val ENTER: String = "⏎"
    const val TAB: String = "⇥"
    const val ESC: String = "⎋"
    const val SPACE: String = "␣"
    const val CTRL: String = "⌃"
    const val ALT: String = "⌥"
}

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
            KeyHint("${KeyGlyph.CTRL}c", "quit"),
        ),
    )
}
