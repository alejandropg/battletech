package io.archinaut.battletech.tui.screen

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import io.archinaut.tenter.color.StandardRole
import io.archinaut.tenter.color.ColorRole
import io.archinaut.tenter.color.ColorSpace
import io.archinaut.tenter.color.TerminalColor

/**
 * On-disk shape of a theme file: one color space plus a value for every [StandardRole], [AppRole],
 * [BoardRole], and [HeatScaleRole], split into semantic tables so a missing or unknown role names
 * where it belongs. Every color value is a string parsed according to [colorSpace] — see
 * [ColorSpace]'s KDoc for the three syntaxes.
 */
@Serializable
internal data class ThemeFile(
    @Serializable(with = ColorSpaceSerializer::class)
    val colorSpace: ColorSpace,
    val background: String,
    val standard: Map<String, String> = emptyMap(),
    val app: Map<String, String> = emptyMap(),
    val board: Map<String, String> = emptyMap(),
    val heatScale: Map<String, String> = emptyMap(),
) {

    /** Parses and validates this file into a [Theme] named [name], throwing [ThemeLoadException] on any failure. */
    fun toTheme(name: String): Theme {
        val defaultBackground = parseColor(background, "$name: background")
        val colors = mutableMapOf<ColorRole, TerminalColor>()
        resolveRoles(standard, StandardRole.entries.associateBy { it.name }, "standard", name, colors)
        resolveRoles(app, AppRole.entries.associateBy { it.name }, "app", name, colors)
        resolveRoles(board, BoardRole.entries.associateBy { it.name }, "board", name, colors)
        resolveRoles(heatScale, HeatScaleRole.entries.associateBy { it.name }, "heatScale", name, colors)
        return Theme(name, colorSpace, defaultBackground, colors)
    }

    private fun <R : ColorRole> resolveRoles(
        values: Map<String, String>,
        rolesByName: Map<String, R>,
        table: String,
        name: String,
        into: MutableMap<ColorRole, TerminalColor>,
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

    private fun parseColor(raw: String, context: String): TerminalColor = try {
        colorSpace.parse(raw)
    } catch (e: IllegalArgumentException) {
        throw ThemeLoadException("$context: invalid $colorSpace color \"$raw\" (${e.message})")
    }
}

/**
 * A theme file's [ColorSpace], one per file, written in lowercase: `truecolor`, `ansi256`, or `ansi16`.
 * There is no conversion between spaces (see [TerminalColor]'s KDoc).
 */
internal object ColorSpaceSerializer : KSerializer<ColorSpace> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ColorSpace", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ColorSpace) {
        encoder.encodeString(value.name.lowercase())
    }

    override fun deserialize(decoder: Decoder): ColorSpace {
        val raw = decoder.decodeString()
        return ColorSpace.entries.firstOrNull { it.name.lowercase() == raw }
            ?: throw SerializationException("unknown color space \"$raw\"; expected truecolor, ansi256, or ansi16")
    }
}
