package io.archinaut.tenter.input

import com.github.ajalt.mordant.input.KeyboardEvent
import java.util.Collections
import java.util.LinkedHashMap
import java.util.LinkedHashSet

/**
 * Every keyboard binding in the application, as data, addressed by a context key [C].
 *
 * Resolution is a first-match-wins walk over the contexts the caller says are active *right now*.
 * The keymap deliberately has no notion of which contexts those are: "is a panel focused", "has the
 * match ended" are application state, not binding facts. [layers] is copied at construction, and
 * its captured action values and chords must remain stable in equality and [InputAction.id].
 *
 * Construction validates the structural contract for every layer: exact chords are unique within a
 * layer, references resolve to the layer's own help groups or to exactly one titled owner for a
 * sectionless layer, and every non-[HintGroup.bindingless] group is credited by a binding. This
 * makes a consumer's configuration safe to use without copying those validation loops.
 *
 * A sectionless layer does not produce a help section. Its bindings can credit a group on exactly
 * one titled layer; this is useful for precedence-only layers such as panel scrolling.
 */
public class KeyMap<C : Any>(layers: Map<C, KeyLayer>) {

    private val layers: Map<C, KeyLayer> = snapshot(layers)
    private val contextSet: Set<C> = Collections.unmodifiableSet(LinkedHashSet(this.layers.keys))

    init {
        validate()
    }

    /** Declared contexts in the supplied map's iteration order. The set is an immutable snapshot. */
    public val contexts: Set<C> get() = contextSet

    /** Returns a declared layer, or [IllegalArgumentException] for an undeclared context. */
    public fun layer(context: C): KeyLayer =
        layers[context] ?: throw IllegalArgumentException("No key layer declared for context $context")

    /**
     * The action bound to [event] by the first layer in [active] that binds its chord, else null.
     * Every context in [active] must be declared; an unknown active context is an argument error.
     */
    public fun resolve(active: List<C>, event: KeyboardEvent): InputAction? {
        for (context in active) {
            val hit = layer(context).bindings.firstOrNull { it.chord == event }
            if (hit != null) return hit.action
        }
        return null
    }

    /**
     * [context]'s help section, one row per declared [HintGroup], in declaration order.
     * Sectionless contexts have no help section and cause [IllegalArgumentException].
     */
    public fun hints(context: C): KeySection {
        val layer = layer(context)
        val title = layer.title
            ?: throw IllegalArgumentException("Context $context declares no title and renders no help section")
        return KeySection(title, layer.hintGroups.map { KeyHint(it.label, it.description) })
    }

    /** Every chord bound to [action], across every layer. Used to derive labels such as a panel badge. */
    public fun chordsFor(action: InputAction): List<KeyboardEvent> =
        layers.values.flatMap { layer -> layer.bindings.filter { it.action == action }.map { it.chord } }

    private fun validate(): Unit {
        val titledOwners = linkedMapOf<String, MutableList<C>>()

        for ((context, layer) in layers) {
            validateLayer(context, layer)
            if (layer.title != null) {
                for (group in layer.hintGroups) {
                    titledOwners.getOrPut(group.id) { mutableListOf() }.add(context)
                }
            }
        }

        val creditedGroups = mutableSetOf<Pair<C, String>>()
        for ((context, layer) in layers) {
            for (binding in layer.bindings) {
                if (layer.title != null) {
                    require(layer.hintGroups.any { it.id == binding.hintGroup }) {
                        "Key layer '$context' binding for ${binding.chord} references hint group " +
                            "'${binding.hintGroup}', which is not declared by that titled layer"
                    }
                    creditedGroups += context to binding.hintGroup
                } else {
                    val owners = titledOwners[binding.hintGroup].orEmpty()
                    require(owners.size == 1) {
                        val ownerText = when {
                            owners.isEmpty() -> "no titled layer"
                            else -> "titled layers ${owners.joinToString()}"
                        }
                        "Sectionless key layer '$context' binding for ${binding.chord} references hint " +
                            "group '${binding.hintGroup}', but it resolves to $ownerText"
                    }
                    creditedGroups += owners.single() to binding.hintGroup
                }
            }
        }

        for ((context, layer) in layers) {
            for (group in layer.hintGroups) {
                require(group.bindingless || context to group.id in creditedGroups) {
                    "Key layer '$context' hint group '${group.id}' is orphaned; add a binding credit " +
                        "or set bindingless=true"
                }
            }
        }
    }

    private fun validateLayer(context: C, layer: KeyLayer): Unit {
        val duplicateChords = layer.bindings.groupBy { it.chord }.filterValues { it.size > 1 }.keys
        require(duplicateChords.isEmpty()) {
            "Key layer '$context' contains duplicate exact chord(s): ${duplicateChords.joinToString()}"
        }

        val groupIds = layer.hintGroups.map { it.id }
        for (group in layer.hintGroups) {
            require(group.id.isNotBlank()) {
                "Key layer '$context' has a hint group with a blank id"
            }
        }
        require(groupIds.size == groupIds.toSet().size) {
            "Key layer '$context' contains duplicate hint group id(s): ${groupIds.joinToString()}"
        }
        for (binding in layer.bindings) {
            require(binding.hintGroup.isNotBlank()) {
                "Key layer '$context' binding for ${binding.chord} has a blank hint group reference"
            }
        }
    }

    private fun snapshot(source: Map<C, KeyLayer>): Map<C, KeyLayer> {
        val copied = LinkedHashMap<C, KeyLayer>(source.size)
        for ((context, layer) in source) {
            copied[context] = KeyLayer(
                title = layer.title,
                bindings = Collections.unmodifiableList(ArrayList(layer.bindings)),
                hintGroups = Collections.unmodifiableList(ArrayList(layer.hintGroups)),
            )
        }
        return Collections.unmodifiableMap(copied)
    }
}
