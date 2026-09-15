package tenter.palette

internal class OverrideRolePalette(
    private val name: String,
    private val base: RolePalette,
    overrides: Map<ColorRole, PaletteColor>,
    public override val defaultBackground: PaletteColor,
) : RolePalette {
    private val overrides: Map<ColorRole, PaletteColor> = overrides.toMap()

    init {
        this.overrides.values.forEach(::validated)
        ChromeRole.entries.forEach { role ->
            foreground(role)
            background(role)
        }
    }

    public override fun foreground(role: ColorRole): PaletteColor =
        validated(overrides[role] ?: base.foreground(role))

    public override fun background(role: ColorRole): PaletteColor = when (role) {
        ChromeRole.DEFAULT -> defaultBackground
        else -> validated(overrides[role] ?: base.background(role))
    }

    public override fun toString(): String = name

    private fun validated(color: PaletteColor): PaletteColor {
        require(color::class == defaultBackground::class) {
            "Palette $name contains a color in a different tier from its default background"
        }
        return color
    }
}
