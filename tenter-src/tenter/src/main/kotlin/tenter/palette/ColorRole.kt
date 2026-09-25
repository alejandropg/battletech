package tenter.palette

/**
 * A semantic color role. Every role resolves to a concrete color through a [RolePalette].
 * Rendering callers use only roles; concrete RGB, xterm, and SGR values are confined to
 * [RolePalette] implementations.
 *
 * [ColorRole] is a pure marker — tenter defines no closed set of every role that can exist.
 * [ChromeRole] is the set tenter's own widgets draw with; a host application defines its own
 * `ColorRole` enum for anything domain-specific (e.g. a map's terrain or faction colors) and
 * implements [RolePalette.foreground] as an exhaustive `when` over both enums:
 *
 * ```
 * override fun foreground(role: ColorRole): PaletteColor = when (role) {
 *     is ChromeRole -> ...       // exhaustive over tenter's roles
 *     is MyAppRole -> ...    // exhaustive over the app's own roles
 *     else -> error("unknown color role: $role")
 * }
 * ```
 *
 * Kotlin's exhaustiveness check still applies to each `is` branch's inner `when`, so adding a
 * role to either enum is still a compile error in every palette that doesn't handle it.
 */
public interface ColorRole
