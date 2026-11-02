package io.archinaut.battletech.tui.input

import com.github.ajalt.mordant.input.MouseEvent

/**
 * The rows a mouse event scrolls a panel by: [WHEEL_SCROLL_STEP] per wheel tick, negative for up.
 * Also preserves the TUI's Mordant 3.0.2 compatibility behavior for side panels only; the
 * fallback is deliberately absent from board clicks.
 *
 * Mordant 3.0.2's posix parser never sets `wheelUp`/`wheelDown`: it enables X10+1005 tracking, where
 * terminals report a wheel tick as button code 96/97, but checks for 64/65 — so a real tick arrives
 * as `left`/`right`, indistinguishable from a click. Side panels have no click semantics, so a
 * left/right press over one is read as a wheel tick; genuine wheel flags (Windows) take precedence.
 * Remove the fallback once Mordant's parser is fixed. Hand-check recipe: `docs/tui-testing.md`.
 */
internal fun legacyPanelScrollDelta(event: MouseEvent, sidePanel: Boolean): Int? = when {
    event.wheelUp -> -WHEEL_SCROLL_STEP
    event.wheelDown -> WHEEL_SCROLL_STEP
    sidePanel && event.left -> -WHEEL_SCROLL_STEP
    sidePanel && event.right -> WHEEL_SCROLL_STEP
    else -> null
}

/** Rows scrolled per wheel tick (matches lazygit's default). */
internal const val WHEEL_SCROLL_STEP: Int = 2
