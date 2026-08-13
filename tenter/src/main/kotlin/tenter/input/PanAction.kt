package tenter.input

/** A manual viewport pan, distinct from cursor movement — see [ChromeInput.panAction]. */
public sealed interface PanAction {
    public data class Pan(val dx: Int, val dy: Int) : PanAction
    public object Recenter : PanAction
}
