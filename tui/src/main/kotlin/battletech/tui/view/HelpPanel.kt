package battletech.tui.view

import tenter.input.KeySection
import tenter.panel.Panel
import tenter.panel.PanelId
import tenter.view.HelpView

/**
 * Builds the shared HELP panel. Callers supply only the panel identity, badge, and the
 * context-specific key sections; title, width, scrolling, and declared panel states stay local
 * to this module.
 */
internal fun <K : PanelId, I> helpPanel(
    id: K,
    badge: Char?,
    sections: (I) -> List<KeySection>,
    width: Int = 42,
): Panel<K, I> = Panel(
    id = id,
    title = HelpView.TITLE,
    normalWidth = width,
    badge = badge,
    normal = { HelpView(sections(it)) },
    // No minimized state — ? dismisses HELP instead (see AppState.helpOpen / SetupState.helpOpen).
    maximized = { HelpView(sections(it)) },
)
