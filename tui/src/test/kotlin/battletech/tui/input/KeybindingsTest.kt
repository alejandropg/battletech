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
 * Only [ContextId.CHROME] and [ContextId.PANEL_SCROLL] exist yet; the seven phase contexts join
 * these invariants (and invariant 2, total context coverage) once they land.
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
    fun `every binding's hint group is declared, and every declared group is used or bindingless`() {
        val declaredIds = map.allHintGroups().map { it.id }
        assertEquals(declaredIds.toSet().size, declaredIds.size, "duplicate hint group ids: $declaredIds")

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
}
