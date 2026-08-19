package tenter.panel

import tenter.screen.Canvas
import tenter.view.Bordered
import tenter.view.ScrollOffset

/**
 * Every panel on one screen, plus the two invariants no single panel can hold: exactly one is
 * focused, and at most one is MAXIMIZED. [main] is the panel whose width is derived rather than
 * declared (the board) — it is always visible, always focusable, and never maximized.
 */
public class PanelSet<K : PanelId, I>(
    /** The main panel — read it for identity and declarations; every state change goes through this set. */
    public val main: Panel<K, I>,
    /** The side panels in layout order — as with [main], read-only from outside; mutate via this set. */
    public val sides: List<Panel<K, I>>,
) {
    private var pendingRecenter: K? = null
    private var lastLayout: PanelLayout<K, I>? = null

    public var focused: K = main.id
        private set

    private fun panelFor(id: K): Panel<K, I>? = if (id == main.id) main else sides.firstOrNull { it.id == id }

    /** Focus [id] if it names a panel in this set, demoting whatever was maximized. Unknown id: no-op. */
    public fun focus(id: K) {
        if (panelFor(id) == null) return
        sides.forEach { if (it.id != id) it.demoteFromMaximized() }
        focused = id
    }

    public fun cycleFocusedState(delta: Int) {
        panelFor(focused)?.cycleState(delta)
    }

    public fun scrollFocused(dx: Int, dy: Int) {
        panelFor(focused)?.scrollBy(dx, dy)
    }

    /** Scrolls the focused panel by one viewport height; [direction] is -1 (up) or +1 (down). */
    public fun pageFocused(direction: Int) {
        val panel = panelFor(focused) ?: return
        val slotHeight = slotFor(focused)?.height
        val page = if (slotHeight != null) {
            (slotHeight - Bordered.VIEWPORT_INSET.top - Bordered.VIEWPORT_INSET.bottom).coerceAtLeast(1)
        } else {
            1
        }
        panel.scrollBy(0, page * direction)
    }

    /** Mouse path: scroll a specific panel regardless of focus. */
    public fun scroll(id: K, dx: Int, dy: Int) {
        panelFor(id)?.scrollBy(dx, dy)
    }

    /** One-shot: the named panel recenters on its reveal target at the next render. */
    public fun requestRecenter(id: K) {
        pendingRecenter = id
    }

    /** The SIDE panel at screen ([x], [y]), or null — the main panel is never returned (see mouse rules). */
    public fun panelIdAt(x: Int, y: Int): K? = lastLayout?.sideAt(x, y)?.panel?.id

    /** The offset [id] settled on at the last render. */
    public fun offsetOf(id: K): ScrollOffset = panelFor(id)?.offset ?: ScrollOffset.ZERO

    private fun slotFor(id: K): PanelLayout.Slot<K, I>? {
        val layout = lastLayout ?: return null
        if (layout.main?.panel?.id == id) return layout.main
        return layout.sides.firstOrNull { it.panel.id == id }
    }

    /** Lays out [visible] side panels plus [main], draws every slot, and returns the layout it used. */
    public fun render(
        canvas: Canvas,
        inputs: I,
        visible: Set<K>,
        reservedTop: Int,
        forgetReveal: Boolean = false,
    ): PanelLayout<K, I> {
        if (focused != main.id && sides.none { it.id == focused && it.id in visible }) {
            focus(main.id)
        }

        val visibleSides = sides.filter { it.id in visible }
        val layout = PanelLayout.compute(canvas.width, canvas.height, reservedTop, main, visibleSides)
        lastLayout = layout

        layout.main?.let { slot ->
            slot.panel.render(
                canvas.region(slot.x, slot.y, slot.width, slot.height),
                inputs,
                focused = slot.panel.id == focused,
                forgetReveal = forgetReveal,
                recenter = pendingRecenter == slot.panel.id,
            )
        }
        for (slot in layout.sides) {
            slot.panel.render(
                canvas.region(slot.x, slot.y, slot.width, slot.height),
                inputs,
                focused = slot.panel.id == focused,
                forgetReveal = forgetReveal,
                recenter = pendingRecenter == slot.panel.id,
            )
        }
        pendingRecenter = null

        return layout
    }
}
