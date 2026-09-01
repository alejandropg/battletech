package battletech.tui.input

import battletech.tactical.model.HexDirection
import battletech.tui.game.GamePanelId
import battletech.tui.setup.SetupAction
import battletech.tui.setup.SetupPanelId
import com.github.ajalt.mordant.input.KeyboardEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.input.PanAction
import tenter.input.ScrollAction
import tenter.text.CellWidth

/**
 * Invariants over [Keybindings.DEFAULT]'s [tenter.input.KeyMap] — enforced here rather than left as
 * prose, so a binding change that breaks one of them fails the build instead of drifting silently.
 */
internal class KeybindingsTest {

    private val map = Keybindings.DEFAULT.map()

    private val chromeChords = map.layer(ContextId.CHROME).bindings.map { it.chord }.toSet()

    @Test
    fun `CHROME's chords never collide with a non-shadowing context's chords`() {
        for (context in ContextId.entries) {
            if (context == ContextId.CHROME) continue
            val layer = map.layer(context)
            if (layer.shadowing) continue
            val collisions = layer.bindings.map { it.chord }.filter { it in chromeChords }
            assertTrue(collisions.isEmpty(), "CHROME collides with $context on: $collisions")
        }
    }

    /**
     * `shadowing` licenses overriding a *phase* context, which sits below CHROME in
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
            if (!layer.shadowing) continue
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

    /**
     * Hint-group ids are resolved PER LAYER, not across the whole map, because `hints(context)`
     * renders only that layer's own list. Checking them globally would let both failures this
     * invariant exists to catch slip through: a binding crediting a group its own layer doesn't
     * declare (an undocumented binding, since the row it names renders in some other section),
     * and an orphaned row in one layer excused by a same-id binding in another (a help row that
     * lies about what is bound).
     *
     * The one legitimate cross-layer credit is a layer that renders no section of its own
     * (`title == null`) — PANEL_SCROLL's bindings are documented by CHROME's `scrollFocused` row,
     * which is what keeps the GLOBAL section's row order intact. That is the only exemption.
     */
    @Test
    fun `every binding's hint group is declared by its own layer, and every declared group is used or bindingless`() {
        // Ids repeat ACROSS layers on purpose: MOVEMENT_IDLE/WEAPON_IDLE/PHYSICAL_IDLE (and
        // BROWSING/FACING) reuse "moveCursor"/"cancel"/"cycleUnit". They are never active at the
        // same time and each layer reads only its own list, so uniqueness is a per-layer rule.
        for (context in ContextId.entries) {
            val layerIds = map.layer(context).hintGroups.map { it.id }
            assertEquals(layerIds.toSet().size, layerIds.size, "duplicate hint group ids within $context: $layerIds")
        }

        val allDeclaredIds = map.allHintGroups().map { it.id }.toSet()
        // Bindings on a section-less layer are documented by whichever layer declares their group.
        val creditsFromSectionlessLayers = ContextId.entries
            .filter { map.layer(it).title == null }
            .flatMap { map.layer(it).bindings }
            .map { it.hintGroup }
            .toSet()

        for ((context, binding) in map.allBindings()) {
            val layer = map.layer(context)
            val ownIds = layer.hintGroups.map { it.id }.toSet()
            val ok = if (layer.title == null) binding.hintGroup in allDeclaredIds else binding.hintGroup in ownIds
            assertTrue(
                ok,
                "binding for ${binding.chord} in $context credits hint group '${binding.hintGroup}', " +
                    "which $context does not declare — hints($context) would never render it",
            )
        }

        for (context in ContextId.entries) {
            val layer = map.layer(context)
            val ownCredits = layer.bindings.map { it.hintGroup }.toSet()
            for (group in layer.hintGroups) {
                assertTrue(
                    group.bindingless || group.id in ownCredits || group.id in creditsFromSectionlessLayers,
                    "hint group '${group.id}' declared by $context is credited by no binding in $context " +
                        "nor by any section-less layer, and it is not bindingless — it would render a row " +
                        "describing keys that are not bound",
                )
            }
        }
    }

    @Test
    fun `every binding's action matches its context's declared action family`() {
        for ((context, binding) in map.allBindings()) {
            val ok = when (context) {
                ContextId.CHROME -> binding.action is ChromeAction || binding.action is PanAction
                ContextId.GAME_CHROME -> binding.action is ChromeAction || binding.action is PanAction
                ContextId.PANEL_SCROLL -> binding.action is ScrollAction
                ContextId.MOVEMENT_IDLE, ContextId.WEAPON_IDLE, ContextId.PHYSICAL_IDLE -> binding.action is IdleAction
                ContextId.BROWSING -> binding.action is BrowsingAction
                ContextId.FACING -> binding.action is FacingAction
                ContextId.WEAPON_DECLARING, ContextId.PHYSICAL_DECLARING -> binding.action is AttackAction
                ContextId.SETUP -> binding.action is SetupAction || binding.action is ChromeAction
            }
            assertTrue(ok, "binding for ${binding.chord} in $context has action ${binding.action} of the wrong family")
        }
    }

    @Test
    fun `every hint label is exactly one cell per codepoint`() {
        for (group in map.allHintGroups()) {
            assertEquals(
                group.label.codePointCount(0, group.label.length),
                CellWidth.of(group.label),
                "'${group.label}' (for '${group.description}') contains a non-width-1 codepoint",
            )
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

        assertEquals('0', keys.badgeFor(GamePanelId.BOARD))
        assertEquals('1', keys.badgeFor(GamePanelId.UNIT_STATUS))
        assertEquals('2', keys.badgeFor(GamePanelId.DECLARED_TARGETS))
        assertEquals('3', keys.badgeFor(GamePanelId.TARGETS))
        assertEquals('4', keys.badgeFor(GamePanelId.TARGET_STATUS))
        assertEquals('5', keys.badgeFor(GamePanelId.ATTACK_RESULTS))
        assertEquals('9', keys.badgeFor(GamePanelId.LOG))
        assertEquals('?', keys.badgeFor(GamePanelId.HELP))

        val badges = GamePanelId.entries.map { keys.badgeFor(it) }
        assertEquals(badges.size, badges.toSet().size, "duplicate badge would let one chord ambiguously resolve two panels")
    }

    @Test
    fun `badgeFor returns the stable per-panel badge for the setup screen too`() {
        val keys = Keybindings.DEFAULT

        assertEquals('1', keys.badgeFor(SetupPanelId.MODE))
        assertEquals('2', keys.badgeFor(SetupPanelId.MAP))
        assertEquals('3', keys.badgeFor(SetupPanelId.PLAYER_1))
        assertEquals('4', keys.badgeFor(SetupPanelId.PLAYER_2))
        assertEquals('?', keys.badgeFor(SetupPanelId.HELP))

        val badges = SetupPanelId.entries.map { keys.badgeFor(it) }
        assertEquals(badges.size, badges.toSet().size, "duplicate badge would let one chord ambiguously resolve two panels")
    }
}
