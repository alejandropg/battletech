package io.archinaut.tenter.panel

import io.archinaut.tenter.screen.Point

/** The completed-frame result of asking a managed panel set about one screen coordinate. */
public data class PanelHit<K : PanelId>(
    public val id: K,
    /** Unpadded content coordinates, or null for a border/padding hit. */
    public val contentPoint: Point?,
)
