package tenter.palette

/**
 * A [ColorRole] that carries its own [PaletteColor] rather than being resolved through a
 * [RolePalette] — for content whose colors are deliberately fixed rather than themeable (e.g. a
 * hardcoded ANSI-art effect that must render identically regardless of which theme is loaded).
 * The renderer resolves a [FixedColorRole] to [color] before asking its [RolePalette] for a
 * semantic color. A fixed role therefore needs no entry in any theme file and works with every
 * valid palette implementation.
 */
public interface FixedColorRole : ColorRole {
    public val color: PaletteColor
}
