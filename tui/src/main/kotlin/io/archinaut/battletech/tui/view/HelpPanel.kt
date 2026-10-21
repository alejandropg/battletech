package io.archinaut.battletech.tui.view

import io.archinaut.tenter.input.KeySection
import io.archinaut.tenter.widget.KeyHelp
import io.archinaut.tenter.widget.panel.Panel
import io.archinaut.tenter.widget.panel.PanelId

/** The HELP panel's title, on both the game and the setup screen. */
internal const val HELP_TITLE: String = "HELP"

/**
 * Builds the shared HELP panel. Callers supply only the panel identity, badge, and the
 * context-specific key sections; title, scrolling, and declared panel states stay local to this
 * module. Where it goes and how wide it is belongs to each screen's layout.
 */
internal fun <K : PanelId, I> helpPanel(
    id: K,
    badge: Char?,
    sections: (I) -> List<KeySection>,
): Panel<K, I> = Panel(
    id = id,
    title = HELP_TITLE,
    badge = badge?.toString(),
    normal = { KeyHelp(sections(it)) },
    // No minimized state — ? dismisses HELP instead (see AppState.helpOpen / SetupState.helpOpen).
    maximized = { KeyHelp(sections(it)) },
)
