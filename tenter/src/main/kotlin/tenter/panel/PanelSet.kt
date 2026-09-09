package tenter.panel

import java.util.Collections
import java.util.IdentityHashMap
import tenter.screen.Canvas
import tenter.view.Bordered
import tenter.view.ScrollOffset

/**
 * The coordinator for one screen's stateful panels. It owns focus and cross-panel transitions;
 * each [Panel] retains its own state and [tenter.view.ViewportState]. A panel instance is claimed
 * by exactly one set for its lifetime.
 */
public class PanelSet<K : PanelId, I> private constructor(
    /** The always-included derived-width panel, or null for a uniform set. */
    public val main: Panel<K, I>?,
    /** Panel declarations in layout order. The list is copied at construction. */
    public val sides: List<Panel<K, I>>,
) {
    private var pendingRecenter: K? = null
    private var lastLayout: PanelLayout<K, I>? = null
    private var focusedId: K? = main?.id ?: sides.firstOrNull()?.id

    /** The focused panel, or null when a uniform set's last frame had no visible panels. */
    public val focused: K? get() = focusedId

    private fun panelFor(id: K): Panel<K, I>? = if (id == main?.id) main else sides.firstOrNull { it.id == id }

    /** Focuses [id] if it names a panel in this set, demoting any other maximized panel. */
    public fun focus(id: K) {
        val panel = panelFor(id) ?: return
        allPanels().forEach { if (it !== panel) it.demoteFromMaximized() }
        focusedId = id
    }

    /** Focuses [id], or cycles it forward when it is already focused. Unknown id: no-op. */
    public fun focusOrCycle(id: K) {
        if (id == focusedId) {
            cycleFocusedState(1)
        } else {
            focus(id)
        }
    }

    /** Cycles the focused panel's declared state. */
    public fun cycleFocusedState(delta: Int) {
        val panel = focusedId?.let(::panelFor) ?: return
        val oldState = panel.state
        panel.cycleState(delta)
        if (panel.state == PanelState.MAXIMIZED && oldState != PanelState.MAXIMIZED) {
            allPanels().forEach { if (it !== panel) it.demoteFromMaximized() }
        }
    }

    public fun scrollFocused(dx: Int, dy: Int) {
        focusedId?.let(::panelFor)?.scrollBy(dx, dy)
    }

    /** Scrolls the focused panel by one viewport height; [direction] is -1 or +1. */
    public fun pageFocused(direction: Int) {
        val id = focusedId ?: return
        val panel = panelFor(id) ?: return
        val slotHeight = slotFor(id)?.height
        val page = if (slotHeight != null) {
            (slotHeight - Bordered.VIEWPORT_INSET.top - Bordered.VIEWPORT_INSET.bottom).coerceAtLeast(1)
        } else {
            1
        }
        panel.scrollBy(0, page * direction)
    }

    /** Mouse path: scrolls a specific panel regardless of focus. */
    public fun scroll(id: K, dx: Int, dy: Int) {
        panelFor(id)?.scrollBy(dx, dy)
    }

    /** One-shot: the named panel recenters on its reveal target at the next render. */
    public fun requestRecenter(id: K) {
        if (panelFor(id) != null) pendingRecenter = id
    }

    /** The SIDE panel at screen ([x], [y]), or null — the main panel is never returned. */
    public fun panelIdAt(x: Int, y: Int): K? = lastLayout?.sideAt(x, y)?.panel?.id

    /**
     * Returns the state observed for [id], or null for an unknown panel. This is an immutable
     * observation of managed state; callers cannot mutate a panel through it.
     */
    public fun stateOf(id: K): PanelState? = panelFor(id)?.state

    /** Returns the settled offset from the last drawable frame, or null if none exists/unknown. */
    public fun offsetOf(id: K): ScrollOffset? = panelFor(id)?.settledOffset()

    private fun slotFor(id: K): PanelLayout.Slot<K, I>? {
        val layout = lastLayout ?: return null
        if (layout.main?.panel?.id == id) return layout.main
        return layout.sides.firstOrNull { it.panel.id == id }
    }

    /**
     * Lays out [visible] panels, selecting each rendered presentation exactly once, then draws
     * every slot and returns the layout used. Width comes from that same selected presentation;
     * it is never recomputed by asking an application builder again.
     */
    public fun render(
        canvas: Canvas,
        inputs: I,
        visible: Set<K>,
        reservedTop: Int,
        uniformColumnCount: Int = visible.size,
        fixedWidthPanels: Set<K> = emptySet(),
    ): PanelLayout<K, I> {
        val visibleSides = sides.filter { it.id in visible }
        normalizeFocus(visibleSides)

        val maximizedPanel = visibleSides.firstOrNull { it.state == PanelState.MAXIMIZED }
        val renderedPanels = if (maximizedPanel != null) {
            listOf(maximizedPanel)
        } else {
            buildList {
                main?.let(::add)
                addAll(visibleSides)
            }
        }
        val presentations = renderedPanels.associateBy({ it.id }, { it.presentation(inputs) })
        val widthOf: (Panel<K, I>) -> Int = { panel -> presentations.getValue(panel.id).width }
        val fixedPanels = fixedWidthPanels + visibleSides
            .filter { it.state == PanelState.MINIMIZED }
            .map { it.id }

        val layout = if (main != null) {
            PanelLayout.compute(canvas.width, canvas.height, reservedTop, main, visibleSides, widthOf)
        } else {
            PanelLayout.computeUniform(
                canvas.width,
                canvas.height,
                reservedTop,
                visibleSides,
                uniformColumnCount,
                fixedPanels,
                widthOf,
            )
        }
        lastLayout = layout

        layout.main?.let { slot ->
            slot.panel.render(
                canvas.region(slot.x, slot.y, slot.width, slot.height),
                presentations.getValue(slot.panel.id),
                focused = slot.panel.id == focusedId,
                recenter = pendingRecenter == slot.panel.id,
            )
        }
        for (slot in layout.sides) {
            slot.panel.render(
                canvas.region(slot.x, slot.y, slot.width, slot.height),
                presentations.getValue(slot.panel.id),
                focused = slot.panel.id == focusedId,
                recenter = pendingRecenter == slot.panel.id,
            )
        }
        pendingRecenter = null

        return layout
    }

    private fun normalizeFocus(visibleSides: List<Panel<K, I>>) {
        if (main != null) {
            val visible = focusedId == main.id || visibleSides.any { it.id == focusedId }
            if (!visible) focus(main.id)
            return
        }

        if (focusedId !in visibleSides.map { it.id }) {
            visibleSides.firstOrNull()?.let { focus(it.id) } ?: run { focusedId = null }
        }
    }

    private fun allPanels(): List<Panel<K, I>> = buildList {
        main?.let(::add)
        addAll(sides)
    }

    public companion object {
        /** Builds a uniform set with at least one panel and copied declarations. */
        public fun <K : PanelId, I> uniform(panels: List<Panel<K, I>>): PanelSet<K, I> {
            require(panels.isNotEmpty()) { "A uniform PanelSet needs at least one panel" }
            return create(main = null, sides = panels, uniform = true)
        }

        /** Builds a derived-main set. The main panel may only declare NORMAL. */
        public fun <K : PanelId, I> mainAndSides(
            main: Panel<K, I>,
            sides: List<Panel<K, I>>,
        ): PanelSet<K, I> = create(main, sides, uniform = false)

        private fun <K : PanelId, I> create(
            main: Panel<K, I>?,
            sides: List<Panel<K, I>>,
            uniform: Boolean,
        ): PanelSet<K, I> {
            if (!uniform) require(main != null)
            if (main != null) {
                require(main.states == listOf(PanelState.NORMAL)) {
                    "Main panel ${main.id} must declare NORMAL only"
                }
            }

            val copiedSides = sides.toList()
            val all = buildList {
                main?.let(::add)
                addAll(copiedSides)
            }
            val ids = HashSet<K>()
            val identities = Collections.newSetFromMap(IdentityHashMap<Panel<K, I>, Boolean>())
            all.forEach { panel ->
                require(ids.add(panel.id)) { "Panel id ${panel.id} appears more than once in this PanelSet" }
                require(identities.add(panel)) { "Panel ${panel.id} appears more than once in this PanelSet" }
                panel.requireUnattached()
            }

            all.forEach { it.claimAttachment() }
            return PanelSet(main, copiedSides)
        }
    }
}
