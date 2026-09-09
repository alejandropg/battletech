package tenterexample

import tenter.screen.ChromeRole
import tenter.screen.ColorRole
import tenter.screen.PaletteColor
import tenter.screen.RolePalette

internal object ExamplePalette : RolePalette {
    override val defaultBackground: PaletteColor = PaletteColor.TrueColor(20, 24, 28)

    override fun foreground(role: ColorRole): PaletteColor = when (role) {
        ChromeRole.DEFAULT -> PaletteColor.TrueColor(232, 236, 240)
        ChromeRole.TEXT_PRIMARY -> PaletteColor.TrueColor(232, 236, 240)
        ChromeRole.TEXT_MUTED -> PaletteColor.TrueColor(164, 174, 184)
        ChromeRole.TEXT_SUBTLE -> PaletteColor.TrueColor(110, 120, 130)
        ChromeRole.ACCENT -> PaletteColor.TrueColor(255, 211, 105)
        ChromeRole.INFO -> PaletteColor.TrueColor(105, 205, 230)
        ChromeRole.SUCCESS -> PaletteColor.TrueColor(125, 215, 140)
        ChromeRole.WARNING -> PaletteColor.TrueColor(245, 195, 95)
        ChromeRole.DANGER -> PaletteColor.TrueColor(245, 120, 120)
        ChromeRole.DRAFT -> PaletteColor.TrueColor(174, 166, 190)
        ChromeRole.DISABLED -> PaletteColor.TrueColor(120, 128, 136)
        ChromeRole.PANEL_BORDER -> PaletteColor.TrueColor(105, 150, 180)
        ChromeRole.PANEL_BORDER_FOCUSED -> PaletteColor.TrueColor(125, 215, 140)
        else -> error("ExamplePalette only resolves ChromeRole, got: $role")
    }
}
