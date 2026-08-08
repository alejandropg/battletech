package battletech.tui.input

/** One key (or chord) and what it does, as shown in the HELP panel. */
internal data class KeyHint(val keys: String, val description: String)

/** A titled group of [hints] — one "local" section (current phase) or the "global" section. */
internal data class KeySection(val title: String, val hints: List<KeyHint>)

/**
 * The keybinding hints shown in the HELP panel, kept next to the `InputMapper.map*Event`
 * function each list documents so a binding change and its hint change together.
 */
internal object Keymap {

    /** Mirrors [InputMapper.mapIdleEvent] as used by [battletech.tui.game.phase.MovementPhase.SelectingUnit]. */
    val MOVEMENT_IDLE: List<KeyHint> = listOf(
        KeyHint("←→↑↓/wasd", "move cursor"),
        KeyHint("Enter", "select unit"),
        KeyHint("Tab", "cycle unit"),
        KeyHint("c", "commit declarations"),
    )

    /** Mirrors [InputMapper.mapBrowsingEvent]. */
    val BROWSING: List<KeyHint> = listOf(
        KeyHint("←→↑↓/wasd", "move cursor"),
        KeyHint("1-6", "select facing"),
        KeyHint("Enter", "confirm path"),
        KeyHint("Esc", "back"),
        KeyHint("Tab", "cycle unit"),
        KeyHint("x", "cycle movement mode"),
    )

    /** Mirrors [InputMapper.mapFacingEvent]. */
    val FACING: List<KeyHint> = listOf(
        KeyHint("1-6", "select facing"),
        KeyHint("Esc", "back"),
        KeyHint("Tab", "cycle unit"),
    )

    /** Mirrors [InputMapper.mapIdleEvent] as used by the attack phases' attacker selection. */
    val ATTACK_IDLE: List<KeyHint> = listOf(
        KeyHint("←→↑↓/wasd", "move cursor"),
        KeyHint("Enter", "select unit"),
        KeyHint("Tab", "cycle unit"),
        KeyHint("c", "commit"),
    )

    /** Mirrors [InputMapper.mapAttackEvent] as used by weapon-attack declaration. */
    val WEAPON_DECLARING: List<KeyHint> = listOf(
        KeyHint("←/→", "twist torso"),
        KeyHint("↑/↓", "navigate weapons"),
        KeyHint("Space", "toggle weapon"),
        KeyHint("Esc", "back"),
        KeyHint("Tab", "next attacker"),
        KeyHint("c", "commit"),
    )

    /** Mirrors [InputMapper.mapAttackEvent] as used by physical-attack declaration (no torso twist). */
    val PHYSICAL_DECLARING: List<KeyHint> = listOf(
        KeyHint("↑/↓", "navigate"),
        KeyHint("Space", "toggle punch/kick"),
        KeyHint("Esc", "back"),
        KeyHint("Tab", "next attacker"),
        KeyHint("c", "commit"),
    )

    /** Keys that work regardless of the current phase. */
    val GLOBAL: KeySection = KeySection(
        title = "GLOBAL",
        hints = listOf(
            KeyHint("alt+h", "toggle HELP"),
            KeyHint("alt+0-5", "collapse/expand a panel"),
            KeyHint("wheel", "scroll a panel"),
            KeyHint("ctrl+c", "quit"),
        ),
    )
}
