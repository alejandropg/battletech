package io.archinaut.tenter.screen

/** A rect, in some [Canvas]'s local coords, that content wants kept visible — see [Canvas.markReveal]. */
public data class RevealRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)
