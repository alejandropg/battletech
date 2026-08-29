package tenter.input

import com.github.ajalt.mordant.input.KeyboardEvent

/**
 * Every keyboard binding in the application, as data, addressed by a context key [C].
 *
 * Resolution is a first-match-wins walk over the contexts the caller says are active *right now*.
 * The keymap deliberately has no notion of which contexts those are: "is a panel focused", "has the
 * match ended" are application state, not binding facts, and keeping them out is what keeps this
 * whole object serialisable.
 *
 * A [HintGroup] may be declared on a different layer than the bindings crediting it — that is how
 * the scroll row appears in the GLOBAL section while its bindings live in a shadowing layer.
 */
public class KeyMap<C : Any>(private val layers: Map<C, KeyLayer>) {

    public val contexts: Set<C> get() = layers.keys

    public fun layer(context: C): KeyLayer =
        layers[context] ?: error("No key layer declared for context $context")

    /** The action bound to [event] by the first layer in [active] that binds its chord, else null. */
    public fun resolve(active: List<C>, event: KeyboardEvent): InputAction? {
        for (context in active) {
            val hit = layer(context).bindings.firstOrNull { it.chord == event }
            if (hit != null) return hit.action
        }
        return null
    }

    /** [context]'s help section, one row per declared [HintGroup], in declaration order. */
    public fun hints(context: C): KeySection {
        val layer = layer(context)
        val title = layer.title ?: error("Context $context declares no title and renders no help section")
        return KeySection(title, layer.hintGroups.map { KeyHint(it.label, it.description) })
    }

    /** Every chord bound to [action], across every layer. Used to derive labels such as a panel badge. */
    public fun chordsFor(action: InputAction): List<KeyboardEvent> =
        layers.values.flatMap { layer -> layer.bindings.filter { it.action == action }.map { it.chord } }

    /** Every binding in the map, for invariant tests. */
    public fun allBindings(): List<Pair<C, KeyBinding>> =
        layers.entries.flatMap { (context, layer) -> layer.bindings.map { context to it } }

    /** Every hint group declared anywhere, for invariant tests. */
    public fun allHintGroups(): List<HintGroup> = layers.values.flatMap { it.hintGroups }
}
