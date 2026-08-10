package battletech.tui.view

/** A framed side-panel region that may remember where it scrolled. */
internal interface Pane : View {
    /** What the last [render] settled on; [ScrollState.NONE] until rendered, or when inert. */
    val scroll: ScrollState
}
