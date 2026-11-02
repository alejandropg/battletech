package io.archinaut.battletech.tui.screen

import io.archinaut.tenter.color.ColorRole

/** This application's own roles that are not board- or heat-specific, beyond Tenter's [io.archinaut.tenter.color.StandardRole]. */
internal enum class AppRole : ColorRole {
    /** A projected, not yet committed value: a drafted declaration, a pending heat source. */
    DRAFT,
}
