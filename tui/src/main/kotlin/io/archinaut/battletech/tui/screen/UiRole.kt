package io.archinaut.battletech.tui.screen

import io.archinaut.tenter.palette.ColorRole

/** This application's own text roles, beyond tenter's [io.archinaut.tenter.palette.ChromeRole]. */
internal enum class UiRole : ColorRole {
    /** A projected, not yet committed value: a drafted declaration, a pending heat source. */
    DRAFT,
}
