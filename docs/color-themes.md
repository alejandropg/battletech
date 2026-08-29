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
  "heatScale": { "CURRENT_BG": "#3A3218", "...": "..." },
  "board":  { "PLAYER_1": "#A0F4FF", "...": "..." }
}
```

`chrome` must hold exactly the 13 `tenter.screen.ChromeRole` names; `board` must hold exactly the
26 `battletech.tui.screen.BoardRole` names; and `heatScale` must hold exactly the three
`battletech.tui.screen.HeatScaleRole` names. A missing or unrecognized role name fails to load with
a `ThemeLoadException` naming the offending role and table — this is the load-time replacement for
the compile-time exhaustiveness check a hand-written `RolePalette` object used to get for free (see
`tenter.screen.RolePalette`'s KDoc). Custom themes written before the `heatScale` table was added
must add all three roles; there is deliberately no unverified fallback color.

Resolution is not theme-specific: `battletech.tui.screen.ThemeLoader` is a thin adapter over
`battletech.tactical.io.ResourceOrFileLoader` (see `docs/architecture.md`), the same generic loader
`battletech.tactical.model.map.GameMapLoader` uses for maps. A packaged theme is resolved as
`theme/<name>.json` on the classpath; `theme/index.json` lists the built-in names
(`{"names": [...]}` — the same schema `map/index.json` uses) so an unrecognized `--theme` name can
name the built-ins in its error, since a jar can't otherwise list a resource directory. For
`--theme <name|path>`, an existing filesystem path is authoritative and never falls back to a
packaged theme; otherwise the value is resolved as a built-in name.

## Why the values are what they are

### Truecolor (`dark`, `light`)

**Heat-scale backgrounds retain the ladder's foreground hierarchy at 4.5:1 contrast.** The current
rung is amber, the current-exclusive/projected-inclusive heating interval is red, and the matching
cooling interval is blue. The arrows remain a redundant non-color cue. `CURRENT_BG` clears 4.5:1
against `TEXT_PRIMARY`; `HEATING_BG` and `COOLING_BG` each clear 4.5:1 against both `TEXT_MUTED` and
`DRAFT`, so the projected endpoint and rule text remain legible. `TuiPaletteTest` enforces those
pairs for truecolor and ANSI-256 themes and requires all three backgrounds to be distinct. ANSI-16
themes follow their existing distinctness-only policy because terminal remapping makes measurable
contrast unknowable.

| Theme | `CURRENT_BG` | `HEATING_BG` | `COOLING_BG` |
|---|---:|---:|---:|
| `dark` | `#3A3218` | `#3C1E24` | `#173044` |
| `light` | `#F1E0AE` | `#F3DADB` | `#D4E5F2` |
| `dark-256` | `58` | `52` | `17` |
| `light-256` | `229` | `224` | `195` |
| `dark-16` | `33` | `31` | `34` |
| `light-16` | `93` | `91` | `96` |

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

**`PANEL_BORDER`/`PANEL_BORDER_FOCUSED` are a pair: the unfocused neutral and a saturated green,
respectively.** A panel's border, title, badge, and scrollbar thumb all key off which of the two is
focused — `Bordered`'s default (`PANEL_BORDER`) is what every non-focusable box still uses, so
giving it a neutral meaning rather than deleting it kept every other bordered view (help text, the
match-over banner) unchanged. The light tiers use a mid-grey (`#5A6169` truecolor, `241` ansi256)
rather than white for the unfocused neutral: a literal white/light-grey border fails the 4.5:1
contrast floor every general role must clear against the light background (`#868B91` truecolor
measures 3.15:1; `245` ansi256 measures 3.17:1) — `TuiPaletteTest` catches this before it ships.

**The focused green is as saturated as each tier can afford, and three tiers can't afford any.**
Focus is signalled by color alone — `Panel.render` swaps the role and nothing else, so there is no
redundant glyph or weight cue of the kind terrain gets — which makes the focused/unfocused
separation worth spending chroma on. The target is lazygit's focused panel border, which is not a
hex at all: lazygit's default `activeBorderColor` is the *named* ANSI color `green`, so what it
actually paints is whatever the terminal's palette holds. `#19CB00` is that green under kitty's
default theme, and it is what `dark` now uses — 8.44:1 on the default background, OkLab chroma
0.246 at hue 142°. The muted `#72BF72` it replaced sat 0.037 from `TERRAIN_WOODS_HEAVY_ICON` and
0.059 from `SUCCESS` in OkLab, and only 0.172 from the unfocused neutral once
deuteranopia-simulated; the new value roughly doubles all three. Note the consequence of pinning a
hex: a truecolor theme freezes this green, while the ansi16 tiers below track the terminal palette
the way lazygit itself does.

`light` cannot reuse that hex — `#19CB00` measures 2.01:1 on `#F8F5EE`, failing the general-role
floor — so it takes the same hue held dark: `#007B00`, 5.03:1, chroma 0.099 → 0.172 at hue 142°.
`dark-256` moves `71` → `40` (`0,215,0`), the closest cube color to `#19CB00` in OkLab (dE 0.033)
at 9.56:1, which also retires a duplicate: `71` was simultaneously `TERRAIN_WOODS_HEAVY_ICON` in
that file.

The remaining three tiers keep their original value because no better one exists. `light-256` stays
at `22`: it is the most chromatic green *of any cube index* that clears 4.5:1 against background
`255` — the next step up (`0,135,0`) measures 4.05:1. The ansi16 tiers stay at `32`, which is not a
compromise here but the exact mechanism lazygit uses — the named green, resolved by the terminal.
The 16-color space holds only one other green, and `92` is already `SUCCESS` (plus
`TERRAIN_WOODS_LIGHT_ICON` in `dark-16`).

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
