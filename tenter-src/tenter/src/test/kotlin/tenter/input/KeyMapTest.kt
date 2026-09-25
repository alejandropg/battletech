package tenter.input

import com.github.ajalt.mordant.input.KeyboardEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class KeyMapTest {

    private enum class Ctx { FIRST, SECOND, THIRD }

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
                    Ctx.FIRST to titledLayer("FIRST", listOf(KeyBinding(chord, actionA, "group"))),
                    Ctx.SECOND to titledLayer("SECOND", listOf(KeyBinding(chord, actionB, "group"))),
                ),
            )

            assertEquals(actionA, map.resolve(listOf(Ctx.FIRST, Ctx.SECOND), KeyboardEvent("x")))
        }

        @Test
        fun `a later layer's binding is not consulted when an earlier layer already binds the chord`() {
            val chord = KeyboardEvent("x")
            val map = KeyMap(
                mapOf(
                    Ctx.FIRST to titledLayer("FIRST", listOf(KeyBinding(chord, actionA, "group"))),
                    Ctx.SECOND to titledLayer("SECOND", listOf(KeyBinding(chord, actionB, "group"))),
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
                    Ctx.SECOND to titledLayer("SECOND", listOf(KeyBinding(KeyboardEvent("x"), actionB, "group"))),
                ),
            )

            assertEquals(actionB, map.resolve(listOf(Ctx.FIRST, Ctx.SECOND), KeyboardEvent("x")))
        }

        @Test
        fun `unknown chord resolves to null`() {
            val map = KeyMap(mapOf(Ctx.FIRST to KeyLayer(title = "FIRST", bindings = emptyList())))

            assertNull(map.resolve(listOf(Ctx.FIRST), KeyboardEvent("z")))
        }

        @Test
        fun `case and shift are part of the chord, so Q and q resolve to different actions`() {
            val map = KeyMap(mapOf(Ctx.FIRST to KeyLayer(
                title = "T",
                bindings = listOf(
                    KeyBinding(KeyboardEvent("q"), actionA, "group"),
                    KeyBinding(KeyboardEvent("Q", shift = true), actionB, "group"),
                ),
                hintGroups = listOf(HintGroup("group", "q", "d")),
            )))

            assertEquals(actionA, map.resolve(listOf(Ctx.FIRST), KeyboardEvent("q")))
            assertEquals(actionB, map.resolve(listOf(Ctx.FIRST), KeyboardEvent("Q", shift = true)))
            assertNull(map.resolve(listOf(Ctx.FIRST), KeyboardEvent("Q")))
        }
    }

    @Nested
    inner class ChordsForTest {
        @Test
        fun `finds every chord bound to an action across every layer`() {
            val map = KeyMap(
                mapOf(
                    Ctx.FIRST to titledLayer("FIRST", listOf(KeyBinding(KeyboardEvent("x"), actionA, "group"))),
                    Ctx.SECOND to titledLayer("SECOND", listOf(KeyBinding(KeyboardEvent("y"), actionA, "group"))),
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
            val map = KeyMap(
                mapOf(
                    Ctx.FIRST to KeyLayer(
                        title = "TITLE",
                        bindings = listOf(
                            KeyBinding(KeyboardEvent("f"), actionA, "first"),
                            KeyBinding(KeyboardEvent("s"), actionB, "second"),
                        ),
                        hintGroups = groups,
                    ),
                ),
            )

            val section = map.hints(Ctx.FIRST)

            assertEquals("TITLE", section.title)
            assertEquals(listOf(KeyHint("F", "first thing"), KeyHint("S", "second thing")), section.hints)
        }

        @Test
        fun `a null-titled layer throws when asked for hints`() {
            val map = KeyMap(mapOf(Ctx.FIRST to KeyLayer(title = null, bindings = emptyList())))

            assertThrows(IllegalArgumentException::class.java) { map.hints(Ctx.FIRST) }
        }
    }

    @Nested
    inner class ConstructionValidationTest {
        @Test
        fun `rejects duplicate exact chords even when they name the same action`() {
            assertInvalid("duplicate exact chord") {
                KeyMap(
                    mapOf(
                        Ctx.FIRST to titledLayer(
                            "FIRST",
                            listOf(
                                KeyBinding(KeyboardEvent("x"), actionA, "group"),
                                KeyBinding(KeyboardEvent("x"), actionA, "group"),
                            ),
                        ),
                    ),
                )
            }
        }

        @Test
        fun `rejects blank binding references and hint ids`() {
            assertInvalid("blank hint group reference") {
                KeyMap(
                    mapOf(
                        Ctx.FIRST to KeyLayer(
                            title = "FIRST",
                            bindings = listOf(KeyBinding(KeyboardEvent("x"), actionA, " ")),
                        ),
                    ),
                )
            }

            assertInvalid("blank id") {
                KeyMap(
                    mapOf(
                        Ctx.FIRST to KeyLayer(
                            title = "FIRST",
                            bindings = emptyList(),
                            hintGroups = listOf(HintGroup(" ", "x", "blank")),
                        ),
                    ),
                )
            }
        }

        @Test
        fun `rejects duplicate hint group ids within one layer`() {
            assertInvalid("duplicate hint group id") {
                KeyMap(
                    mapOf(
                        Ctx.FIRST to KeyLayer(
                            title = "FIRST",
                            bindings = emptyList(),
                            hintGroups = listOf(
                                HintGroup("same", "a", "first", bindingless = true),
                                HintGroup("same", "b", "second", bindingless = true),
                            ),
                        ),
                    ),
                )
            }
        }

        @Test
        fun `rejects a titled binding that references an absent group`() {
            assertInvalid("not declared by that titled layer") {
                KeyMap(
                    mapOf(
                        Ctx.FIRST to KeyLayer(
                            title = "FIRST",
                            bindings = listOf(KeyBinding(KeyboardEvent("x"), actionA, "missing")),
                        ),
                    ),
                )
            }
        }

        @Test
        fun `rejects an orphan group unless it is explicitly bindingless`() {
            assertInvalid("orphaned") {
                KeyMap(
                    mapOf(
                        Ctx.FIRST to KeyLayer(
                            title = "FIRST",
                            bindings = emptyList(),
                            hintGroups = listOf(HintGroup("orphan", "o", "orphan")),
                        ),
                    ),
                )
            }

            KeyMap(
                mapOf(
                    Ctx.FIRST to KeyLayer(
                        title = "FIRST",
                        bindings = emptyList(),
                        hintGroups = listOf(HintGroup("wheel", "wheel", "scroll", bindingless = true)),
                    ),
                ),
            )
        }

        @Test
        fun `allows repeated group ids on separate titled layers when references are local`() {
            val map = KeyMap(
                mapOf(
                    Ctx.FIRST to titledLayer("FIRST", listOf(KeyBinding(KeyboardEvent("x"), actionA, "group"))),
                    Ctx.SECOND to titledLayer("SECOND", listOf(KeyBinding(KeyboardEvent("y"), actionB, "group"))),
                ),
            )

            assertEquals(actionA, map.resolve(listOf(Ctx.FIRST), KeyboardEvent("x")))
            assertEquals(actionB, map.resolve(listOf(Ctx.SECOND), KeyboardEvent("y")))
        }

        @Test
        fun `requires a sectionless binding to resolve to exactly one titled owner`() {
            val owner = titledLayer(
                "OWNER",
                emptyList(),
                hintGroups = listOf(HintGroup("shared", "s", "shared")),
            )
            val sectionless = KeyLayer(
                title = null,
                bindings = listOf(KeyBinding(KeyboardEvent("x"), actionA, "shared")),
            )
            val map = KeyMap(mapOf(Ctx.FIRST to owner, Ctx.SECOND to sectionless))
            assertEquals(actionA, map.resolve(listOf(Ctx.SECOND), KeyboardEvent("x")))

            assertInvalid("no titled layer") {
                KeyMap(mapOf(Ctx.SECOND to sectionless))
            }
            assertInvalid("titled layers") {
                KeyMap(
                    mapOf(
                        Ctx.FIRST to owner,
                        Ctx.SECOND to titledLayer(
                            "SECOND",
                            emptyList(),
                            hintGroups = listOf(HintGroup("shared", "s", "shared")),
                        ),
                        Ctx.THIRD to sectionless,
                    ),
                )
            }
        }

        @Test
        fun `copies source maps and lists and does not expose mutable captured collections`() {
            val sourceBindings = mutableListOf(KeyBinding(KeyboardEvent("x"), actionA, "group"))
            val sourceHints = mutableListOf(HintGroup("group", "x", "action"))
            val source = linkedMapOf<Ctx, KeyLayer>(
                Ctx.FIRST to KeyLayer("FIRST", sourceBindings, sourceHints),
            )
            val map = KeyMap(source)

            source.clear()
            sourceBindings.clear()
            sourceHints.clear()

            assertEquals(setOf(Ctx.FIRST), map.contexts)
            assertEquals(actionA, map.resolve(listOf(Ctx.FIRST), KeyboardEvent("x")))
            assertEquals(listOf(KeyHint("x", "action")), map.hints(Ctx.FIRST).hints)
            assertThrows(UnsupportedOperationException::class.java) {
                (map.layer(Ctx.FIRST).bindings as MutableList).clear()
            }
            assertThrows(UnsupportedOperationException::class.java) {
                (map.contexts as MutableSet).clear()
            }
        }

        @Test
        fun `allows an empty keymap and rejects unknown contexts as argument errors`() {
            val map = KeyMap<Ctx>(emptyMap())

            assertTrue(map.contexts.isEmpty())
            assertNull(map.resolve(emptyList(), KeyboardEvent("x")))
            assertThrows(IllegalArgumentException::class.java) {
                map.resolve(listOf(Ctx.FIRST), KeyboardEvent("x"))
            }
            assertThrows(IllegalArgumentException::class.java) {
                map.layer(Ctx.FIRST)
            }
        }
    }

    @Nested
    inner class BindingValidationTest {
        @Test
        fun `an uppercase single-character chord is a distinct chord from its lowercase form`() {
            KeyBinding(KeyboardEvent("H", alt = true), actionA, "group") // must not throw
        }

        @Test
        fun `a shifted printable chord is distinct from its unshifted form`() {
            KeyBinding(KeyboardEvent("h", shift = true), actionA, "group") // must not throw
        }

        @Test
        fun `an empty key is rejected`() {
            assertThrows(IllegalArgumentException::class.java) {
                KeyBinding(KeyboardEvent(""), actionA, "group")
            }
        }
    }

    private fun titledLayer(
        title: String,
        bindings: List<KeyBinding>,
        hintGroups: List<HintGroup> = listOf(HintGroup("group", "x", "test")),
    ): KeyLayer = KeyLayer(title = title, bindings = bindings, hintGroups = hintGroups)

    private fun assertInvalid(message: String, block: () -> Unit): Unit {
        val error = assertThrows(IllegalArgumentException::class.java, block)
        assertTrue(error.message.orEmpty().contains(message), "Expected '$message' in '${error.message}'")
    }
}
