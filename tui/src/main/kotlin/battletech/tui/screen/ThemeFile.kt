package battletech.tui.screen

import com.github.ajalt.mordant.rendering.AnsiLevel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tenter.screen.ChromeRole
import tenter.screen.ColorRole
import tenter.screen.PaletteColor

/**
 * On-disk shape of a theme file: one color space plus a value for every [ChromeRole] and
 * [BoardRole], and [HeatScaleRole], split into semantic tables so a missing or unknown role names
 * where it belongs. Every color value is a string parsed according to [colorSpace] — see
 * [ColorSpace]'s KDoc for the three syntaxes.
 */
@Serializable
internal data class ThemeFile(
    val colorSpace: ColorSpace,
    val background: String,
    val chrome: Map<String, String> = emptyMap(),
    val board: Map<String, String> = emptyMap(),
    val heatScale: Map<String, String> = emptyMap(),
) {

    /** Parses and validates this file into a [Theme] named [name], throwing [ThemeLoadException] on any failure. */
    fun toTheme(name: String): Theme {
        val defaultBackground = parseColor(background, "$name: background")
        val colors = mutableMapOf<ColorRole, PaletteColor>()
        resolveRoles(chrome, ChromeRole.entries.associateBy { it.name }, "chrome", name, colors)
        resolveRoles(board, BoardRole.entries.associateBy { it.name }, "board", name, colors)
        resolveRoles(heatScale, HeatScaleRole.entries.associateBy { it.name }, "heatScale", name, colors)
        return Theme(name, colorSpace.level, defaultBackground, colors)
    }

    private fun <R : ColorRole> resolveRoles(
        values: Map<String, String>,
        rolesByName: Map<String, R>,
        table: String,
        name: String,
        into: MutableMap<ColorRole, PaletteColor>,
    ) {
        for ((key, value) in values) {
            val role = rolesByName[key]
                ?: throw ThemeLoadException("$name: unknown $table role \"$key\"")
            into[role] = parseColor(value, "$name: $key")
        }
        val missing = rolesByName.keys - values.keys
        if (missing.isNotEmpty()) {
            throw ThemeLoadException("$name: missing $table roles: ${missing.sorted().joinToString(", ")}")
        }
    }

    private fun parseColor(raw: String, context: String): PaletteColor = try {
        PaletteColor.parse(raw, colorSpace.level)
    } catch (e: IllegalArgumentException) {
        throw ThemeLoadException("$context: invalid $colorSpace color \"$raw\" (${e.message})")
    }
}

/**
 * The three [PaletteColor] subtypes a theme file can target, one per file — there is no
 * conversion between them (see [PaletteColor]'s KDoc). Each carries its own value syntax:
 * [TRUECOLOR] is `#RRGGBB`, [ANSI256] is a decimal string in `16..255`, [ANSI16] is a decimal
 * string in `30..37` or `90..97`.
 */
@Serializable
internal enum class ColorSpace(internal val level: AnsiLevel) {
    @SerialName("truecolor") TRUECOLOR(AnsiLevel.TRUECOLOR),
    @SerialName("ansi256") ANSI256(AnsiLevel.ANSI256),
    @SerialName("ansi16") ANSI16(AnsiLevel.ANSI16),
}
