package battletech.tui.input

import battletech.tui.game.GamePanelId
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

    @Test
    fun `CHROME's chords never collide with a non-shadowing context's chords`() {
        val chromeChords = map.layer(ContextId.CHROME).bindings.map { it.chord }.toSet()
        for (context in ContextId.entries) {
            if (context == ContextId.CHROME) continue
            val layer = map.layer(context)
            if (layer.shadowing) continue
            val collisions = layer.bindings.map { it.chord }.filter { it in chromeChords }
            assertTrue(collisions.isEmpty(), "CHROME collides with $context on: $collisions")
        }
    }

    @Test
    fun `every declared context has a layer`() {
        assertEquals(ContextId.entries.toSet(), map.contexts)
    }

    @Test
    fun `every binding's hint group is declared, and every declared group is used or bindingless`() {
        // Uniqueness is per layer, not global: MOVEMENT_IDLE/WEAPON_IDLE/PHYSICAL_IDLE (and
        // BROWSING/FACING) intentionally reuse ids like "moveCursor"/"cancel"/"cycleUnit" — they
        // are never active at the same time, and each layer's own hints() reads only its own list.
        for (context in ContextId.entries) {
            val layerIds = map.layer(context).hintGroups.map { it.id }
            assertEquals(layerIds.toSet().size, layerIds.size, "duplicate hint group ids within $context: $layerIds")
        }

        val declaredIds = map.allHintGroups().map { it.id }
        val (_, bindings) = map.allBindings().unzip()
        val boundGroupIds = bindings.map { it.hintGroup }.toSet()
        for (binding in bindings) {
            assertTrue(
                binding.hintGroup in declaredIds,
                "binding for ${binding.chord} credits undeclared hint group '${binding.hintGroup}'",
            )
        }
        for (group in map.allHintGroups()) {
            assertTrue(
                group.bindingless || group.id in boundGroupIds,
                "hint group '${group.id}' is declared but no binding credits it, and it is not bindingless",
            )
        }
    }

    @Test
    fun `every binding's action matches its context's declared action family`() {
        for ((context, binding) in map.allBindings()) {
            val ok = when (context) {
                ContextId.CHROME -> binding.action is ChromeAction || binding.action is PanAction
                ContextId.PANEL_SCROLL -> binding.action is ScrollAction
                ContextId.MOVEMENT_IDLE, ContextId.WEAPON_IDLE, ContextId.PHYSICAL_IDLE -> binding.action is IdleAction
                ContextId.BROWSING -> binding.action is BrowsingAction
                ContextId.FACING -> binding.action is FacingAction
                ContextId.WEAPON_DECLARING, ContextId.PHYSICAL_DECLARING -> binding.action is AttackAction
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

        assertEquals(ChromeAction.FocusPanel(GamePanelId.BOARD), keys.resolve(listOf(ContextId.CHROME), KeyboardEvent("0", alt = true)))
        assertEquals(ChromeAction.ToggleHelp, keys.resolve(listOf(ContextId.CHROME), KeyboardEvent("h", alt = true)))
        assertEquals(PanAction.Pan(PanAction.Direction.LEFT), keys.resolve(listOf(ContextId.CHROME), KeyboardEvent("h")))
        assertEquals(PanAction.Recenter, keys.resolve(listOf(ContextId.CHROME), KeyboardEvent("Home")))
        assertEquals(ChromeAction.Quit, keys.resolve(listOf(ContextId.CHROME), KeyboardEvent("c", ctrl = true)))
        assertEquals(ChromeAction.CycleState(1), keys.resolve(listOf(ContextId.CHROME), KeyboardEvent("+")))
        assertEquals(
            keys.resolve(listOf(ContextId.CHROME), KeyboardEvent("h", alt = true)),
            keys.resolve(listOf(ContextId.CHROME), KeyboardEvent("H", alt = true, shift = true)),
        )
    }

    @Test
    fun `characterisation - default phase bindings`() {
        val keys = Keybindings.DEFAULT

        assertEquals(IdleAction.CommitDeclarations, keys.resolve(listOf(ContextId.MOVEMENT_IDLE), KeyboardEvent("c")))
        assertEquals(BrowsingAction.CycleMode, keys.resolve(listOf(ContextId.BROWSING), KeyboardEvent("x")))
        assertEquals(FacingAction.SelectFacing(3), keys.resolve(listOf(ContextId.FACING), KeyboardEvent("3")))
        assertEquals(AttackAction.ToggleWeapon, keys.resolve(listOf(ContextId.WEAPON_DECLARING), KeyboardEvent(" ")))
        assertEquals(AttackAction.ToggleWeapon, keys.resolve(listOf(ContextId.PHYSICAL_DECLARING), KeyboardEvent(" ")))
    }

    /**
     * The badge doubles as the user-facing `Alt+<badge>` chord and the bordered decoration badge —
     * now sourced from the keymap (see [Keybindings.badgeFor]) instead of a field on [GamePanelId].
     * Pinning these values guards against a future binding change silently remapping which panel
     * each keystroke acts on.
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
        assertEquals('h', keys.badgeFor(GamePanelId.HELP))

        val badges = GamePanelId.entries.map { keys.badgeFor(it) }
        assertEquals(badges.size, badges.toSet().size, "duplicate badge would let one chord ambiguously resolve two panels")
    }
}
