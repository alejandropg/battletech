# BattleTech

A multi-module Kotlin project to play BattleTech tabletop game in a terminal (TUI).

The terminal should use a NerdFonts font. Truecolor gets the full terrain palette; 256-color and
16-color terminals get their own authored themes, with terrain material carried by icon color and
glyph instead of a hex fill. The theme is auto-selected from the terminal's detected color support
(`dark`, `dark-256`, or `dark-16`), or set explicitly with
`--theme dark|light|dark-256|light-256|dark-16|light-16`.

Supports hot seat and network play:

```
battletech-tui                                  hot-seat (both players share this terminal)
battletech-tui host [--port N]                  host a session
battletech-tui join <ip[:port]> --session <id>  join a hosted session
battletech-tui serve [--port N]                 headless dedicated server
```

Run any form with `--help` for its full option list.
