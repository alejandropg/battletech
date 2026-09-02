package battletech.tui.input

import battletech.tactical.model.HexDirection
import battletech.tui.game.GamePanelId
import battletech.tui.setup.SetupAction
import battletech.tui.setup.SetupPanelId
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
     * The character a panel shows in its border: the single-character key of the chord bound to
     * [action], so rebinding `alt+p` to LOG relabels LOG's border too. Null when nothing is bound.
     *
     * Takes the ACTION rather than the panel because which action focuses a panel is the caller's
     * own fact: every panel but HELP is focused by [ChromeAction.FocusPanel], while HELP is
     * reached by [ChromeAction.ToggleHelp] (`?` does more than focus — see `AppState.helpOpen`).
     * Asking for a panel instead would force this class to hold a list of which panel ids are
     * "the help one" in every screen that exists — a list that silently rots the moment a screen
     * is added.
     */
    fun badgeFor(action: InputAction): Char? = keyMap.chordsFor(action).firstOrNull()?.key?.singleOrNull()

    internal fun map(): KeyMap<ContextId> = keyMap // invariant tests only

    companion object {
        val DEFAULT: Keybindings = Keybindings(KeyMap(defaultLayers()))
    }
}

private fun defaultLayers(): Map<ContextId, KeyLayer> = mapOf(
    ContextId.CHROME to chromeLayer(),
    ContextId.GAME_CHROME to gameChromeLayer(),
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
    ContextId.SETUP to setupLayer(),
)

/**
 * The chords every screen shares: help, resize the focused panel, quit. Panel focus and board pan
 * are game-only — see [gameChromeLayer] — so this layer's own bindings never collide with SETUP's
 * `1`-`4` panel-focus chords.
 */
private fun chromeLayer(): KeyLayer {
    val bindings = shiftedPunctuation("?", ChromeAction.ToggleHelp, "toggleHelp") +
        shiftedPunctuation("+", ChromeAction.CycleState(1), "cycleState") +
        listOf(
            KeyBinding(KeyboardEvent("-"), ChromeAction.CycleState(-1), "cycleState"),
            KeyBinding(KeyboardEvent("c", ctrl = true), ChromeAction.Quit, "quit"),
        )

    val hintGroups = listOf(
        HintGroup("focusPanel", "0-9", "focus/resize a panel"),
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

/**
 * The game screen's own chrome: panel focus (`0`-`9`) and board pan/recenter — bindings that only
 * make sense once a [GamePanelId] board exists, so they don't belong in [chromeLayer], which every
 * screen (including SETUP) shares. `title = null`: these chords are documented by GLOBAL's
 * `focusPanel`/`pan`/`recenter` rows (see [tenter.input.KeyMap]'s KDoc on section-less layers).
 */
private fun gameChromeLayer(): KeyLayer {
    val focusPanelBindings = listOf(
        KeyboardEvent("0") to GamePanelId.BOARD,
        KeyboardEvent("1") to GamePanelId.UNIT_STATUS,
        KeyboardEvent("2") to GamePanelId.DECLARED_TARGETS,
        KeyboardEvent("3") to GamePanelId.TARGETS,
        KeyboardEvent("4") to GamePanelId.TARGET_STATUS,
        KeyboardEvent("5") to GamePanelId.ATTACK_RESULTS,
        KeyboardEvent("9") to GamePanelId.LOG,
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

    val bindings = focusPanelBindings + panBindings + listOf(
        KeyBinding(KeyboardEvent("Home"), PanAction.Recenter, "recenter"),
    )

    return KeyLayer(title = null, bindings = bindings)
}

/**
 * The interactive setup screen's own chords. `SETUP` binds `1`-`4` for panel focus (unlike the
 * game, which uses `0`-`9` via [gameChromeLayer]) — see `docs`'s interactive-setup-screen plan for
 * why the two never need to coexist in one active-context list.
 */
private fun setupLayer(): KeyLayer {
    val focusPanelBindings = listOf(
        KeyboardEvent("1") to SetupPanelId.MODE,
        KeyboardEvent("2") to SetupPanelId.MAP,
        KeyboardEvent("3") to SetupPanelId.PLAYER_1,
        KeyboardEvent("4") to SetupPanelId.PLAYER_2,
    ).map { (chord, panel) -> KeyBinding(chord, ChromeAction.FocusPanel(panel), "focusPanel") }

    val bindings = focusPanelBindings + listOf(
        KeyBinding(KeyboardEvent("w"), SetupAction.MoveCursor(-1), "moveCursor"),
        KeyBinding(KeyboardEvent("ArrowUp"), SetupAction.MoveCursor(-1), "moveCursor"),
        KeyBinding(KeyboardEvent("s"), SetupAction.MoveCursor(1), "moveCursor"),
        KeyBinding(KeyboardEvent("ArrowDown"), SetupAction.MoveCursor(1), "moveCursor"),
        KeyBinding(KeyboardEvent("a"), SetupAction.Adjust(-1), "adjust"),
        KeyBinding(KeyboardEvent("ArrowLeft"), SetupAction.Adjust(-1), "adjust"),
        KeyBinding(KeyboardEvent("d"), SetupAction.Adjust(1), "adjust"),
        KeyBinding(KeyboardEvent("ArrowRight"), SetupAction.Adjust(1), "adjust"),
        KeyBinding(KeyboardEvent(" "), SetupAction.Toggle, "toggle"),
        KeyBinding(KeyboardEvent("Enter"), SetupAction.NextPanel, "nextPanel"),
        KeyBinding(KeyboardEvent("c"), SetupAction.Commit, "commit"),
    )

    val hintGroups = listOf(
        HintGroup("focusPanel", "1-4", "focus a panel"),
        HintGroup("moveCursor", "↑↓/ws", "move cursor"),
        HintGroup("adjust", "←→/ad", "adjust count"),
        HintGroup("toggle", KeyGlyph.SPACE, "toggle selection"),
        HintGroup("nextPanel", KeyGlyph.ENTER, "next panel"),
        HintGroup("commit", "c", "commit"),
    )

    return KeyLayer(title = "SETUP", bindings = bindings, hintGroups = hintGroups, shadowing = false)
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

private val FACING_KEYS: List<Pair<String, HexDirection>> = listOf(
    "q" to HexDirection.NW, "w" to HexDirection.N,  "e" to HexDirection.NE,
    "a" to HexDirection.SW, "s" to HexDirection.S,  "d" to HexDirection.SE,
)

/** Lowercase for SELECT FACING; uppercase for BROWSE DESTINATION, where lowercase moves the cursor. */
private fun facingBindings(shifted: Boolean, action: (HexDirection) -> InputAction): List<KeyBinding> =
    FACING_KEYS.map { (key, direction) ->
        val chord = if (shifted) KeyboardEvent(key.uppercase(), shift = true) else KeyboardEvent(key)
        KeyBinding(chord, action(direction), "selectFacing")
    }

/**
 * Both encodings of one shifted-punctuation keystroke. Posix derives `shift` from the character
 * produced (`?` arrives with `shift = false`); Windows reports the physical key state
 * (`shift = true`). One key, spelled two ways by two terminals — not two ways to do one thing.
 */
private fun shiftedPunctuation(key: String, action: InputAction, hintGroup: String): List<KeyBinding> =
    listOf(
        KeyBinding(KeyboardEvent(key), action, hintGroup),
        KeyBinding(KeyboardEvent(key, shift = true), action, hintGroup),
    )

private val MOVE_CURSOR_HINT = HintGroup("moveCursor", "qweasd/←→↑↓", "move cursor")

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
    bindings = cursorBindings { BrowsingAction.MoveCursor(it) } +
        facingBindings(shifted = true) { BrowsingAction.SelectFacing(it) } + listOf(
        KeyBinding(KeyboardEvent("Enter"), BrowsingAction.ConfirmPath, "confirmPath"),
        KeyBinding(KeyboardEvent("Escape"), BrowsingAction.Cancel, "cancel"),
        KeyBinding(KeyboardEvent("Tab"), BrowsingAction.CycleUnit, "cycleUnit"),
        KeyBinding(KeyboardEvent("x"), BrowsingAction.CycleMode, "cycleMode"),
    ),
    hintGroups = listOf(
        MOVE_CURSOR_HINT,
        HintGroup("selectFacing", "QWEASD", "commit with facing"),
        HintGroup("confirmPath", KeyGlyph.ENTER, "confirm path"),
        HintGroup("cancel", KeyGlyph.ESC, "back"),
        HintGroup("cycleUnit", KeyGlyph.TAB, "cycle unit"),
        HintGroup("cycleMode", "x", "cycle movement mode"),
    ),
)

private fun facingLayer(): KeyLayer = KeyLayer(
    title = "SELECT FACING",
    bindings = facingBindings(shifted = false) { FacingAction.SelectFacing(it) } + listOf(
        KeyBinding(KeyboardEvent("Escape"), FacingAction.Cancel, "cancel"),
        KeyBinding(KeyboardEvent("Tab"), FacingAction.CycleUnit, "cycleUnit"),
    ),
    hintGroups = listOf(
        HintGroup("selectFacing", "qweasd", "select facing"),
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
