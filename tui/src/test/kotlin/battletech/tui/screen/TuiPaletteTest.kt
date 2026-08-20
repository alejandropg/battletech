package battletech.tui.screen

import battletech.tui.screen.TuiPaletteTest.Companion.PLAYER_FOREGROUNDS
import battletech.tui.screen.TuiPaletteTest.Companion.SUBTLE_ROLES
import battletech.tui.screen.TuiPaletteTest.Companion.luminance
import com.github.ajalt.mordant.rendering.AnsiLevel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import tenter.screen.ChromeRole
import tenter.screen.ColorRole
import tenter.screen.PaletteColor

/**
 * Verifies each built-in [Theme] (loaded from the packaged `theme` resources via [ThemeLoader],
 * not a checked-in fixture — see this class's own tests for why using the real packaged files
 * here doubles as a regression test) against the guarantees laid out in the color-theme plan: WCAG
 * contrast for
 * truecolor and ANSI-256 themes (their sRGB values are fixed and reproducible), distinctness only
 * for ANSI-16 themes (codes `0..15` are terminal-configurable, so their sRGB — and therefore
 * contrast — is unknowable at build time; do not add a contrast assertion for the `dark-16`/
 * `light-16` themes), and the reduced-theme design decision that every `TERRAIN_*_BG` role
 * collapses to the default background outside truecolor.
 *
 * Deliberately does NOT assert every non-default role value one by one — that would duplicate
 * the theme file's own values in the test and fail on every deliberate tweak while catching
 * nothing these properties don't already cover. The two default-surface values and the four
 * intentionally restored dark-theme terrain fills are pinned exactly: renderer output depends on
 * the former, while the latter are an explicit compatibility target from the previous palette.
 */
internal class TuiPaletteTest {

    private val loader = ThemeLoader()
    private val allThemes: List<Theme> = loader.builtInNames().map { loader.loadResource("theme/$it.json") }

    private val truecolorThemes = allThemes.filter { it.level == AnsiLevel.TRUECOLOR }
    private val ansi256Themes = allThemes.filter { it.level == AnsiLevel.ANSI256 }
    private val ansi16Themes = allThemes.filter { it.level == AnsiLevel.ANSI16 }
    private val reducedThemes = allThemes.filter { it.level != AnsiLevel.TRUECOLOR }

    /** Every role this module resolves: [ChromeRole] plus the board-specific [BoardRole]s. */
    private val allRoles: List<ColorRole> = ChromeRole.entries + BoardRole.entries

    /**
     * Every semantic role except the six terrain fills, four terrain icons, three badge
     * backgrounds, and the badge foreground — the general UI roles a screen surface actually
     * paints text with. [SUBTLE_ROLES] (checked separately, at a lower bar) are excluded too.
     */
    private val generalRoles = allRoles.filterNot {
        it == ChromeRole.DEFAULT || it in TERRAIN_FILLS || it in TERRAIN_ICONS || it in BADGE_BACKGROUNDS ||
            it == BoardRole.ELEVATION_BADGE_FG || it in SUBTLE_ROLES
    }

    @Test
    fun `the built-in theme index lists exactly six themes and every one loads`() {
        assertThat(allThemes).hasSize(6)
    }

    // ---- default surface: exact, pinned values (renderer tests depend on these literally) -------

    @Test
    fun `truecolor default surfaces match the exact authored hex values`() {
        val dark = resolveTheme("dark")
        assertThat(dark.foreground(ChromeRole.DEFAULT)).isEqualTo(rgb(0xDD, 0xE2, 0xE5))
        assertThat(dark.background(ChromeRole.DEFAULT)).isEqualTo(rgb(0x10, 0x14, 0x18))

        val light = resolveTheme("light")
        assertThat(light.foreground(ChromeRole.DEFAULT)).isEqualTo(rgb(0x20, 0x24, 0x28))
        assertThat(light.background(ChromeRole.DEFAULT)).isEqualTo(rgb(0xF8, 0xF5, 0xEE))
    }

    @Test
    fun `dark truecolor terrain fills restore the previous palette values`() {
        val dark = resolveTheme("dark")

        assertThat(dark.background(BoardRole.TERRAIN_WOODS_LIGHT_BG)).isEqualTo(rgb(0x3E, 0x5E, 0x33))
        assertThat(dark.background(BoardRole.TERRAIN_WOODS_HEAVY_BG)).isEqualTo(rgb(0x2C, 0x48, 0x26))
        assertThat(dark.background(BoardRole.TERRAIN_WATER_SHALLOW_BG)).isEqualTo(rgb(0x2F, 0x5E, 0x7E))
        assertThat(dark.background(BoardRole.TERRAIN_WATER_DEEP_BG)).isEqualTo(rgb(0x23, 0x4C, 0x68))
    }

    @Test
    fun `ansi256 default surfaces match the exact authored indices`() {
        val dark256 = resolveTheme("dark-256")
        assertThat(dark256.foreground(ChromeRole.DEFAULT)).isEqualTo(PaletteColor.Xterm256(253))
        assertThat(dark256.background(ChromeRole.DEFAULT)).isEqualTo(PaletteColor.Xterm256(233))

        val light256 = resolveTheme("light-256")
        assertThat(light256.foreground(ChromeRole.DEFAULT)).isEqualTo(PaletteColor.Xterm256(235))
        assertThat(light256.background(ChromeRole.DEFAULT)).isEqualTo(PaletteColor.Xterm256(255))
    }

    @Test
    fun `ansi16 default surfaces match the exact authored codes`() {
        val dark16 = resolveTheme("dark-16")
        assertThat(dark16.foreground(ChromeRole.DEFAULT)).isEqualTo(PaletteColor.Ansi16(37))
        assertThat(dark16.background(ChromeRole.DEFAULT)).isEqualTo(PaletteColor.Ansi16(30))

        val light16 = resolveTheme("light-16")
        assertThat(light16.foreground(ChromeRole.DEFAULT)).isEqualTo(PaletteColor.Ansi16(30))
        assertThat(light16.background(ChromeRole.DEFAULT)).isEqualTo(PaletteColor.Ansi16(97))
    }

    // ---- every theme resolves every role, in its own color space ------------------------------

    @Test
    fun `every theme resolves every non-default role in its own PaletteColor space`() {
        for (theme in allThemes) {
            for (role in allRoles) {
                val fg = theme.foreground(role)
                val expectedType = when (theme.level) {
                    AnsiLevel.TRUECOLOR -> PaletteColor.TrueColor::class
                    AnsiLevel.ANSI256 -> PaletteColor.Xterm256::class
                    AnsiLevel.ANSI16 -> PaletteColor.Ansi16::class
                    AnsiLevel.NONE -> error("no theme targets AnsiLevel.NONE")
                }
                assertThat(fg).describedAs("$theme.foreground($role)").isInstanceOf(expectedType.java)
            }
        }
    }

    @Test
    fun `every theme uses danger color for selected targets`() {
        for (theme in allThemes) {
            assertThat(theme.foreground(BoardRole.TARGET_SELECTED))
                .describedAs("$theme: TARGET_SELECTED vs DANGER")
                .isEqualTo(theme.foreground(ChromeRole.DANGER))
        }
    }

    // ---- truecolor-only properties ---------------------------------------------------------------

    @Test
    fun `truecolor- critical board foregrounds clear 4point5 to 1 against every terrain fill`() {
        for (theme in truecolorThemes) {
            for (fg in CRITICAL_BOARD_FOREGROUNDS) {
                for (fill in TERRAIN_FILLS) {
                    val ratio = contrast(luminance(theme.foreground(fg)), luminance(theme.background(fill)))
                    assertThat(ratio).describedAs("$theme: $fg on $fill").isGreaterThanOrEqualTo(4.5)
                }
            }
        }
    }

    @Test
    fun `truecolor- player foregrounds clear 3 to 1 against every terrain fill, one tier below the rest`() {
        // Unit ownership is the only board fact with no redundant non-color cue, so the instinct is
        // to hold these to the strictest floor. That instinct is wrong here, because the two
        // requirements pull against each other: 4.5:1 over these mid-luminance fills admits only
        // colors near L*0.87, and sRGB offers almost no chroma that light. Every pair satisfying it
        // is a pastel pair, and a player who cannot tell their mech from the enemy's has lost the
        // information the floor was protecting. 3:1 (WCAG 1.4.11, non-text/graphical objects) keeps
        // a unit readable over the worst fill while freeing the chroma that makes the two sides
        // distinct — which the separation test below then enforces as its own property.
        for (theme in truecolorThemes) {
            for (fg in PLAYER_FOREGROUNDS) {
                for (fill in TERRAIN_FILLS) {
                    val ratio = contrast(luminance(theme.foreground(fg)), luminance(theme.background(fill)))
                    assertThat(ratio).describedAs("$theme: $fg on $fill").isGreaterThanOrEqualTo(3.0)
                }
            }
        }
    }

    @Test
    fun `truecolor and ansi256- the two player colors are perceptually far apart`() {
        // Contrast-against-background and distinctness-from-each-other are independent properties,
        // and only the first was ever asserted for truecolor — which is how a pair at OkLab dE 0.12
        // shipped. Measured in OkLab rather than by contrast ratio because both colors are light by
        // construction: their ratio against each other is ~1, no matter how different they look.
        for (theme in truecolorThemes + ansi256Themes) {
            val distance = perceptualDistance(
                theme.foreground(BoardRole.PLAYER_1),
                theme.foreground(BoardRole.PLAYER_2),
            )
            assertThat(distance).describedAs("$theme: PLAYER_1 vs PLAYER_2 OkLab distance")
                .isGreaterThanOrEqualTo(MIN_PLAYER_SEPARATION)
        }
    }

    @Test
    fun `truecolor- each terrain icon clears 4point5 to 1 against its own terrain fill`() {
        for (theme in truecolorThemes) {
            for ((icon, fills) in ICON_TO_FILLS) {
                for (fill in fills) {
                    val ratio = contrast(luminance(theme.foreground(icon)), luminance(theme.background(fill)))
                    assertThat(ratio).describedAs("$theme: $icon on $fill").isGreaterThanOrEqualTo(4.5)
                }
            }
        }
    }

    @Test
    fun `truecolor- all six terrain fills are mutually distinct`() {
        for (theme in truecolorThemes) {
            val fills = TERRAIN_FILLS.map { theme.background(it) }
            assertThat(fills.toSet()).describedAs("$theme terrain fills").hasSize(TERRAIN_FILLS.size)
        }
    }

    @Test
    fun `truecolor- light woods is brighter than heavy woods, shallow water is brighter than deep water`() {
        for (theme in truecolorThemes) {
            val light = luminance(theme.background(BoardRole.TERRAIN_WOODS_LIGHT_BG))
            val heavy = luminance(theme.background(BoardRole.TERRAIN_WOODS_HEAVY_BG))
            assertThat(light).describedAs("$theme light vs heavy woods luminance").isGreaterThan(heavy)

            val shallow = luminance(theme.background(BoardRole.TERRAIN_WATER_SHALLOW_BG))
            val deep = luminance(theme.background(BoardRole.TERRAIN_WATER_DEEP_BG))
            assertThat(shallow).describedAs("$theme shallow vs deep water luminance").isGreaterThan(deep)
        }
    }

    // ---- truecolor + ansi256 properties (both have known, fixed sRGB values) --------------------

    @Test
    fun `truecolor and ansi256- elevation badge foreground clears 4point5 to 1 against every badge tier`() {
        for (theme in truecolorThemes + ansi256Themes) {
            val fg = theme.foreground(BoardRole.ELEVATION_BADGE_FG)
            for (badge in BADGE_BACKGROUNDS) {
                val ratio = contrast(luminance(fg), luminance(theme.background(badge)))
                assertThat(ratio).describedAs("$theme: ELEVATION_BADGE_FG on $badge").isGreaterThanOrEqualTo(4.5)
            }
        }
    }

    @Test
    fun `truecolor and ansi256- the three elevation badge tiers are mutually distinct`() {
        for (theme in truecolorThemes + ansi256Themes) {
            val badges = BADGE_BACKGROUNDS.map { theme.background(it) }
            assertThat(badges.toSet()).describedAs("$theme badge tiers").hasSize(BADGE_BACKGROUNDS.size)
        }
    }

    @Test
    fun `truecolor and ansi256- every general role clears 4point5 to 1 against the default background, except DISABLED at 3 to 1`() {
        for (theme in truecolorThemes + ansi256Themes) {
            val defaultBg = luminance(theme.background(ChromeRole.DEFAULT))
            for (role in generalRoles) {
                val required = if (role == ChromeRole.DISABLED) 3.0 else 4.5
                val ratio = contrast(luminance(theme.foreground(role)), defaultBg)
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
            val defaultBg = luminance(theme.background(ChromeRole.DEFAULT))
            for (role in SUBTLE_ROLES) {
                val ratio = contrast(luminance(theme.foreground(role)), defaultBg)
                assertThat(ratio).describedAs("$theme: $role on default background").isGreaterThanOrEqualTo(2.0)
            }
        }
    }

    @Test
    fun `truecolor and ansi256- default foreground clears 4point5 to 1 against default background`() {
        for (theme in truecolorThemes + ansi256Themes) {
            val ratio = contrast(luminance(theme.foreground(ChromeRole.DEFAULT)), luminance(theme.background(ChromeRole.DEFAULT)))
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
            val fg = theme.foreground(BoardRole.BOARD_BORDER)
            for (fill in TERRAIN_FILLS) {
                assertNotEquals(fg, theme.background(fill), "$theme: BOARD_BORDER vs $fill")
            }
        }
    }

    @Test
    fun `ansi256 themes use only xterm indices 16 through 255`() {
        for (theme in ansi256Themes) {
            for (role in allRoles) {
                val index = (theme.foreground(role) as PaletteColor.Xterm256).index
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
            val light = theme.foreground(BoardRole.TERRAIN_WOODS_LIGHT_ICON)
            val heavy = theme.foreground(BoardRole.TERRAIN_WOODS_HEAVY_ICON)
            val water = theme.foreground(BoardRole.TERRAIN_WATER_ICON)
            val rough = theme.foreground(BoardRole.TERRAIN_ROUGH_ICON)
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
            for (role in allRoles) {
                val code = (theme.foreground(role) as PaletteColor.Ansi16).code
                assertThat(code).describedAs("$theme: $role code").matches { it in 30..37 || it in 90..97 }
            }
        }
    }

    @Test
    fun `ansi16- roles the player must tell apart do not share a code`() {
        for (theme in ansi16Themes) {
            assertNotEquals(theme.foreground(BoardRole.PLAYER_1), theme.foreground(BoardRole.PLAYER_2), "$theme: PLAYER_1 vs PLAYER_2")
            val moves = listOf(BoardRole.MOVE_WALK, BoardRole.MOVE_RUN, BoardRole.MOVE_JUMP).map { theme.foreground(it) }
            assertThat(moves.toSet()).describedAs("$theme move colors").hasSize(3)
            assertNotEquals(theme.foreground(BoardRole.TARGET_VALID), theme.foreground(BoardRole.TARGET_SELECTED), "$theme: TARGET_VALID vs TARGET_SELECTED")
            assertNotEquals(theme.foreground(BoardRole.BOARD_ACTIVE), theme.foreground(BoardRole.BOARD_BORDER), "$theme: BOARD_ACTIVE vs BOARD_BORDER")
            assertNotEquals(theme.foreground(BoardRole.TERRAIN_WATER_ICON), theme.foreground(BoardRole.TERRAIN_ROUGH_ICON), "$theme: water vs rough icon")
            assertNotEquals(theme.foreground(BoardRole.TERRAIN_WOODS_LIGHT_ICON), theme.foreground(BoardRole.TERRAIN_WATER_ICON), "$theme: woods vs water icon")
            assertNotEquals(theme.foreground(BoardRole.TERRAIN_WOODS_LIGHT_ICON), theme.foreground(BoardRole.TERRAIN_ROUGH_ICON), "$theme: woods vs rough icon")
            val badges = BADGE_BACKGROUNDS.map { theme.background(it) }
            assertThat(badges.toSet()).describedAs("$theme badge tiers").hasSize(BADGE_BACKGROUNDS.size)
        }
    }

    // ---- reduced themes: terrain fill folds into the default background --------------------------

    @Test
    fun `reduced themes- every terrain fill role resolves to the default background`() {
        for (theme in reducedThemes) {
            val defaultBg = theme.background(ChromeRole.DEFAULT)
            for (fill in TERRAIN_FILLS) {
                assertThat(theme.background(fill)).describedAs("$theme: $fill").isEqualTo(defaultBg)
            }
        }
    }

    private companion object {
        /** Deliberately lower-contrast general roles — see the "subtle roles" test's KDoc. */
        private val SUBTLE_ROLES: List<ColorRole> = listOf(ChromeRole.TEXT_SUBTLE, BoardRole.BOARD_BORDER)
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
        /**
         * Board roles that must remain legible over every terrain fill. The selected-target marker
         * is intentionally excluded because it shares the requested danger red; it remains covered
         * by the default-background contrast check for general roles. [PLAYER_FOREGROUNDS] are
         * excluded too — they sit one tier lower, for the reason given on that list.
         */
        private val CRITICAL_BOARD_FOREGROUNDS: List<ColorRole> = listOf(
            ChromeRole.TEXT_PRIMARY, BoardRole.MOVE_WALK, BoardRole.MOVE_RUN, BoardRole.MOVE_JUMP,
            BoardRole.ATTACK_RANGE, BoardRole.BOARD_ACTIVE,
            BoardRole.LINE_OF_SIGHT, BoardRole.TARGET_VALID,
        )

        /** The two unit-ownership colors — see the "player foregrounds" test's KDoc. */
        private val PLAYER_FOREGROUNDS: List<ColorRole> = listOf(BoardRole.PLAYER_1, BoardRole.PLAYER_2)

        /**
         * OkLab distance below which the two player colors are too alike to tell apart on a glyph
         * two cells wide. The pastel pair this floor was introduced to retire measured 0.12.
         */
        private const val MIN_PLAYER_SEPARATION = 0.15
        private val ICON_TO_FILLS = mapOf(
            BoardRole.TERRAIN_WOODS_LIGHT_ICON to listOf(BoardRole.TERRAIN_WOODS_LIGHT_BG),
            BoardRole.TERRAIN_WOODS_HEAVY_ICON to listOf(BoardRole.TERRAIN_WOODS_HEAVY_BG),
            BoardRole.TERRAIN_WATER_ICON to listOf(BoardRole.TERRAIN_WATER_SHALLOW_BG, BoardRole.TERRAIN_WATER_DEEP_BG),
            BoardRole.TERRAIN_ROUGH_ICON to listOf(BoardRole.TERRAIN_ROUGH_BG),
        )

        private fun rgb(r: Int, g: Int, b: Int) = PaletteColor.TrueColor(r, g, b)

        /** WCAG sRGB relative luminance. [PaletteColor.Ansi16] (ANSI-16) has no defined luminance — never call this on one. */
        private fun luminance(color: PaletteColor): Double {
            val (r, g, b) = when (color) {
                is PaletteColor.TrueColor -> Triple(color.red, color.green, color.blue)
                is PaletteColor.Xterm256 -> ansi256Rgb(color.index)
                is PaletteColor.Ansi16 -> error("ANSI-16 codes have no defined sRGB value — see this file's class KDoc")
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

        /**
         * Euclidean distance in OkLab — a perceptually uniform space, so one threshold means the
         * same thing at every lightness and hue. [PaletteColor.Ansi16] has no defined sRGB value,
         * so this shares [luminance]'s restriction: never call it on one.
         */
        private fun perceptualDistance(a: PaletteColor, b: PaletteColor): Double {
            val (l1, a1, b1) = oklab(a)
            val (l2, a2, b2) = oklab(b)
            return Math.sqrt((l1 - l2) * (l1 - l2) + (a1 - a2) * (a1 - a2) + (b1 - b2) * (b1 - b2))
        }

        private fun oklab(color: PaletteColor): Triple<Double, Double, Double> {
            val (red, green, blue) = when (color) {
                is PaletteColor.TrueColor -> Triple(color.red, color.green, color.blue)
                is PaletteColor.Xterm256 -> ansi256Rgb(color.index)
                is PaletteColor.Ansi16 -> error("ANSI-16 codes have no defined sRGB value — see this file's class KDoc")
            }
            fun linear(c: Int): Double {
                val cs = c / 255.0
                return if (cs <= 0.04045) cs / 12.92 else Math.pow((cs + 0.055) / 1.055, 2.4)
            }
            val r = linear(red)
            val g = linear(green)
            val b = linear(blue)
            val l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
            val m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
            val s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
            return Triple(
                0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
            )
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
