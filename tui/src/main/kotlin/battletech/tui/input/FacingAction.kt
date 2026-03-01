package battletech.tui.input

public sealed interface FacingAction {
    public data class SelectFacing(val index: Int) : FacingAction
    public data object Cancel : FacingAction
}
