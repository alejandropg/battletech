package tenter.screen

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

/**
 * The roles tenter's own widgets (chrome, borders, help text, gauges, …) draw with. Every
 * [RolePalette] a host application supplies must resolve all of them.
 *
 * Several roles may deliberately resolve to the same value in some palettes — that's a palette
 * choice, not a reason to merge roles here: they are semantically distinct call sites that a
 * palette can later choose to diverge without touching any of them.
 */
/**
 * A [ColorRole] that carries its own [PaletteColor] rather than being resolved through a
 * [RolePalette] — for content whose colors are deliberately fixed rather than themeable (e.g. a
 * hardcoded ANSI-art effect that must render identically regardless of which theme is loaded).
 * [MapRolePalette.foreground] resolves any [FixedColorRole] to [color] directly, ahead of its
 * normal role-map lookup, so a [FixedColorRole] needs no entry in any theme file.
 */
public interface FixedColorRole : ColorRole {
    public val color: PaletteColor
}

public enum class ChromeRole : ColorRole {
    /** The surface's default — the only role whose foreground and background differ. */
    DEFAULT,

    TEXT_PRIMARY,
    TEXT_MUTED,
    TEXT_SUBTLE,
    ACCENT,
    INFO,
    SUCCESS,
    WARNING,
    DANGER,
    DRAFT,
    DISABLED,
    PANEL_BORDER,
    PANEL_BORDER_FOCUSED,
}
