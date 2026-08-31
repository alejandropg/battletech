# BattleTech

A multi-module Kotlin project to play BattleTech tabletop game in a terminal (TUI).

The terminal should use a NerdFonts font. Truecolor gets the full terrain palette; 256-color and
16-color terminals get their own authored themes, with terrain material carried by icon color and
glyph instead of a hex fill. The theme is auto-selected from the terminal's detected color support
(`dark`, `dark-256`, or `dark-16`), or set explicitly with `--theme <name|path>` — a built-in name
(`dark`, `light`, `dark-256`, `light-256`, `dark-16`, `light-16`) or the path to your own theme
file; see `docs/color-themes.md` for the file format.

Supports hot seat and network play. There is no default command — one must always be named — and
every root option (registration, theme, listing) must come BEFORE the command name:

```
battletech-tui [--add-map <path>]... [--add-mech <path>]... [--add-unit <path>]... [--theme <name|path>] hot-seat [--map <name>] [--unit <name>]   hot-seat
battletech-tui [--add-*]... [--theme <name|path>] host [--port N] [--map <name>] [--unit <name>]     host a session
battletech-tui [--add-*]... join <ip[:port]> --session <id>                                          join a session
battletech-tui [--add-*]... [--theme <name|path>] server [--port N] [--map <name>] [--unit <name>]   headless server
```

Registration and selection are two different verbs. `--add-map`/`--add-mech`/`--add-unit` (root
options, repeatable) each register an external file under its filename minus `.json`; `--map`/
`--unit` (on `hot-seat`/`host`/`server` only) then SELECT a board and a unit collection by name
from everything registered — built-in content plus whatever `--add-*` added. A unit collection is
just a roster: it carries no board of its own, so the same one is playable on any registered map.
See [`docs/unit-files.md`](docs/unit-files.md) and [`docs/mech-files.md`](docs/mech-files.md) for
file formats and examples. Run any form with `--help` for its full option list.

Each mode exposes only the options it consumes: `join` never selects a map or unit collection —
those come from the host — though it still accepts `--add-*` so its local-drift check has
registered content to compare the host's map and mechs against. `server` accepts `--theme` (it's a
root option, valid everywhere) but discards it, being headless.

Each content or theme option has a matching `--list-maps`, `--list-mechs`, `--list-units`, or
`--list-themes` flag, valid everywhere (they're root options too). Listing includes built-in and
any externally registered assets, then exits — combine several flags in one invocation to see
multiple kinds at once, with or without a command name.
