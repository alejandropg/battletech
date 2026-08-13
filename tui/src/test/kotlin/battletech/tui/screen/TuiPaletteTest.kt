package battletech.tui.screen

import com.github.ajalt.mordant.rendering.AnsiLevel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import tenter.screen.ColorRole
import tenter.screen.PaletteColor
import tenter.screen.RolePalette
import tenter.screen.UiRole

/**
 * Verifies each theme's [RolePalette] (via [toRolePalette]) against the guarantees laid out in
 * the color-theme plan: WCAG contrast for truecolor and ANSI-256 themes (their sRGB values are
 * fixed and reproducible), distinctness only for ANSI-16 themes (codes `0..15` are
 * terminal-configurable, so their sRGB — and therefore contrast — is unknowable at build time; do
 * not add a contrast assertion for [TuiTheme.DARK_16] or [TuiTheme.LIGHT_16]), and the
 * reduced-theme design decision that every `TERRAIN_*_BG` role collapses to the default
 * background outside truecolor.
 *
 * Deliberately does NOT assert every non-default role value one by one — that would duplicate
 * the palette source in the test and fail on every deliberate tweak while catching nothing these
 * properties don't already cover. The two default-surface values and the four intentionally
 * restored dark-theme terrain fills are pinned exactly: renderer output depends on the former,
 * while the latter are an explicit compatibility target from the previous palette.
 */
internal class TuiPaletteTest {

    private val truecolorThemes = TuiTheme.entries.filter { it.level == AnsiLevel.TRUECOLOR }
    private val ansi256Themes = TuiTheme.entries.filter { it.level == AnsiLevel.ANSI256 }
    private val ansi16Themes = TuiTheme.entries.filter { it.level == AnsiLevel.ANSI16 }
    private val reducedThemes = TuiTheme.entries.filter { it.level != AnsiLevel.TRUECOLOR }

    /** Every role this module resolves: [UiRole] plus the board-specific [BoardRole]s. */
    private val allRoles: List<ColorRole> = UiRole.entries + BoardRole.entries

    /**
     * Every semantic role except the six terrain fills, four terrain icons, three badge
     * backgrounds, and the badge foreground — the general UI roles a screen surface actually
     * paints text with. [SUBTLE_ROLES] (checked separately, at a lower bar) are excluded too.
     */
    private val generalRoles = allRoles.filterNot {
        it == UiRole.DEFAULT || it in TERRAIN_FILLS || it in TERRAIN_ICONS || it in BADGE_BACKGROUNDS ||
            it == BoardRole.ELEVATION_BADGE_FG || it in SUBTLE_ROLES
    }

    // ---- default surface: exact, pinned values (renderer tests depend on these literally) -------

    @Test
    fun `truecolor default surfaces match the exact authored hex values`() {
        val dark = TuiTheme.DARK.toRolePalette()
        assertThat(dark.foreground(UiRole.DEFAULT)).isEqualTo(rgb(0xDD, 0xE2, 0xE5))
        assertThat(dark.background(UiRole.DEFAULT)).isEqualTo(rgb(0x10, 0x14, 0x18))

        val light = TuiTheme.LIGHT.toRolePalette()
        assertThat(light.foreground(UiRole.DEFAULT)).isEqualTo(rgb(0x20, 0x24, 0x28))
        assertThat(light.background(UiRole.DEFAULT)).isEqualTo(rgb(0xF8, 0xF5, 0xEE))
    }

    @Test
    fun `dark truecolor terrain fills restore the previous palette values`() {
        val dark = TuiTheme.DARK.toRolePalette()

        assertThat(dark.background(BoardRole.TERRAIN_WOODS_LIGHT_BG)).isEqualTo(rgb(0x3E, 0x5E, 0x33))
        assertThat(dark.background(BoardRole.TERRAIN_WOODS_HEAVY_BG)).isEqualTo(rgb(0x2C, 0x48, 0x26))
        assertThat(dark.background(BoardRole.TERRAIN_WATER_SHALLOW_BG)).isEqualTo(rgb(0x2F, 0x5E, 0x7E))
        assertThat(dark.background(BoardRole.TERRAIN_WATER_DEEP_BG)).isEqualTo(rgb(0x23, 0x4C, 0x68))
    }

    @Test
    fun `ansi256 default surfaces match the exact authored indices`() {
        val dark256 = TuiTheme.DARK_256.toRolePalette()
        assertThat(dark256.foreground(UiRole.DEFAULT)).isEqualTo(PaletteColor.Indexed(253))
        assertThat(dark256.background(UiRole.DEFAULT)).isEqualTo(PaletteColor.Indexed(233))

        val light256 = TuiTheme.LIGHT_256.toRolePalette()
        assertThat(light256.foreground(UiRole.DEFAULT)).isEqualTo(PaletteColor.Indexed(235))
        assertThat(light256.background(UiRole.DEFAULT)).isEqualTo(PaletteColor.Indexed(255))
    }

    @Test
    fun `ansi16 default surfaces match the exact authored codes`() {
        val dark16 = TuiTheme.DARK_16.toRolePalette()
        assertThat(dark16.foreground(UiRole.DEFAULT)).isEqualTo(PaletteColor.Basic(37))
        assertThat(dark16.background(UiRole.DEFAULT)).isEqualTo(PaletteColor.Basic(30))

        val light16 = TuiTheme.LIGHT_16.toRolePalette()
        assertThat(light16.foreground(UiRole.DEFAULT)).isEqualTo(PaletteColor.Basic(30))
        assertThat(light16.background(UiRole.DEFAULT)).isEqualTo(PaletteColor.Basic(97))
    }

    // ---- every theme resolves every role, in its own color space ------------------------------

    @Test
    fun `every theme resolves every non-default role in its own PaletteColor space`() {
        for (theme in TuiTheme.entries) {
            val palette = theme.toRolePalette()
            for (role in allRoles) {
                val fg = palette.foreground(role)
                val expectedType = when (theme.level) {
                    AnsiLevel.TRUECOLOR -> PaletteColor.TrueColor::class
                    AnsiLevel.ANSI256 -> PaletteColor.Indexed::class
                    AnsiLevel.ANSI16 -> PaletteColor.Basic::class
                    AnsiLevel.NONE -> error("no theme targets AnsiLevel.NONE")
                }
                assertThat(fg).describedAs("$theme.foreground($role)").isInstanceOf(expectedType.java)
            }
        }
    }

    // ---- truecolor-only properties ---------------------------------------------------------------

    @Test
    fun `truecolor- critical board foregrounds clear 4point5 to 1 against every terrain fill`() {
        for (theme in truecolorThemes) {
            val palette = theme.toRolePalette()
            for (fg in CRITICAL_BOARD_FOREGROUNDS) {
                for (fill in TERRAIN_FILLS) {
                    val ratio = contrast(luminance(palette.foreground(fg)), luminance(palette.background(fill)))
                    assertThat(ratio).describedAs("$theme: $fg on $fill").isGreaterThanOrEqualTo(4.5)
                }
            }
        }
    }

    @Test
    fun `truecolor- each terrain icon clears 4point5 to 1 against its own terrain fill`() {
        for (theme in truecolorThemes) {
            val palette = theme.toRolePalette()
            for ((icon, fills) in ICON_TO_FILLS) {
                for (fill in fills) {
                    val ratio = contrast(luminance(palette.foreground(icon)), luminance(palette.background(fill)))
                    assertThat(ratio).describedAs("$theme: $icon on $fill").isGreaterThanOrEqualTo(4.5)
                }
            }
        }
    }

    @Test
    fun `truecolor- all six terrain fills are mutually distinct`() {
        for (theme in truecolorThemes) {
            val palette = theme.toRolePalette()
            val fills = TERRAIN_FILLS.map { palette.background(it) }
            assertThat(fills.toSet()).describedAs("$theme terrain fills").hasSize(TERRAIN_FILLS.size)
        }
    }

    @Test
    fun `truecolor- light woods is brighter than heavy woods, shallow water is brighter than deep water`() {
        for (theme in truecolorThemes) {
            val palette = theme.toRolePalette()
            val light = luminance(palette.background(BoardRole.TERRAIN_WOODS_LIGHT_BG))
            val heavy = luminance(palette.background(BoardRole.TERRAIN_WOODS_HEAVY_BG))
            assertThat(light).describedAs("$theme light vs heavy woods luminance").isGreaterThan(heavy)

            val shallow = luminance(palette.background(BoardRole.TERRAIN_WATER_SHALLOW_BG))
            val deep = luminance(palette.background(BoardRole.TERRAIN_WATER_DEEP_BG))
            assertThat(shallow).describedAs("$theme shallow vs deep water luminance").isGreaterThan(deep)
        }
    }

    // ---- truecolor + ansi256 properties (both have known, fixed sRGB values) --------------------

    @Test
    fun `truecolor and ansi256- elevation badge foreground clears 4point5 to 1 against every badge tier`() {
        for (theme in truecolorThemes + ansi256Themes) {
            val palette = theme.toRolePalette()
            val fg = palette.foreground(BoardRole.ELEVATION_BADGE_FG)
            for (badge in BADGE_BACKGROUNDS) {
                val ratio = contrast(luminance(fg), luminance(palette.background(badge)))
                assertThat(ratio).describedAs("$theme: ELEVATION_BADGE_FG on $badge").isGreaterThanOrEqualTo(4.5)
            }
        }
    }

    @Test
    fun `truecolor and ansi256- the three elevation badge tiers are mutually distinct`() {
        for (theme in truecolorThemes + ansi256Themes) {
            val palette = theme.toRolePalette()
            val badges = BADGE_BACKGROUNDS.map { palette.background(it) }
            assertThat(badges.toSet()).describedAs("$theme badge tiers").hasSize(BADGE_BACKGROUNDS.size)
        }
    }

    @Test
    fun `truecolor and ansi256- every general role clears 4point5 to 1 against the default background, except DISABLED at 3 to 1`() {
        for (theme in truecolorThemes + ansi256Themes) {
            val palette = theme.toRolePalette()
            val defaultBg = luminance(palette.background(UiRole.DEFAULT))
            for (role in generalRoles) {
                val required = if (role == UiRole.DISABLED) 3.0 else 4.5
                val ratio = contrast(luminance(palette.foreground(role)), defaultBg)
                assertThat(ratio).describedAs("$theme: $role on default background").isGreaterThanOrEqualTo(required)
            }
        }
    }

    @Test
    fun `truecolor and ansi256- subtle roles (coordinates, board grid) clear only 2 to 1 against the default background, by design`() {
        // TEXT_SUBTLE and BOARD_BORDER are deliberately lower-contrast than every other general
        // role: coordinate labels and ordinary hex grid lines are meant to recede rather than
        // compete with terrain/units for attention (matching the original literal-color scheme's
        // muted GRAY/DARK_GRAY intent — TEXT_SUBTLE was pushed further still after the first
        // round of darkening left coordinates too prominent). 2:1 still keeps them from
        // disappearing entirely; nothing reads real information through their color alone — see
        // the redundant non-color cues in the palette plan's "Terrain and elevation rendering"
        // section.
        for (theme in truecolorThemes + ansi256Themes) {
            val palette = theme.toRolePalette()
            val defaultBg = luminance(palette.background(UiRole.DEFAULT))
            for (role in SUBTLE_ROLES) {
                val ratio = contrast(luminance(palette.foreground(role)), defaultBg)
                assertThat(ratio).describedAs("$theme: $role on default background").isGreaterThanOrEqualTo(2.0)
            }
        }
    }

    @Test
    fun `truecolor and ansi256- default foreground clears 4point5 to 1 against default background`() {
        for (theme in truecolorThemes + ansi256Themes) {
            val palette = theme.toRolePalette()
            val ratio = contrast(luminance(palette.foreground(UiRole.DEFAULT)), luminance(palette.background(UiRole.DEFAULT)))
            assertThat(ratio).describedAs("$theme default fg/bg").isGreaterThanOrEqualTo(4.5)
        }
    }

    @Test
    fun `truecolor and ansi256- decorative BOARD_BORDER is merely distinct from every terrain fill, not required to pop`() {
        // BOARD_BORDER carries no information by itself — terrain material reads from the fill/icon,
        // elevation from the badge — so the grid line only needs to be a different color from its
        // fill, not legible at any particular ratio. Reduced themes fold every fill into the
        // default background, where BOARD_BORDER's own default-background contrast is already
        // covered by the "subtle roles" test above.
        for (theme in truecolorThemes + ansi256Themes) {
            val palette = theme.toRolePalette()
            val fg = palette.foreground(BoardRole.BOARD_BORDER)
            for (fill in TERRAIN_FILLS) {
                assertNotEquals(fg, palette.background(fill), "$theme: BOARD_BORDER vs $fill")
            }
        }
    }

    @Test
    fun `ansi256 themes use only xterm indices 16 through 255`() {
        for (theme in ansi256Themes) {
            val palette = theme.toRolePalette()
            for (role in allRoles) {
                val index = (palette.foreground(role) as PaletteColor.Indexed).index
                assertThat(index).describedAs("$theme: $role index").isBetween(16, 255)
            }
        }
    }

    @Test
    fun `ansi256- the water and rough icon colors are mutually distinct from each other and from woods`() {
        // The two woods icons (light/heavy) may share a color in a reduced theme — density then
        // reads from the glyph (tree-outline vs pine-tree) instead. Water and rough must still be
        // their own distinct colors, and distinct from whichever woods color is in use.
        for (theme in ansi256Themes) {
            val palette = theme.toRolePalette()
            val light = palette.foreground(BoardRole.TERRAIN_WOODS_LIGHT_ICON)
            val heavy = palette.foreground(BoardRole.TERRAIN_WOODS_HEAVY_ICON)
            val water = palette.foreground(BoardRole.TERRAIN_WATER_ICON)
            val rough = palette.foreground(BoardRole.TERRAIN_ROUGH_ICON)
            assertNotEquals(water, rough, "$theme: water vs rough icon")
            assertNotEquals(light, water, "$theme: light woods vs water icon")
            assertNotEquals(light, rough, "$theme: light woods vs rough icon")
            assertNotEquals(heavy, water, "$theme: heavy woods vs water icon")
            assertNotEquals(heavy, rough, "$theme: heavy woods vs rough icon")
        }
    }

    // ---- ansi16: distinctness only, never contrast ------------------------------------------------

    @Test
    fun `ansi16- every role resolves to a valid SGR foreground code`() {
        for (theme in ansi16Themes) {
            val palette = theme.toRolePalette()
            for (role in allRoles) {
                val code = (palette.foreground(role) as PaletteColor.Basic).code
                assertThat(code).describedAs("$theme: $role code").matches { it in 30..37 || it in 90..97 }
            }
        }
    }

    @Test
    fun `ansi16- roles the player must tell apart do not share a code`() {
        for (theme in ansi16Themes) {
            val palette = theme.toRolePalette()
            assertNotEquals(palette.foreground(BoardRole.PLAYER_1), palette.foreground(BoardRole.PLAYER_2), "$theme: PLAYER_1 vs PLAYER_2")
            val moves = listOf(BoardRole.MOVE_WALK, BoardRole.MOVE_RUN, BoardRole.MOVE_JUMP).map { palette.foreground(it) }
            assertThat(moves.toSet()).describedAs("$theme move colors").hasSize(3)
            assertNotEquals(palette.foreground(BoardRole.TARGET_VALID), palette.foreground(BoardRole.TARGET_SELECTED), "$theme: TARGET_VALID vs TARGET_SELECTED")
            assertNotEquals(palette.foreground(BoardRole.BOARD_ACTIVE), palette.foreground(BoardRole.BOARD_BORDER), "$theme: BOARD_ACTIVE vs BOARD_BORDER")
            assertNotEquals(palette.foreground(BoardRole.TERRAIN_WATER_ICON), palette.foreground(BoardRole.TERRAIN_ROUGH_ICON), "$theme: water vs rough icon")
            assertNotEquals(palette.foreground(BoardRole.TERRAIN_WOODS_LIGHT_ICON), palette.foreground(BoardRole.TERRAIN_WATER_ICON), "$theme: woods vs water icon")
            assertNotEquals(palette.foreground(BoardRole.TERRAIN_WOODS_LIGHT_ICON), palette.foreground(BoardRole.TERRAIN_ROUGH_ICON), "$theme: woods vs rough icon")
            val badges = BADGE_BACKGROUNDS.map { palette.background(it) }
            assertThat(badges.toSet()).describedAs("$theme badge tiers").hasSize(BADGE_BACKGROUNDS.size)
        }
    }

    // ---- reduced themes: terrain fill folds into the default background --------------------------

    @Test
    fun `reduced themes- every terrain fill role resolves to the default background`() {
        for (theme in reducedThemes) {
            val palette = theme.toRolePalette()
            val defaultBg = palette.background(UiRole.DEFAULT)
            for (fill in TERRAIN_FILLS) {
                assertThat(palette.background(fill)).describedAs("$theme: $fill").isEqualTo(defaultBg)
            }
        }
    }

    private companion object {
        /** Deliberately lower-contrast general roles — see the "subtle roles" test's KDoc. */
        private val SUBTLE_ROLES: List<ColorRole> = listOf(UiRole.TEXT_SUBTLE, BoardRole.BOARD_BORDER)
        private val TERRAIN_FILLS = listOf(
            BoardRole.TERRAIN_CLEAR_BG,
            BoardRole.TERRAIN_WOODS_LIGHT_BG,
            BoardRole.TERRAIN_WOODS_HEAVY_BG,
            BoardRole.TERRAIN_WATER_SHALLOW_BG,
            BoardRole.TERRAIN_WATER_DEEP_BG,
            BoardRole.TERRAIN_ROUGH_BG,
        )
        private val TERRAIN_ICONS = listOf(
            BoardRole.TERRAIN_WOODS_LIGHT_ICON,
            BoardRole.TERRAIN_WOODS_HEAVY_ICON,
            BoardRole.TERRAIN_WATER_ICON,
            BoardRole.TERRAIN_ROUGH_ICON,
        )
        private val BADGE_BACKGROUNDS = listOf(
            BoardRole.ELEVATION_1_BADGE_BG,
            BoardRole.ELEVATION_2_BADGE_BG,
            BoardRole.ELEVATION_HIGH_BADGE_BG,
        )
        private val CRITICAL_BOARD_FOREGROUNDS: List<ColorRole> = listOf(
            UiRole.TEXT_PRIMARY, BoardRole.MOVE_WALK, BoardRole.MOVE_RUN, BoardRole.MOVE_JUMP,
            BoardRole.PLAYER_1, BoardRole.PLAYER_2, BoardRole.ATTACK_RANGE, BoardRole.BOARD_ACTIVE,
            BoardRole.LINE_OF_SIGHT, BoardRole.TARGET_VALID, BoardRole.TARGET_SELECTED,
        )
        private val ICON_TO_FILLS = mapOf(
            BoardRole.TERRAIN_WOODS_LIGHT_ICON to listOf(BoardRole.TERRAIN_WOODS_LIGHT_BG),
            BoardRole.TERRAIN_WOODS_HEAVY_ICON to listOf(BoardRole.TERRAIN_WOODS_HEAVY_BG),
            BoardRole.TERRAIN_WATER_ICON to listOf(BoardRole.TERRAIN_WATER_SHALLOW_BG, BoardRole.TERRAIN_WATER_DEEP_BG),
            BoardRole.TERRAIN_ROUGH_ICON to listOf(BoardRole.TERRAIN_ROUGH_BG),
        )

        private fun rgb(r: Int, g: Int, b: Int) = PaletteColor.TrueColor(r, g, b)

        /** WCAG sRGB relative luminance. [PaletteColor.Basic] (ANSI-16) has no defined luminance — never call this on one. */
        private fun luminance(color: PaletteColor): Double {
            val (r, g, b) = when (color) {
                is PaletteColor.TrueColor -> Triple(color.red, color.green, color.blue)
                is PaletteColor.Indexed -> ansi256Rgb(color.index)
                is PaletteColor.Basic -> error("ANSI-16 codes have no defined sRGB value — see this file's class KDoc")
            }
            fun channel(c: Int): Double {
                val cs = c / 255.0
                return if (cs <= 0.03928) cs / 12.92 else Math.pow((cs + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
        }

        private fun contrast(a: Double, b: Double): Double {
            val lighter = maxOf(a, b)
            val darker = minOf(a, b)
            return (lighter + 0.05) / (darker + 0.05)
        }

        private val CUBE_STEPS = intArrayOf(0, 95, 135, 175, 215, 255)

        /** The xterm-256 palette's fixed sRGB value for index [index] (`16..255`). */
        private fun ansi256Rgb(index: Int): Triple<Int, Int, Int> {
            require(index in 16..255) { "index must be in 16..255, got: $index" }
            return if (index in 232..255) {
                val v = 8 + 10 * (index - 232)
                Triple(v, v, v)
            } else {
                val i = index - 16
                Triple(CUBE_STEPS[i / 36], CUBE_STEPS[(i / 6) % 6], CUBE_STEPS[i % 6])
            }
        }
    }
}
