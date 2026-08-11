package battletech.tui.screen

/**
 * One theme's role table. Implemented once per [TuiTheme] as a stateless `internal object` whose
 * [resolve] is a single `when` expression over [Color] — Kotlin checks enum-`when` exhaustiveness
 * at compile time, so adding a [Color] entry is a build error in every implementation. That is
 * deliberately relied on instead of a runtime completeness check: a `Map<Color, PaletteColor>`
 * cannot offer the same guarantee (unknown keys are impossible either way, but duplicate keys
 * would silently take the last value, and a missing key would only surface at first use).
 */
internal interface RolePalette {
    /** The theme's default background. [resolve] of [Color.DEFAULT] is the default *foreground*. */
    val defaultBackground: PaletteColor

    fun resolve(role: Color): PaletteColor
}
