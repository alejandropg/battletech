package battletech.tui.input

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
