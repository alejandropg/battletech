package io.archinaut.battletech.tui.input

/** A manual viewport pan, distinct from cursor movement. One step; the caller supplies the stride. */
public sealed interface PanAction : Action {

    public enum class Direction { LEFT, RIGHT, UP, DOWN }

    public data class Pan(val direction: Direction) : PanAction

    public data object Recenter : PanAction
}
