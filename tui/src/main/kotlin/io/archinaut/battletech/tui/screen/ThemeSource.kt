package io.archinaut.battletech.tui.screen

import io.archinaut.tenter.color.ColorSpace
import io.archinaut.tenter.color.MapColorScheme

/**
 * This app's [MapColorScheme] — a [io.archinaut.tenter.color.ColorScheme] loaded from a packaged/custom theme
 * file. Kept as a local alias (rather than spelling out `MapColorScheme` at every call site) since
 * every `Theme` in this codebase is specifically one built by [resolveTheme]/[ThemeLoader] from
 * this app's own packaged theme files under `theme/` — see `docs/color-themes.md` for the file
 * format.
 */
internal typealias Theme = MapColorScheme

/**
 * Resolves a theme [spec] to a [Theme]. Mirrors
 * [io.archinaut.battletech.tactical.model.map.resolveMap]: if [spec] identifies an existing filesystem path,
 * that source is authoritative and is loaded via [loader]. Otherwise, [spec] is treated as an
 * extensionless packaged theme name and `theme/<spec>.json` is loaded from the classpath.
 *
 * Throws [ThemeLoadException] when the selected path cannot be read or parsed, or when the
 * packaged resource is missing, malformed, or semantically invalid (an unknown or missing role, an
 * out-of-range color value). An existing path's failure never falls back to a packaged resource.
 */
public fun resolveTheme(spec: String, loader: ThemeLoader = ThemeLoader()): Theme = loader.resolve(spec)

/**
 * The packaged theme name auto-selected for [space] when `--theme` was not supplied. Always the
 * dark variant in every space — there is no terminal light/dark detection. A `null` space (a
 * terminal without color) renders no color, so its mapping is never actually observed.
 */
internal fun defaultThemeName(space: ColorSpace?): String = when (space) {
    ColorSpace.TRUECOLOR, null -> "dark"
    ColorSpace.ANSI256 -> "dark-256"
    ColorSpace.ANSI16 -> "dark-16"
}
