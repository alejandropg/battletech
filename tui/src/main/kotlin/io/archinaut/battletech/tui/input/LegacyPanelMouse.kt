package io.archinaut.battletech.tui.input

import com.github.ajalt.mordant.input.MouseEvent
import io.archinaut.tenter.input.MouseInput

/**
 * Preserves the TUI's Mordant 3.0.2 compatibility behavior for side panels only. The toolkit
 * treats actual wheel flags as the complete mouse-scroll contract; this app-local fallback is
 * deliberately absent from the independent consumer and from board clicks.
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
