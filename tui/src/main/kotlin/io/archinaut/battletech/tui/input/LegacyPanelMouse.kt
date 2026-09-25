package io.archinaut.battletech.tui.input

import com.github.ajalt.mordant.input.MouseEvent
import io.archinaut.tenter.input.MouseInput

/**
 * Preserves the TUI's Mordant 3.0.2 compatibility behavior for side panels only. The toolkit
 * treats actual wheel flags as the complete mouse-scroll contract; this app-local fallback is
 * deliberately absent from the independent consumer and from board clicks.
 *
 * Mordant 3.0.2's posix parser never sets `wheelUp`/`wheelDown`: it enables X10+1005 tracking, where
 * terminals report a wheel tick as button code 96/97, but checks for 64/65 — so a real tick arrives
 * as `left`/`right`, indistinguishable from a click. Side panels have no click semantics, so a
 * left/right press over one is read as a wheel tick; genuine wheel flags (Windows) take precedence.
 * Remove the fallback once Mordant's parser is fixed. Hand-check recipe: `docs/tui-testing.md`.
 */
internal fun legacyPanelScrollDelta(event: MouseEvent, sidePanel: Boolean): Int? =
    MouseInput.scrollDelta(event) ?: if (sidePanel) {
        when {
            event.left -> -MouseInput.SCROLL_STEP
            event.right -> MouseInput.SCROLL_STEP
            else -> null
        }
    } else {
        null
    }
