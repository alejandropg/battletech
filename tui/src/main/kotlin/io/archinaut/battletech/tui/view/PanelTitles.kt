package io.archinaut.battletech.tui.view

import io.archinaut.tenter.screen.Padding
import io.archinaut.tenter.widget.Title

/**
 * A panel's border titles: the [key] that focuses it as `[key]`, then [text]. The key comes first,
 * so a panel too narrow for both, such as a minimized one, still shows it.
 */
internal fun panelTitles(key: Char?, text: String): List<Title> =
    listOfNotNull(key?.let { Title("[$it]", padding = Padding.ZERO) }, Title(text))
