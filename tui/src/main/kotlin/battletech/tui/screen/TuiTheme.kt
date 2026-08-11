package battletech.tui.screen

import com.github.ajalt.mordant.rendering.AnsiLevel

/**
 * One of six terminal color themes: a dark/light pair authored natively for each of the three
 * ANSI capability tiers. There is no conversion between tiers — see [TuiPalette] and
 * [PaletteColor]'s KDoc for why.
 */
public enum class TuiTheme(
    internal val flag: String,
    internal val level: AnsiLevel,
) {
    DARK("dark", AnsiLevel.TRUECOLOR),
    LIGHT("light", AnsiLevel.TRUECOLOR),
    DARK_256("dark-256", AnsiLevel.ANSI256),
    LIGHT_256("light-256", AnsiLevel.ANSI256),
    DARK_16("dark-16", AnsiLevel.ANSI16),
    LIGHT_16("light-16", AnsiLevel.ANSI16),
    ;

    public companion object {
        /**
         * Auto-selects a theme from the terminal's detected [AnsiLevel], used when `--theme` was
         * not supplied. Always the dark variant at every tier — there is no terminal light/dark
         * detection. [AnsiLevel.NONE] renders nothing, so its mapping is never actually observed.
         */
        internal fun autoFor(level: AnsiLevel): TuiTheme = when (level) {
            AnsiLevel.TRUECOLOR -> DARK
            AnsiLevel.ANSI256 -> DARK_256
            AnsiLevel.ANSI16 -> DARK_16
            AnsiLevel.NONE -> DARK
        }

        /** The [TuiTheme] whose [flag] is [value], or null if no theme matches. */
        internal fun fromFlag(value: String): TuiTheme? = entries.find { it.flag == value }
    }
}
