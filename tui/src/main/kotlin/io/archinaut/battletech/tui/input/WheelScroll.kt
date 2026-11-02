package io.archinaut.battletech.tui.input

import com.github.ajalt.mordant.input.MouseEvent

/** The rows a wheel tick scrolls a panel by, negative for up; `null` for any other mouse event. */
internal fun wheelScrollDelta(event: MouseEvent): Int? = when {
    event.wheelUp -> -WHEEL_SCROLL_STEP
    event.wheelDown -> WHEEL_SCROLL_STEP
    else -> null
}

/** Rows scrolled per wheel tick (matches lazygit's default). */
internal const val WHEEL_SCROLL_STEP: Int = 2
