package io.archinaut.battletech.tui.animation

import io.archinaut.tenter.color.TerminalColor

/*
 * The weapon-fire animations' entire color vocabulary: fixed ANSI-16 [TerminalColor]s, used directly
 * as color roles. These are hardcoded, not themed: every animation must render with the same
 * palette regardless of which [io.archinaut.tenter.color.ColorScheme] the host app loaded, and
 * Tenter renders a [TerminalColor] as given without ever consulting the loaded theme.
 *
 * Values use fixed ANSI-16 escapes — for example, `"\033[31;1m"` (bright red) maps to
 * [ANIMATION_DANGER]. [io.archinaut.tenter.screen.Style] has no bold attribute, so a `;1` (bold) escape
 * maps to its bright `9x` code rather than losing the intensity distinction entirely.
 */

/** `\033[90m` — dim starfield / decaying trail particles. */
internal val ANIMATION_GRAY: TerminalColor = TerminalColor.Ansi16(90)

/** `\033[36m` — cyan tracer/reticle marks. */
internal val ANIMATION_CYAN: TerminalColor = TerminalColor.Ansi16(36)

/** `\033[96;1m` — bright cyan missile mid-trail. */
internal val ANIMATION_BRIGHT_CYAN: TerminalColor = TerminalColor.Ansi16(96)

/** `\033[31;1m` — bright red: beam core, impact flash, muzzle heat. */
internal val ANIMATION_DANGER: TerminalColor = TerminalColor.Ansi16(91)

/** `\033[35;1m` — bright magenta laser beam variant. */
internal val ANIMATION_MAGENTA: TerminalColor = TerminalColor.Ansi16(95)

/** `\033[33m` — plain yellow (ejected casing mid-flight). */
internal val ANIMATION_AMBER: TerminalColor = TerminalColor.Ansi16(33)

/** `\033[33;1m` — bright yellow: tracers, sparks, missile exhaust. */
internal val ANIMATION_WARNING: TerminalColor = TerminalColor.Ansi16(93)

/** `\033[37;1m` — bright white: projectile heads, impact core, hot metal. */
internal val ANIMATION_BRIGHT: TerminalColor = TerminalColor.Ansi16(97)

/**
 * Fixed black background for every cell the animation draws, space included — the panel is fully
 * opaque over whatever the board painted underneath it, and (per the hardcoded-not-themed rule
 * above) that opacity can't depend on the loaded theme's own default surface either.
 */
internal val ANIMATION_BACKGROUND: TerminalColor = TerminalColor.Ansi16(30)

/**
 * The floating panel's border color — `io.archinaut.battletech.tui.view.Workspace` passes this as
 * its [io.archinaut.tenter.widget.Block]'s `borderColor` so the frame itself is hardcoded too, not
 * [io.archinaut.tenter.color.StandardRole.PANEL_BORDER]. Reuses [ANIMATION_GRAY]'s value rather than a fourth
 * dim gray constant.
 */
internal val ANIMATION_BORDER: TerminalColor = ANIMATION_GRAY
