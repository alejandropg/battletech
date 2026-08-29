package battletech.tui.input

import battletech.tactical.model.HexDirection
import battletech.tui.game.GamePanelId
import com.github.ajalt.mordant.input.KeyboardEvent
import tenter.input.HintGroup
import tenter.input.InputAction
import tenter.input.KeyBinding
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
        KeyboardEvent("0", alt = true) to GamePanelId.BOARD,
        KeyboardEvent("1", alt = true) to GamePanelId.UNIT_STATUS,
        KeyboardEvent("2", alt = true) to GamePanelId.DECLARED_TARGETS,
        KeyboardEvent("3", alt = true) to GamePanelId.TARGETS,
        KeyboardEvent("4", alt = true) to GamePanelId.TARGET_STATUS,
        KeyboardEvent("5", alt = true) to GamePanelId.ATTACK_RESULTS,
        KeyboardEvent("9", alt = true) to GamePanelId.LOG,
    ).map { (chord, panel) -> KeyBinding(chord, ChromeAction.FocusPanel(panel), "focusPanel") }

    val panBindings = listOf(
        KeyboardEvent("h") to PanAction.Direction.LEFT,
        KeyboardEvent("l") to PanAction.Direction.RIGHT,
        KeyboardEvent("k") to PanAction.Direction.UP,
        KeyboardEvent("j") to PanAction.Direction.DOWN,
        KeyboardEvent("ArrowLeft", ctrl = true) to PanAction.Direction.LEFT,
        KeyboardEvent("ArrowRight", ctrl = true) to PanAction.Direction.RIGHT,
        KeyboardEvent("ArrowUp", ctrl = true) to PanAction.Direction.UP,
        KeyboardEvent("ArrowDown", ctrl = true) to PanAction.Direction.DOWN,
    ).map { (chord, direction) -> KeyBinding(chord, PanAction.Pan(direction), "pan") }

    val bindings = focusPanelBindings + listOf(
        KeyBinding(KeyboardEvent("?"), ChromeAction.ToggleHelp, "toggleHelp"),
        KeyBinding(KeyboardEvent("+"), ChromeAction.CycleState(1), "cycleState"),
        KeyBinding(KeyboardEvent("-"), ChromeAction.CycleState(-1), "cycleState"),
    ) + panBindings + listOf(
        KeyBinding(KeyboardEvent("Home"), PanAction.Recenter, "recenter"),
        KeyBinding(KeyboardEvent("c", ctrl = true), ChromeAction.Quit, "quit"),
    )

    val hintGroups = listOf(
        HintGroup("focusPanel", "${KeyGlyph.ALT}0-9", "focus a panel"),
        HintGroup("toggleHelp", "?", "toggle help"),
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
        KeyBinding(KeyboardEvent("ArrowUp"), ScrollAction.Lines(-1), "scrollFocused"),
        KeyBinding(KeyboardEvent("ArrowDown"), ScrollAction.Lines(1), "scrollFocused"),
        KeyBinding(KeyboardEvent("PageUp"), ScrollAction.Pages(-1), "scrollFocused"),
        KeyBinding(KeyboardEvent("PageDown"), ScrollAction.Pages(1), "scrollFocused"),
    ),
)

/**
 * The eight key-to-direction chords shared by every idle-selecting state and by [MovementPhase.
 * Browsing][battletech.tui.game.phase.MovementPhase.Browsing] — mirrors the old `InputMapper.
 * keyToDirection`, including `q`/`e`→NW/NE and `a`→SW alongside the wasd/arrow set.
 */
private fun cursorBindings(action: (HexDirection) -> InputAction): List<KeyBinding> = listOf(
    KeyboardEvent("ArrowUp") to HexDirection.N,
    KeyboardEvent("w") to HexDirection.N,
    KeyboardEvent("ArrowDown") to HexDirection.S,
    KeyboardEvent("s") to HexDirection.S,
    KeyboardEvent("ArrowRight") to HexDirection.SE,
    KeyboardEvent("d") to HexDirection.SE,
    KeyboardEvent("ArrowLeft") to HexDirection.NW,
    KeyboardEvent("q") to HexDirection.NW,
    KeyboardEvent("e") to HexDirection.NE,
    KeyboardEvent("a") to HexDirection.SW,
).map { (chord, direction) -> KeyBinding(chord, action(direction), "moveCursor") }

private fun facingBindings(action: (Int) -> InputAction): List<KeyBinding> =
    (1..6).map { index -> KeyBinding(KeyboardEvent(index.toString()), action(index), "selectFacing") }

private val MOVE_CURSOR_HINT = HintGroup("moveCursor", "←→↑↓/wasd", "move cursor")

/** Shared by MOVEMENT_IDLE, WEAPON_IDLE, and PHYSICAL_IDLE — identical bindings, different title/commit wording. */
private fun idleLayer(title: String, commitAction: IdleAction, commitDescription: String): KeyLayer = KeyLayer(
    title = title,
    bindings = cursorBindings { IdleAction.MoveCursor(it) } + listOf(
        KeyBinding(KeyboardEvent("Enter"), IdleAction.SelectUnit, "selectUnit"),
        KeyBinding(KeyboardEvent("Tab"), IdleAction.CycleUnit, "cycleUnit"),
        KeyBinding(KeyboardEvent("c"), commitAction, "commit"),
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
        KeyBinding(KeyboardEvent("Enter"), BrowsingAction.ConfirmPath, "confirmPath"),
        KeyBinding(KeyboardEvent("Escape"), BrowsingAction.Cancel, "cancel"),
        KeyBinding(KeyboardEvent("Tab"), BrowsingAction.CycleUnit, "cycleUnit"),
        KeyBinding(KeyboardEvent("x"), BrowsingAction.CycleMode, "cycleMode"),
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
        KeyBinding(KeyboardEvent("Escape"), FacingAction.Cancel, "cancel"),
        KeyBinding(KeyboardEvent("Tab"), FacingAction.CycleUnit, "cycleUnit"),
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
            KeyBinding(KeyboardEvent("ArrowRight"), AttackAction.TwistTorso(clockwise = true), "twistTorso"),
            KeyBinding(KeyboardEvent("d"), AttackAction.TwistTorso(clockwise = true), "twistTorso"),
            KeyBinding(KeyboardEvent("ArrowLeft"), AttackAction.TwistTorso(clockwise = false), "twistTorso"),
            KeyBinding(KeyboardEvent("a"), AttackAction.TwistTorso(clockwise = false), "twistTorso"),
        )
    } else {
        emptyList()
    }

    val bindings = twistTorsoBindings + listOf(
        KeyBinding(KeyboardEvent("ArrowUp"), AttackAction.NavigateWeapons(-1), "navigate"),
        KeyBinding(KeyboardEvent("w"), AttackAction.NavigateWeapons(-1), "navigate"),
        KeyBinding(KeyboardEvent("ArrowDown"), AttackAction.NavigateWeapons(1), "navigate"),
        KeyBinding(KeyboardEvent("s"), AttackAction.NavigateWeapons(1), "navigate"),
        KeyBinding(KeyboardEvent(" "), AttackAction.ToggleWeapon, "toggleWeapon"),
        KeyBinding(KeyboardEvent("Escape"), AttackAction.Cancel, "cancel"),
        KeyBinding(KeyboardEvent("Tab"), AttackAction.NextAttacker, "nextAttacker"),
        KeyBinding(KeyboardEvent("c"), AttackAction.Commit, "commit"),
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
