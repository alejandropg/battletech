package io.archinaut.battletech.tui.input

import io.archinaut.battletech.tactical.model.HexDirection
import io.archinaut.battletech.tui.game.GamePanelId
import io.archinaut.battletech.tui.setup.SetupAction
import io.archinaut.battletech.tui.setup.SetupPanelId
import io.archinaut.battletech.tui.loop.SHADOWING_CONTEXTS
import com.github.ajalt.mordant.input.KeyboardEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.archinaut.tenter.input.PanAction
import io.archinaut.tenter.input.ScrollAction
import io.archinaut.tenter.panel.PanelId
import io.archinaut.tenter.text.CellWidth

/**
 * Application invariants over [Keybindings.DEFAULT]'s [io.archinaut.tenter.input.KeyMap]. Structural keymap
 * validation belongs to [io.archinaut.tenter.input.KeyMap]'s constructor; these tests retain the TUI policies
 * that the reusable toolkit cannot know, plus behavior-specific default binding coverage.
 */
internal class KeybindingsTest {

    private val map = Keybindings.DEFAULT.map()

    private val chromeChords = map.layer(ContextId.CHROME).bindings.map { it.chord }.toSet()

    @Test
    fun `CHROME's chords never collide with a non-shadowing context's chords`() {
        for (context in ContextId.entries) {
            if (context == ContextId.CHROME) continue
            val layer = map.layer(context)
            if (context in SHADOWING_CONTEXTS) continue
            val collisions = layer.bindings.map { it.chord }.filter { it in chromeChords }
            assertTrue(collisions.isEmpty(), "CHROME collides with $context on: $collisions")
        }
    }

    /**
     * A shadowing context licenses overriding a *phase* context, which sits below CHROME in
     * `runLoop.activeContexts` — it is not a licence to shadow CHROME itself, which every
     * shadowing layer precedes. Without this, a future PANEL_SCROLL binding on `Home` or `h`
     * would silently steal recenter/pan whenever a side panel was focused, and the test above
     * would skip it.
     */
    @Test
    fun `a shadowing context never shadows CHROME — it exists to override phase contexts only`() {
        for (context in ContextId.entries) {
            if (context == ContextId.CHROME) continue
            val layer = map.layer(context)
            if (context !in SHADOWING_CONTEXTS) continue
            val collisions = layer.bindings.map { it.chord }.filter { it in chromeChords }
            assertTrue(
                collisions.isEmpty(),
                "shadowing context $context precedes CHROME and would steal: $collisions",
            )
        }
    }

    @Test
    fun `every declared context has a layer`() {
        assertEquals(ContextId.entries.toSet(), map.contexts)
    }

    @Test
    fun `every binding's action matches its context's declared action family`() {
        for (context in ContextId.entries) {
            for (binding in map.layer(context).bindings) {
                val ok = when (context) {
                    ContextId.CHROME -> binding.action is ChromeAction || binding.action is PanAction
                    ContextId.GAME_CHROME -> binding.action is ChromeAction || binding.action is PanAction
                    ContextId.PANEL_SCROLL -> binding.action is ScrollAction
                    ContextId.MOVEMENT_IDLE, ContextId.WEAPON_IDLE, ContextId.PHYSICAL_IDLE -> binding.action is IdleAction
                    ContextId.BROWSING -> binding.action is BrowsingAction
                    ContextId.FACING -> binding.action is FacingAction
                    ContextId.WEAPON_DECLARING, ContextId.PHYSICAL_DECLARING -> binding.action is AttackAction
                    ContextId.SETUP -> binding.action is SetupAction || binding.action is ChromeAction || binding.action is PanAction
                }
                assertTrue(ok, "binding for ${binding.chord} in $context has action ${binding.action} of the wrong family")
            }
        }
    }

    @Test
    fun `every hint label is exactly one cell per codepoint`() {
        for (context in ContextId.entries) {
            for (group in map.layer(context).hintGroups) {
                assertEquals(
                    group.label.codePointCount(0, group.label.length),
                    CellWidth.of(group.label),
                    "'${group.label}' (for '${group.description}') contains a non-width-1 codepoint",
                )
            }
        }
    }

    @Test
    fun `characterisation - default chrome bindings`() {
        val keys = Keybindings.DEFAULT

        assertEquals(ChromeAction.FocusPanel(GamePanelId.BOARD), keys.resolve(listOf(ContextId.GAME_CHROME), KeyboardEvent("0")))
        assertEquals(ChromeAction.ToggleHelp, keys.resolve(listOf(ContextId.CHROME), KeyboardEvent("?")))
        assertEquals(PanAction.Pan(PanAction.Direction.LEFT), keys.resolve(listOf(ContextId.GAME_CHROME), KeyboardEvent("h")))
        assertEquals(PanAction.Recenter, keys.resolve(listOf(ContextId.GAME_CHROME), KeyboardEvent("Home")))
        assertEquals(ChromeAction.Quit, keys.resolve(listOf(ContextId.CHROME), KeyboardEvent("c", ctrl = true)))
        assertEquals(ChromeAction.CycleState(1), keys.resolve(listOf(ContextId.CHROME), KeyboardEvent("+")))
        // Posix reports "?" with shift = false, Windows with shift = true (see shiftedPunctuation's
        // KDoc). Both are declared bindings, not one folded into the other, so both resolve alike.
        assertEquals(
            keys.resolve(listOf(ContextId.CHROME), KeyboardEvent("?")),
            keys.resolve(listOf(ContextId.CHROME), KeyboardEvent("?", shift = true)),
        )
    }

    @Test
    fun `characterisation - default phase bindings`() {
        val keys = Keybindings.DEFAULT

        assertEquals(IdleAction.CommitDeclarations, keys.resolve(listOf(ContextId.MOVEMENT_IDLE), KeyboardEvent("c")))
        assertEquals(BrowsingAction.CycleMode, keys.resolve(listOf(ContextId.BROWSING), KeyboardEvent("x")))
        assertEquals(FacingAction.SelectFacing(HexDirection.SE), keys.resolve(listOf(ContextId.FACING), KeyboardEvent("d")))
        assertEquals(BrowsingAction.MoveCursor(HexDirection.SE), keys.resolve(listOf(ContextId.BROWSING), KeyboardEvent("d")))
        assertEquals(BrowsingAction.SelectFacing(HexDirection.SE), keys.resolve(listOf(ContextId.BROWSING), KeyboardEvent("D", shift = true)))
        assertEquals(AttackAction.ToggleWeapon, keys.resolve(listOf(ContextId.WEAPON_DECLARING), KeyboardEvent(" ")))
        assertEquals(AttackAction.ToggleWeapon, keys.resolve(listOf(ContextId.PHYSICAL_DECLARING), KeyboardEvent(" ")))
    }

    @Test
    fun `characterisation - setup maximized-view pan bindings`() {
        val keys = Keybindings.DEFAULT

        assertEquals(PanAction.Pan(PanAction.Direction.LEFT), keys.resolve(listOf(ContextId.SETUP), KeyboardEvent("h")))
        assertEquals(PanAction.Pan(PanAction.Direction.DOWN), keys.resolve(listOf(ContextId.SETUP), KeyboardEvent("j")))
        assertEquals(PanAction.Pan(PanAction.Direction.UP), keys.resolve(listOf(ContextId.SETUP), KeyboardEvent("k")))
        assertEquals(PanAction.Pan(PanAction.Direction.RIGHT), keys.resolve(listOf(ContextId.SETUP), KeyboardEvent("l")))
        assertEquals(
            PanAction.Pan(PanAction.Direction.RIGHT),
            keys.resolve(listOf(ContextId.SETUP), KeyboardEvent("ArrowRight", ctrl = true)),
        )
    }

    /**
     * The badge doubles as the user-facing focus chord (`Alt+<badge>` for every panel but HELP,
     * whose badge — `?` — is the whole chord) and the bordered decoration badge — now sourced from
     * the keymap (see [Keybindings.badgeFor]) instead of a field on [GamePanelId]. Pinning these
     * values guards against a future binding change silently remapping which panel each keystroke
     * acts on.
     */
    @Test
    fun `badgeFor returns the stable per-panel badge, and every badge is unique`() {
        val keys = Keybindings.DEFAULT

        assertEquals('0', keys.badgeFor(ChromeAction.FocusPanel(GamePanelId.BOARD)))
        assertEquals('1', keys.badgeFor(ChromeAction.FocusPanel(GamePanelId.UNIT_STATUS)))
        assertEquals('2', keys.badgeFor(ChromeAction.FocusPanel(GamePanelId.DECLARED_TARGETS)))
        assertEquals('3', keys.badgeFor(ChromeAction.FocusPanel(GamePanelId.TARGETS)))
        assertEquals('4', keys.badgeFor(ChromeAction.FocusPanel(GamePanelId.TARGET_STATUS)))
        assertEquals('5', keys.badgeFor(ChromeAction.FocusPanel(GamePanelId.ATTACK_RESULTS)))
        assertEquals('9', keys.badgeFor(ChromeAction.FocusPanel(GamePanelId.LOG)))
        assertEquals('?', keys.badgeFor(ChromeAction.ToggleHelp))

        val badges = GamePanelId.entries.map { keys.badgeFor(focusActionFor(it)) }
        assertEquals(badges.size, badges.toSet().size, "duplicate badge would let one chord ambiguously resolve two panels")
    }

    @Test
    fun `badgeFor returns the stable per-panel badge for the setup screen too`() {
        val keys = Keybindings.DEFAULT

        assertEquals('1', keys.badgeFor(ChromeAction.FocusPanel(SetupPanelId.MODE)))
        assertEquals('2', keys.badgeFor(ChromeAction.FocusPanel(SetupPanelId.MAP)))
        assertEquals('3', keys.badgeFor(ChromeAction.FocusPanel(SetupPanelId.PLAYER_1)))
        assertEquals('4', keys.badgeFor(ChromeAction.FocusPanel(SetupPanelId.PLAYER_2)))
        assertEquals('?', keys.badgeFor(ChromeAction.ToggleHelp))

        val badges = SetupPanelId.entries.map { keys.badgeFor(focusActionFor(it)) }
        assertEquals(badges.size, badges.toSet().size, "duplicate badge would let one chord ambiguously resolve two panels")
    }

    /** What each panel builder passes to `badgeFor`: `?` reaches HELP, every other panel its own focus chord. */
    private fun focusActionFor(panel: PanelId): ChromeAction =
        if (panel == GamePanelId.HELP || panel == SetupPanelId.HELP) {
            ChromeAction.ToggleHelp
        } else {
            ChromeAction.FocusPanel(panel)
        }
}
