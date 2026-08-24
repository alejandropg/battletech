package battletech.tui.screen

import com.github.ajalt.mordant.rendering.AnsiLevel
import tenter.screen.MapRolePalette

/**
 * This app's [MapRolePalette] — a [tenter.screen.RolePalette] loaded from a packaged/custom theme
 * file. Kept as a local alias (rather than spelling out `MapRolePalette` at every call site) since
 * every `Theme` in this codebase is specifically one built by [resolveTheme]/[ThemeLoader] from
 * this app's own packaged theme files under `theme/` — see `docs/color-themes.md` for the file
 * format.
 */
internal typealias Theme = MapRolePalette

/**
 * Resolves a theme [spec] to a [Theme]. Mirrors
 * [battletech.tactical.model.map.resolveMap]: if [spec] identifies an existing filesystem path,
 * that source is authoritative and is loaded via [loader]. Otherwise, [spec] is treated as an
 * extensionless packaged theme name and `theme/<spec>.json` is loaded from the classpath.
 *
 * Throws [ThemeLoadException] when the selected path cannot be read or parsed, or when the
 * packaged resource is missing, malformed, or semantically invalid (an unknown or missing role, an
 * out-of-range color value). An existing path's failure never falls back to a packaged resource.
 */
public fun resolveTheme(spec: String, loader: ThemeLoader = ThemeLoader()): Theme = loader.resolve(spec)

/**
 * The packaged theme name auto-selected for [level] when `--theme` was not supplied. Always the
 * dark variant at every tier — there is no terminal light/dark detection. [AnsiLevel.NONE] renders
 * nothing, so its mapping is never actually observed.
 */
internal fun defaultThemeName(level: AnsiLevel): String = when (level) {
    AnsiLevel.TRUECOLOR -> "dark"
    AnsiLevel.ANSI256 -> "dark-256"
    AnsiLevel.ANSI16 -> "dark-16"
    AnsiLevel.NONE -> "dark"
}
