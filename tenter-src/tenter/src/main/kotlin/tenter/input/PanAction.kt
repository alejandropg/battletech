package tenter.input

/** A manual viewport pan, distinct from cursor movement. One step; the caller supplies the stride. */
public sealed interface PanAction : InputAction {

    public enum class Direction { LEFT, RIGHT, UP, DOWN }

    public data class Pan(val direction: Direction) : PanAction {
        override val id: String get() = "pan.${direction.name.lowercase()}"
    }

    public data object Recenter : PanAction {
        override val id: String get() = "pan.recenter"
    }
}
