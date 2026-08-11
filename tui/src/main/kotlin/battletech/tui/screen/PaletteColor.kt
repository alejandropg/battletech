package battletech.tui.screen

/**
 * A color in whatever space its theme was authored in. Never converted between spaces — a
 * [TuiTheme] picks one [PaletteColor] subtype for every role it defines, matching the terminal
 * capability that theme targets, and [TextStyleFactory] renders each subtype with the matching
 * ANSI encoding directly. There is no nearest-color downsampling anywhere in this package.
 */
internal sealed interface PaletteColor {
    /** 24-bit color for [battletech.tui.screen.TuiTheme.DARK]/[battletech.tui.screen.TuiTheme.LIGHT]. Channels in `0..255`. */
    data class TrueColor(val red: Int, val green: Int, val blue: Int) : PaletteColor {
        init {
            require(red in 0..255) { "red must be in 0..255, got: $red" }
            require(green in 0..255) { "green must be in 0..255, got: $green" }
            require(blue in 0..255) { "blue must be in 0..255, got: $blue" }
        }
    }

    /**
     * xterm-256 palette index, restricted to `16..255`. Indices `0..15` are remapped by the
     * user's terminal theme, so their contrast is unknowable at build time — `16..255` are fixed
     * by the xterm specification and therefore both reproducible and testable.
     */
    data class Indexed(val index: Int) : PaletteColor {
        init {
            require(index in 16..255) { "index must be in 16..255, got: $index" }
        }
    }

    /** ANSI-16 SGR foreground code: `30..37` or `90..97`. Mordant derives the background code from it. */
    data class Basic(val code: Int) : PaletteColor {
        init {
            require(code in 30..37 || code in 90..97) { "code must be in 30..37 or 90..97, got: $code" }
        }
    }
}
