package tenter.input

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent

/**
 * Input mappings that belong to the terminal chrome itself — quitting, panel chords, scrolling,
 * and viewport panning — rather than to any application-specific content. An application maps
 * its own domain actions (cursor movement, selection, …) separately and checks these first, since
 * they are meant to work the same regardless of what content is on screen.
 */
public object ChromeInput {

    /** Number of rows scrolled per wheel tick (matches lazygit default). */
    public const val SCROLL_STEP: Int = 2

    public fun isQuit(event: KeyboardEvent): Boolean =
        event.ctrl && event.key == "c"

    /**
     * The panel key an alt-chord names, if any: `alt+0` -> `'0'`, `alt+H` -> `'h'`. Resolving
     * the key to a panel identity and deciding what the chord does (focus, or HELP's open/close)
     * is the caller's job, not this mapper's.
     */
    public fun panelKey(event: KeyboardEvent): Char? {
        if (!event.alt) return null
        return event.key.singleOrNull()?.lowercaseChar()
    }

    /** `+` -> +1, `-` -> -1: one step through the focused panel's declared states. */
    public fun stateCycle(event: KeyboardEvent): Int? = when {
        event.ctrl || event.alt -> null
        event.key == "+" -> 1
        event.key == "-" -> -1
        else -> null
    }

    /**
     * Keyboard scrolling for the focused panel. Null unless the event is a bare arrow/page key —
     * `ctrl+arrows` are the board pan and must not be stolen (see [panAction]), so they and any
     * `alt`-chord are excluded here too.
     */
    public fun scrollAction(event: KeyboardEvent): ScrollAction? {
        if (event.ctrl || event.alt) return null
        return when (event.key) {
            "ArrowUp" -> ScrollAction.Lines(-1)
            "ArrowDown" -> ScrollAction.Lines(1)
            "PageUp" -> ScrollAction.Pages(-1)
            "PageDown" -> ScrollAction.Pages(1)
            else -> null
        }
    }

    /**
     * Returns the scroll delta for a mouse event: negative for scroll-up, positive for
     * scroll-down, null when the event carries no scroll intent.
     *
     * [overPanel] must be true when the pointer is over a scrollable panel slot; false
     * when it is over any other, non-scrollable area.
     *
     * ### Mordant 3.0.2 wheel-parsing workaround
     * Mordant's posix event parser checks `wheelUp = cb == 64` and `wheelDown = cb == 65`,
     * but real terminals in X10/1005 encoding (which Mordant enables) transmit wheel events
     * as button-code-plus-32: cb 96 (wheel up) and cb 97 (wheel down).
     * `96 and 3 == 0` → Mordant decodes wheel-up as `left = true`; `97 and 3 == 1` →
     * `right = true`.  Consequently, on any real posix terminal `wheelUp` and `wheelDown`
     * are never true and a physical wheel tick is indistinguishable from a button press.
     *
     * Workaround: over a scrollable panel slot, treat a left press as wheel-up and a right
     * press as wheel-down.  Panels have no click semantics of their own, so nothing is lost.
     * Content click handling is unaffected because it is reached only when [overPanel] is false.
     * The canonical `wheelUp`/`wheelDown` branches keep precedence so the code is correct on
     * Windows and future-proof once Mordant is patched upstream.
     *
     * A button *release* arrives with all button flags false and yields null.
     */
    public fun scrollDelta(event: MouseEvent, overPanel: Boolean): Int? = when {
        event.wheelUp -> -SCROLL_STEP
        event.wheelDown -> SCROLL_STEP
        overPanel && event.left -> -SCROLL_STEP
        overPanel && event.right -> SCROLL_STEP
        else -> null
    }

    /**
     * Manual viewport panning: `hjkl` or ctrl+arrows shift the viewport by one [stepX]/[stepY]
     * stride; `Home` recenters. Callers bind this globally so it never competes with plain
     * arrows/wasd (cursor movement) — `hjkl` and `Home` are typically unused elsewhere, and
     * ctrl+arrow is a distinct key from plain arrow.
     */
    public fun panAction(event: KeyboardEvent, stepX: Int, stepY: Int): PanAction? = when {
        event.key == "Home" -> PanAction.Recenter
        event.ctrl -> when (event.key) {
            "ArrowLeft" -> PanAction.Pan(-stepX, 0)
            "ArrowRight" -> PanAction.Pan(stepX, 0)
            "ArrowUp" -> PanAction.Pan(0, -stepY)
            "ArrowDown" -> PanAction.Pan(0, stepY)
            else -> null
        }
        !event.alt -> when (event.key) {
            "h" -> PanAction.Pan(-stepX, 0)
            "l" -> PanAction.Pan(stepX, 0)
            "k" -> PanAction.Pan(0, -stepY)
            "j" -> PanAction.Pan(0, stepY)
            else -> null
        }
        else -> null
    }
}
