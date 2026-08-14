# TUI color themes

The TUI's six built-in color themes (`dark`, `light`, `dark-256`, `light-256`, `dark-16`,
`light-16`) are data, not code: each is a JSON file in the repository's root `theme/` directory,
loaded at runtime by `battletech.tui.screen.resolveTheme` — see that function's KDoc and
`battletech.tui.screen.ThemeFile` for the exact resolution and validation rules. This doc covers
the file format and, since JSON can't hold comments, the design rationale behind the values
themselves. `battletech.tui.screen.TuiPaletteTest` enforces every contrast/distinctness/ordering
guarantee described below against the actual packaged files — if this doc and that test ever
disagree, the test is authoritative.

## File format

One file per theme, one color space per file — there is no conversion between tiers (see
`tenter.screen.PaletteColor`'s KDoc). `colorSpace` selects both the value syntax and the
`AnsiLevel` tier the theme targets:

| `colorSpace` | Value syntax                         | `PaletteColor` subtype |
|--------------|--------------------------------------|------------------------|
| `truecolor`  | `#RRGGBB`                            | `TrueColor`            |
| `ansi256`    | decimal string, `16..255`            | `Xterm256`             |
| `ansi16`     | decimal string, `30..37` or `90..97` | `Ansi16`               |

```json
{
  "colorSpace": "truecolor",
  "background": "#101418",
  "chrome": { "DEFAULT": "#DDE2E5", "...": "..." },
  "board":  { "PLAYER_1": "#A0F4FF", "...": "..." }
}
```

`chrome` must hold exactly the 12 `tenter.screen.ChromeRole` names; `board` must hold exactly the
26 `battletech.tui.screen.BoardRole` names. A missing or unrecognized role name fails to load with
a `ThemeLoadException` naming the offending role and table — this is the load-time replacement for
the compile-time exhaustiveness check a hand-written `RolePalette` object used to get for free (see
`tenter.screen.RolePalette`'s KDoc).

Resolution is not theme-specific: `battletech.tui.screen.ThemeLoader` is a thin adapter over
`battletech.tactical.io.ResourceOrFileLoader` (see `docs/architecture.md`), the same generic loader
`battletech.tactical.model.map.GameMapLoader` uses for maps. A packaged theme is resolved as
`theme/<name>.json` on the classpath; `theme/index.json` lists the built-in names
(`{"names": [...]}` — the same schema `map/index.json` uses) so an unrecognized `--theme` name can
name the built-ins in its error, since a jar can't otherwise list a resource directory. `--theme
<name|path>` behaves exactly like `--map` (down to the shared implementation): an existing
filesystem path is authoritative and never falls back to a packaged theme.

## Why the values are what they are

### Truecolor (`dark`, `light`)

**`PLAYER_2` sits at a 3:1 contrast floor while every other critical board foreground clears
4.5:1.** Unit ownership is the one board fact with no redundant non-color cue — `UnitRenderer` draws
friendly and enemy mechs with identical glyphs — so the instinct is to hold it to the *strictest*
floor. That's backwards here: the two requirements fight each other. 4.5:1 against the mid-luminance
terrain fills only admits colors near L\*≈0.87, and sRGB has almost no chroma left that light —
every pair satisfying it is a pastel pair. An earlier pastel pair (`#A8D8FF`/`#FFC0E7`) measured
OkLab ΔE 0.12 (0.05 under deuteranopia simulation) — effectively the same color, which is worse than
low contrast. 3:1 (WCAG 1.4.11, non-text/graphical objects) keeps a unit legible over the worst fill
while freeing the chroma the two sides need to actually look different; `PLAYER_1` stays light and
clears 4.5:1 on its own, buying a lightness gap that survives color-blind vision too. The two colors
are additionally required to sit ≥0.15 apart in OkLab (`TuiPaletteTest`'s player-separation test) —
contrast-against-background and distinctness-from-each-other are independent properties, and only
the first was ever checked when the 0.12 pair shipped.

**`TEXT_SUBTLE` and `BOARD_BORDER` clear only 2:1 against the default background, by design.**
Coordinate labels and ordinary hex grid lines are meant to recede, not compete with terrain and
units for attention — matching the literal-color scheme this plan replaced, which deliberately
muted both the same way. `BOARD_BORDER`'s "against every terrain fill" requirement is similarly
downgraded from a ratio to mere distinctness: the border carries no information by itself (material
reads from the fill/icon, elevation from the badge), so it only needs to be a different color from
what it outlines, not legible at a set ratio.

**Woods/water fills and elevation badges were retuned once elevated `CLEAR` hexes became whole-hex
fills** rather than a small badge cell. The fills were brightened back toward the original
literal-color values (density now needing to read at a glance across a whole hex), while the three
badge backgrounds were darkened a tier — a bright badge that only had to pop as a small chip became
overpowering once it could cover an entire hex. `WATER_SHALLOW_BG` moved the least of the four
fills: it has almost no headroom before `DANGER`/`TARGET_SELECTED`'s 4.5:1 guarantee against it
would break.

### ANSI-256 (`dark-256`, `light-256`)

All six `TERRAIN_*_BG` roles collapse to the theme's default background. The xterm-256 cube has no
usable dark brown and only one usable dark green; any tinted terrain fill that still clears 4.5:1
against board foregrounds forces every foreground toward the same near-white. Trading tinted hexes
for eleven distinguishable foregrounds is the better tradeoff — terrain material is carried by the
icon color and glyph instead, not the fill. For the same cube-scarcity reason, `light-256` gives
both woods icons the same green (index 22, the only cube green that clears 4.5:1 on that
background); density there reads from the glyph (tree-outline vs. pine-tree) instead of color.

### ANSI-16 (`dark-16`, `light-16`)

**Distinctness-tested, never contrast-tested.** Codes `0..15` are remapped by the user's terminal
theme, so their sRGB — and therefore their contrast — is unknowable at build time. These two themes
are authored against the conventional interpretation of the 16 SGR codes and verified only for role
*distinctness* (roles the player must tell apart — `PLAYER_1`/`PLAYER_2`, the three move colors, the
two target markers, active vs. border, and the terrain icon set — never share a code).
`TERRAIN_*_BG` again collapses to the default background, for the same reason as ANSI-256.

## Adding or changing a theme

Edit (or add) a file under `theme/`; if adding one, also add its name to `theme/index.json`. No
Kotlin change or rebuild is required to *use* a custom theme (`--theme <path>` loads any theme file
directly), but a new **built-in** theme should still get the same verification the six shipped ones
get: extend `battletech.tui.screen.TuiPaletteTest`'s built-in theme list (it already reads from
`ThemeLoader().builtInNames()`, so this is automatic) and confirm it against the contrast/
distinctness guarantees above before shipping it.
