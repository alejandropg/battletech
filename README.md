# BattleTech

A multi-module Kotlin project to play BattleTech tabletop game in a terminal (TUI).

The terminal should use a NerdFonts font. Truecolor gets the full terrain palette; 256-color and
16-color terminals get their own authored themes, with terrain material carried by icon color and
glyph instead of a hex fill. The theme is auto-selected from the terminal's detected color support
(`dark`, `dark-256`, or `dark-16`), or set explicitly with `--theme <name|path>` — a built-in name
(`dark`, `light`, `dark-256`, `light-256`, `dark-16`, `light-16`) or the path to your own theme
file; see `docs/color-themes.md` for the file format.

Supports hot seat and network play:

```
battletech-tui [--game <name|path>] [--map <path>]...                   hot-seat
battletech-tui host [--port N] [--game <name|path>] [--map <path>]...   host a session
battletech-tui join <ip[:port]> --session <id>                          join a session
battletech-tui server [--port N] [--game <name|path>] [--map <path>]... headless server
```

`--game` selects a packaged or external starting-game definition. Each repeated `--map` registers
an external map under its filename so the selected game can reference it. See
[`docs/game-files.md`](docs/game-files.md) for the JSON format and examples. Run any form with
`--help` for its full option list.
