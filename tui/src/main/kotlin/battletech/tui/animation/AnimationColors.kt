package battletech.tui.animation

import tenter.screen.FixedColorRole
import tenter.screen.PaletteColor

/**
 * A [FixedColorRole] wrapping one ANSI-16 code — the weapon-fire animations' entire color
 * vocabulary. These are hardcoded, not themed: every animation must render with the same palette
 * regardless of which [tenter.screen.RolePalette] the host app loaded, so
 * [tenter.screen.MapRolePalette] resolves a [FixedColorRole] straight to [color] without ever
 * consulting the loaded theme.
 *
 * Values use fixed ANSI-16 escapes — for example, `"\033[31;1m"` (bright red) maps to
 * [ANIMATION_DANGER]. [tenter.screen.Cell.Style] has no bold attribute, so a `;1` (bold) escape
 * maps to its bright `9x` code rather than losing the intensity distinction entirely.
 */
internal data class AnimationColor(override val color: PaletteColor) : FixedColorRole

/** `\033[90m` — dim starfield / decaying trail particles. */
internal val ANIMATION_GRAY: AnimationColor = AnimationColor(PaletteColor.Ansi16(90))

/** `\033[36m` — cyan tracer/reticle marks. */
internal val ANIMATION_CYAN: AnimationColor = AnimationColor(PaletteColor.Ansi16(36))

/** `\033[96;1m` — bright cyan missile mid-trail. */
internal val ANIMATION_BRIGHT_CYAN: AnimationColor = AnimationColor(PaletteColor.Ansi16(96))

/** `\033[31;1m` — bright red: beam core, impact flash, muzzle heat. */
internal val ANIMATION_DANGER: AnimationColor = AnimationColor(PaletteColor.Ansi16(91))

/** `\033[35;1m` — bright magenta laser beam variant. */
internal val ANIMATION_MAGENTA: AnimationColor = AnimationColor(PaletteColor.Ansi16(95))

/** `\033[33m` — plain yellow (ejected casing mid-flight). */
internal val ANIMATION_AMBER: AnimationColor = AnimationColor(PaletteColor.Ansi16(33))

/** `\033[33;1m` — bright yellow: tracers, sparks, missile exhaust. */
internal val ANIMATION_WARNING: AnimationColor = AnimationColor(PaletteColor.Ansi16(93))

/** `\033[37;1m` — bright white: projectile heads, impact core, hot metal. */
internal val ANIMATION_BRIGHT: AnimationColor = AnimationColor(PaletteColor.Ansi16(97))

/**
 * Fixed black background for every cell the animation draws, space included — the panel is fully
 * opaque over whatever the board painted underneath it, and (per the hardcoded-not-themed rule
 * above) that opacity can't depend on the loaded theme's own default surface either.
 */
internal val ANIMATION_BACKGROUND: AnimationColor = AnimationColor(PaletteColor.Ansi16(30))

/**
 * The floating panel's border color — `battletech.tui.view.Workspace` passes this as
 * [tenter.view.Bordered]'s `borderColor` so the frame itself is hardcoded too, not
 * [tenter.screen.ChromeRole.PANEL_BORDER]. Reuses [ANIMATION_GRAY]'s value rather than a fourth
 * dim gray constant.
 */
internal val ANIMATION_BORDER: AnimationColor = ANIMATION_GRAY
