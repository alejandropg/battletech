package battletech.tui.input

import battletech.tactical.model.HexDirection
import battletech.tui.game.GamePanelId
import com.github.ajalt.mordant.input.KeyboardEvent
import tenter.input.HintGroup
import tenter.input.InputAction
import tenter.input.KeyBinding
import tenter.input.KeyChord
import tenter.input.KeyGlyph
import tenter.input.KeyLayer
import tenter.input.KeyMap
import tenter.input.KeySection
import tenter.input.PanAction
import tenter.input.ScrollAction

/** The domain facade over the generic [KeyMap] — every keyboard binding in this application. */
internal class Keybindings(private val keyMap: KeyMap<ContextId>) {

    fun resolve(active: List<ContextId>, event: KeyboardEvent): InputAction? = keyMap.resolve(active, event)

    fun hints(context: ContextId): KeySection = keyMap.hints(context)

    /** ctrl+c today. Injected into `terminalEvents` so quit is a binding without leaving the takeWhile. */
    fun isQuit(event: KeyboardEvent): Boolean =
        keyMap.resolve(listOf(ContextId.CHROME), event) == ChromeAction.Quit

    /**
     * The character a panel shows in its border: the single-character key of its own focus chord,
     * so rebinding `alt+p` to LOG relabels LOG's border too. Null when nothing focuses it.
     */
    fun badgeFor(panel: GamePanelId): Char? {
        val action = if (panel == GamePanelId.HELP) ChromeAction.ToggleHelp else ChromeAction.FocusPanel(panel)
        return keyMap.chordsFor(action).firstOrNull()?.key?.singleOrNull()
    }

    internal fun map(): KeyMap<ContextId> = keyMap // invariant tests only

    companion object {
        val DEFAULT: Keybindings = Keybindings(KeyMap(defaultLayers()))
    }
}

private fun defaultLayers(): Map<ContextId, KeyLayer> = mapOf(
    ContextId.CHROME to chromeLayer(),
    ContextId.PANEL_SCROLL to panelScrollLayer(),
    ContextId.MOVEMENT_IDLE to idleLayer(
        title = "MOVEMENT",
        commitAction = IdleAction.CommitDeclarations,
        commitDescription = "commit declarations",
    ),
    ContextId.BROWSING to browsingLayer(),
    ContextId.FACING to facingLayer(),
    ContextId.WEAPON_IDLE to idleLayer(
        title = "WEAPON ATTACK",
        commitAction = IdleAction.CommitDeclarations,
        commitDescription = "commit",
    ),
    ContextId.WEAPON_DECLARING to declaringLayer(title = "DECLARE FIRE", includeTwistTorso = true),
    ContextId.PHYSICAL_IDLE to idleLayer(
        title = "PHYSICAL ATTACK",
        commitAction = IdleAction.CommitDeclarations,
        commitDescription = "commit",
    ),
    ContextId.PHYSICAL_DECLARING to declaringLayer(title = "DECLARE PHYSICAL", includeTwistTorso = false),
)

private fun chromeLayer(): KeyLayer {
    val focusPanelBindings = listOf(
        KeyChord("0", alt = true) to GamePanelId.BOARD,
        KeyChord("1", alt = true) to GamePanelId.UNIT_STATUS,
        KeyChord("2", alt = true) to GamePanelId.DECLARED_TARGETS,
        KeyChord("3", alt = true) to GamePanelId.TARGETS,
        KeyChord("4", alt = true) to GamePanelId.TARGET_STATUS,
        KeyChord("5", alt = true) to GamePanelId.ATTACK_RESULTS,
        KeyChord("9", alt = true) to GamePanelId.LOG,
    ).map { (chord, panel) -> KeyBinding(chord, ChromeAction.FocusPanel(panel), "focusPanel") }

    val panBindings = listOf(
        KeyChord("h") to PanAction.Direction.LEFT,
        KeyChord("l") to PanAction.Direction.RIGHT,
        KeyChord("k") to PanAction.Direction.UP,
        KeyChord("j") to PanAction.Direction.DOWN,
        KeyChord("ArrowLeft", ctrl = true) to PanAction.Direction.LEFT,
        KeyChord("ArrowRight", ctrl = true) to PanAction.Direction.RIGHT,
        KeyChord("ArrowUp", ctrl = true) to PanAction.Direction.UP,
        KeyChord("ArrowDown", ctrl = true) to PanAction.Direction.DOWN,
    ).map { (chord, direction) -> KeyBinding(chord, PanAction.Pan(direction), "pan") }

    val bindings = focusPanelBindings + listOf(
        KeyBinding(KeyChord("h", alt = true), ChromeAction.ToggleHelp, "focusPanel"),
        KeyBinding(KeyChord("+"), ChromeAction.CycleState(1), "cycleState"),
        KeyBinding(KeyChord("-"), ChromeAction.CycleState(-1), "cycleState"),
    ) + panBindings + listOf(
        KeyBinding(KeyChord("Home"), PanAction.Recenter, "recenter"),
        KeyBinding(KeyChord("c", ctrl = true), ChromeAction.Quit, "quit"),
    )

    val hintGroups = listOf(
        HintGroup("focusPanel", "${KeyGlyph.ALT}0-9/${KeyGlyph.ALT}h", "focus a panel"),
        HintGroup("cycleState", "+/-", "resize focused panel"),
        HintGroup("scrollFocused", "↑↓/PgUp/PgDn", "scroll focused panel"),
        HintGroup("wheel", "wheel", "scroll a panel", bindingless = true),
        HintGroup("pan", "hjkl/${KeyGlyph.CTRL}←→↑↓", "pan board"),
        HintGroup("recenter", "Home", "recenter board on cursor"),
        HintGroup("quit", "${KeyGlyph.CTRL}c", "quit"),
    )

    return KeyLayer(title = "GLOBAL", bindings = bindings, hintGroups = hintGroups)
}

private fun panelScrollLayer(): KeyLayer = KeyLayer(
    title = null,
    shadowing = true,
    bindings = listOf(
        KeyBinding(KeyChord("ArrowUp"), ScrollAction.Lines(-1), "scrollFocused"),
        KeyBinding(KeyChord("ArrowDown"), ScrollAction.Lines(1), "scrollFocused"),
        KeyBinding(KeyChord("PageUp"), ScrollAction.Pages(-1), "scrollFocused"),
        KeyBinding(KeyChord("PageDown"), ScrollAction.Pages(1), "scrollFocused"),
    ),
)

/**
 * The eight key-to-direction chords shared by every idle-selecting state and by [MovementPhase.
 * Browsing][battletech.tui.game.phase.MovementPhase.Browsing] — mirrors the old `InputMapper.
 * keyToDirection`, including `q`/`e`→NW/NE and `a`→SW alongside the wasd/arrow set.
 */
private fun cursorBindings(action: (HexDirection) -> InputAction): List<KeyBinding> = listOf(
    KeyChord("ArrowUp") to HexDirection.N,
    KeyChord("w") to HexDirection.N,
    KeyChord("ArrowDown") to HexDirection.S,
    KeyChord("s") to HexDirection.S,
    KeyChord("ArrowRight") to HexDirection.SE,
    KeyChord("d") to HexDirection.SE,
    KeyChord("ArrowLeft") to HexDirection.NW,
    KeyChord("q") to HexDirection.NW,
    KeyChord("e") to HexDirection.NE,
    KeyChord("a") to HexDirection.SW,
).map { (chord, direction) -> KeyBinding(chord, action(direction), "moveCursor") }

private fun facingBindings(action: (Int) -> InputAction): List<KeyBinding> =
    (1..6).map { index -> KeyBinding(KeyChord(index.toString()), action(index), "selectFacing") }

private val MOVE_CURSOR_HINT = HintGroup("moveCursor", "←→↑↓/wasd", "move cursor")

/** Shared by MOVEMENT_IDLE, WEAPON_IDLE, and PHYSICAL_IDLE — identical bindings, different title/commit wording. */
private fun idleLayer(title: String, commitAction: IdleAction, commitDescription: String): KeyLayer = KeyLayer(
    title = title,
    bindings = cursorBindings { IdleAction.MoveCursor(it) } + listOf(
        KeyBinding(KeyChord("Enter"), IdleAction.SelectUnit, "selectUnit"),
        KeyBinding(KeyChord("Tab"), IdleAction.CycleUnit, "cycleUnit"),
        KeyBinding(KeyChord("c"), commitAction, "commit"),
    ),
    hintGroups = listOf(
        MOVE_CURSOR_HINT,
        HintGroup("selectUnit", KeyGlyph.ENTER, "select unit"),
        HintGroup("cycleUnit", KeyGlyph.TAB, "cycle unit"),
        HintGroup("commit", "c", commitDescription),
    ),
)

private fun browsingLayer(): KeyLayer = KeyLayer(
    title = "BROWSE DESTINATION",
    bindings = cursorBindings { BrowsingAction.MoveCursor(it) } + facingBindings { BrowsingAction.SelectFacing(it) } + listOf(
        KeyBinding(KeyChord("Enter"), BrowsingAction.ConfirmPath, "confirmPath"),
        KeyBinding(KeyChord("Escape"), BrowsingAction.Cancel, "cancel"),
        KeyBinding(KeyChord("Tab"), BrowsingAction.CycleUnit, "cycleUnit"),
        KeyBinding(KeyChord("x"), BrowsingAction.CycleMode, "cycleMode"),
    ),
    hintGroups = listOf(
        MOVE_CURSOR_HINT,
        HintGroup("selectFacing", "1-6", "select facing"),
        HintGroup("confirmPath", KeyGlyph.ENTER, "confirm path"),
        HintGroup("cancel", KeyGlyph.ESC, "back"),
        HintGroup("cycleUnit", KeyGlyph.TAB, "cycle unit"),
        HintGroup("cycleMode", "x", "cycle movement mode"),
    ),
)

private fun facingLayer(): KeyLayer = KeyLayer(
    title = "SELECT FACING",
    bindings = facingBindings { FacingAction.SelectFacing(it) } + listOf(
        KeyBinding(KeyChord("Escape"), FacingAction.Cancel, "cancel"),
        KeyBinding(KeyChord("Tab"), FacingAction.CycleUnit, "cycleUnit"),
    ),
    hintGroups = listOf(
        HintGroup("selectFacing", "1-6", "select facing"),
        HintGroup("cancel", KeyGlyph.ESC, "back"),
        HintGroup("cycleUnit", KeyGlyph.TAB, "cycle unit"),
    ),
)

/** Shared by WEAPON_DECLARING and PHYSICAL_DECLARING — the latter drops the torso-twist rows (§4.2). */
private fun declaringLayer(title: String, includeTwistTorso: Boolean): KeyLayer {
    val twistTorsoBindings = if (includeTwistTorso) {
        listOf(
            KeyBinding(KeyChord("ArrowRight"), AttackAction.TwistTorso(clockwise = true), "twistTorso"),
            KeyBinding(KeyChord("d"), AttackAction.TwistTorso(clockwise = true), "twistTorso"),
            KeyBinding(KeyChord("ArrowLeft"), AttackAction.TwistTorso(clockwise = false), "twistTorso"),
            KeyBinding(KeyChord("a"), AttackAction.TwistTorso(clockwise = false), "twistTorso"),
        )
    } else {
        emptyList()
    }

    val bindings = twistTorsoBindings + listOf(
        KeyBinding(KeyChord("ArrowUp"), AttackAction.NavigateWeapons(-1), "navigate"),
        KeyBinding(KeyChord("w"), AttackAction.NavigateWeapons(-1), "navigate"),
        KeyBinding(KeyChord("ArrowDown"), AttackAction.NavigateWeapons(1), "navigate"),
        KeyBinding(KeyChord("s"), AttackAction.NavigateWeapons(1), "navigate"),
        KeyBinding(KeyChord(" "), AttackAction.ToggleWeapon, "toggleWeapon"),
        KeyBinding(KeyChord("Escape"), AttackAction.Cancel, "cancel"),
        KeyBinding(KeyChord("Tab"), AttackAction.NextAttacker, "nextAttacker"),
        KeyBinding(KeyChord("c"), AttackAction.Commit, "commit"),
    )

    val hintGroups = buildList {
        if (includeTwistTorso) add(HintGroup("twistTorso", "←→/ad", "twist torso"))
        add(HintGroup("navigate", if (includeTwistTorso) "↑↓/ws" else "↑↓", if (includeTwistTorso) "navigate weapons" else "navigate"))
        add(HintGroup("toggleWeapon", KeyGlyph.SPACE, if (includeTwistTorso) "toggle weapon" else "toggle punch/kick"))
        add(HintGroup("cancel", KeyGlyph.ESC, "back"))
        add(HintGroup("nextAttacker", KeyGlyph.TAB, "next attacker"))
        add(HintGroup("commit", "c", "commit"))
    }

    return KeyLayer(title = title, bindings = bindings, hintGroups = hintGroups)
}
