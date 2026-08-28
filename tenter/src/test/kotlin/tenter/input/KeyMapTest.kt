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
            val chord = KeyChord("x")
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
            val chord = KeyChord("x")
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
                    Ctx.SECOND to KeyLayer(title = "SECOND", bindings = listOf(KeyBinding(KeyChord("x"), actionB, "group"))),
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
                    Ctx.FIRST to KeyLayer(title = "FIRST", bindings = listOf(KeyBinding(KeyChord("x"), actionA, "group"))),
                    Ctx.SECOND to KeyLayer(title = "SECOND", bindings = listOf(KeyBinding(KeyChord("y"), actionA, "group"))),
                ),
            )

            assertEquals(setOf(KeyChord("x"), KeyChord("y")), map.chordsFor(actionA).toSet())
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
    inner class KeyChordOfTest {
        @Test
        fun `alt+H normalises to the same chord as alt+h`() {
            assertEquals(
                KeyChord.of(KeyboardEvent("h", alt = true)),
                KeyChord.of(KeyboardEvent("H", alt = true, shift = true)),
            )
        }

        @Test
        fun `a named key keeps its reported shift state`() {
            assertEquals(
                KeyChord("ArrowUp", shift = true),
                KeyChord.of(KeyboardEvent("ArrowUp", shift = true)),
            )
        }

        @Test
        fun `of only ever produces chords the constructor accepts`() {
            // The two are the same set by construction — this pins that they stay so, since a
            // chord `of` could not produce would be a binding that silently never fires.
            val events = listOf(
                KeyboardEvent("H", alt = true, shift = true),
                KeyboardEvent("h"),
                KeyboardEvent("+"),
                KeyboardEvent(" "),
                KeyboardEvent("ArrowUp", shift = true),
                KeyboardEvent("PageDown", ctrl = true),
            )

            for (event in events) {
                val chord = KeyChord.of(event)
                assertEquals(chord, KeyChord(chord.key, chord.ctrl, chord.alt, chord.shift))
            }
        }

        @Test
        fun `an uppercase single-character chord is rejected rather than left silently dead`() {
            assertThrows(IllegalArgumentException::class.java) { KeyChord("H", alt = true) }
        }

        @Test
        fun `a shifted printable chord is rejected — the character already encodes the shift`() {
            assertThrows(IllegalArgumentException::class.java) { KeyChord("h", shift = true) }
        }
    }
}
