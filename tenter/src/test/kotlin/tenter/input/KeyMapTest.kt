package tenter.input

import com.github.ajalt.mordant.input.KeyboardEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class KeyMapTest {

    private enum class Ctx { FIRST, SECOND }

    private data class TestAction(override val id: String) : InputAction

    private val actionA = TestAction("a")
    private val actionB = TestAction("b")

    @Nested
    inner class ResolveTest {
        @Test
        fun `first active layer wins when two layers bind the same chord`() {
            val chord = KeyboardEvent("x")
            val map = KeyMap(
                mapOf(
                    Ctx.FIRST to KeyLayer(title = "FIRST", bindings = listOf(KeyBinding(chord, actionA, "group"))),
                    Ctx.SECOND to KeyLayer(title = "SECOND", bindings = listOf(KeyBinding(chord, actionB, "group"))),
                ),
            )

            assertEquals(actionA, map.resolve(listOf(Ctx.FIRST, Ctx.SECOND), KeyboardEvent("x")))
        }

        @Test
        fun `a later layer's binding is not consulted when an earlier layer already binds the chord`() {
            val chord = KeyboardEvent("x")
            val map = KeyMap(
                mapOf(
                    Ctx.FIRST to KeyLayer(title = "FIRST", bindings = listOf(KeyBinding(chord, actionA, "group"))),
                    Ctx.SECOND to KeyLayer(title = "SECOND", bindings = listOf(KeyBinding(chord, actionB, "group"))),
                ),
            )

            assertEquals(actionA, map.resolve(listOf(Ctx.FIRST, Ctx.SECOND), KeyboardEvent("x")))
            assertTrue(map.resolve(listOf(Ctx.FIRST, Ctx.SECOND), KeyboardEvent("x")) != actionB)
        }

        @Test
        fun `falls through to a later layer when the earlier layer has no binding for the chord`() {
            val map = KeyMap(
                mapOf(
                    Ctx.FIRST to KeyLayer(title = "FIRST", bindings = emptyList()),
                    Ctx.SECOND to KeyLayer(title = "SECOND", bindings = listOf(KeyBinding(KeyboardEvent("x"), actionB, "group"))),
                ),
            )

            assertEquals(actionB, map.resolve(listOf(Ctx.FIRST, Ctx.SECOND), KeyboardEvent("x")))
        }

        @Test
        fun `unknown chord resolves to null`() {
            val map = KeyMap(mapOf(Ctx.FIRST to KeyLayer(title = "FIRST", bindings = emptyList())))

            assertNull(map.resolve(listOf(Ctx.FIRST), KeyboardEvent("z")))
        }
    }

    @Nested
    inner class ChordsForTest {
        @Test
        fun `finds every chord bound to an action across every layer`() {
            val map = KeyMap(
                mapOf(
                    Ctx.FIRST to KeyLayer(title = "FIRST", bindings = listOf(KeyBinding(KeyboardEvent("x"), actionA, "group"))),
                    Ctx.SECOND to KeyLayer(title = "SECOND", bindings = listOf(KeyBinding(KeyboardEvent("y"), actionA, "group"))),
                ),
            )

            assertEquals(setOf(KeyboardEvent("x"), KeyboardEvent("y")), map.chordsFor(actionA).toSet())
        }
    }

    @Nested
    inner class HintsTest {
        @Test
        fun `maps hint groups to rows in declaration order`() {
            val groups = listOf(
                HintGroup("first", "F", "first thing"),
                HintGroup("second", "S", "second thing"),
            )
            val map = KeyMap(mapOf(Ctx.FIRST to KeyLayer(title = "TITLE", bindings = emptyList(), hintGroups = groups)))

            val section = map.hints(Ctx.FIRST)

            assertEquals("TITLE", section.title)
            assertEquals(listOf(KeyHint("F", "first thing"), KeyHint("S", "second thing")), section.hints)
        }

        @Test
        fun `a null-titled layer throws when asked for hints`() {
            val map = KeyMap(mapOf(Ctx.FIRST to KeyLayer(title = null, bindings = emptyList())))

            assertThrows(IllegalStateException::class.java) { map.hints(Ctx.FIRST) }
        }
    }

    @Nested
    inner class NormalizedTest {
        @Test
        fun `alt+H normalizes to the same chord as alt+h`() {
            assertEquals(
                KeyboardEvent("h", alt = true).normalized(),
                KeyboardEvent("H", alt = true, shift = true).normalized(),
            )
        }

        @Test
        fun `a named key keeps its reported shift state`() {
            assertEquals(
                KeyboardEvent("ArrowUp", shift = true),
                KeyboardEvent("ArrowUp", shift = true).normalized(),
            )
        }

        @Test
        fun `normalized is idempotent, so every resolvable chord is bindable`() {
            // KeyMap matches against normalized() output and KeyBinding only accepts chords already
            // in that form. This pins the two sets as one: a chord normalized() can produce must
            // always be one a binding can declare, or a keystroke would be unbindable.
            val events = listOf(
                KeyboardEvent("H", alt = true, shift = true),
                KeyboardEvent("h"),
                KeyboardEvent("+"),
                KeyboardEvent(" "),
                KeyboardEvent("ArrowUp", shift = true),
                KeyboardEvent("PageDown", ctrl = true),
            )

            for (event in events) {
                val chord = event.normalized()
                assertEquals(chord, chord.normalized())
                KeyBinding(chord, actionA, "group") // must not throw
            }
        }
    }

    @Nested
    inner class BindingValidationTest {
        @Test
        fun `an uppercase single-character chord is rejected rather than left silently dead`() {
            assertThrows(IllegalArgumentException::class.java) {
                KeyBinding(KeyboardEvent("H", alt = true), actionA, "group")
            }
        }

        @Test
        fun `a shifted printable chord is rejected — the character already encodes the shift`() {
            assertThrows(IllegalArgumentException::class.java) {
                KeyBinding(KeyboardEvent("h", shift = true), actionA, "group")
            }
        }

        @Test
        fun `an empty key is rejected`() {
            assertThrows(IllegalArgumentException::class.java) {
                KeyBinding(KeyboardEvent(""), actionA, "group")
            }
        }
    }
}
