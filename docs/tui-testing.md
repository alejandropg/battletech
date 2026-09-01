# TUI visual testing

The TUI has ~50 automated test files; **those are the primary strategy for
verifying behavior**. Use the recipe below only to spot-check rendering that
can't be expressed as a unit test.

## `./gradlew :tui:run` does not work

`tui/build.gradle.kts` deliberately fails the `run` task:

```kotlin
// Gradle always runs tasks in a forked JVM that has no controlling terminal,
// so Mordant's enterRawMode() cannot work here. Run the JAR directly instead:
//   ./gradlew :tui:shadowJar && java -jar tui/build/libs/tui.jar
tasks.named<JavaExec>("run") {
    doFirst {
        throw GradleException(
            "The TUI requires a direct terminal connection that Gradle cannot provide " +
            "(Gradle always forks a separate JVM detached from the terminal).\n" +
            "Build and run the JAR directly:\n" +
            "  ./gradlew :tui:shadowJar && java -jar tui/build/libs/tui.jar"
        )
    }
}
```

Gradle always runs tasks in a forked JVM with no controlling terminal, so
Mordant's `enterRawMode()` cannot work. Build and run the jar directly:

```bash
./gradlew :tui:shadowJar && java -jar tui/build/libs/tui.jar
```

## The interactive setup screen

Bare invocation (`java -jar tui/build/libs/tui.jar`, no subcommand) opens the setup screen — it
has no `--map`/`--unit` fixture path the way the game screen does, so the tmux recipe below is
the way to hand-check it.

```bash
tmux new-session -d -s setup -x 220 -y 50
tmux send-keys -t setup 'java -jar tui/build/libs/tui.jar' Enter && sleep 2
tmux capture-pane -t setup -pe   # stage 1: only the MODE panel, hot-seat checked by default
tmux send-keys -t setup 'c' ''   # lock hot-seat in
tmux capture-pane -t setup -pe   # stage 2: MAP / PLAYER 1 / PLAYER 2 appear, focus on MAP
tmux send-keys -t setup ' ' ''   # space selects the map under the cursor
tmux send-keys -t setup '3' ''   # focus PLAYER 1
tmux send-keys -t setup ' ' ''   # count 0 -> 1 for the mech under the cursor
tmux send-keys -t setup '4' ''   # focus PLAYER 2
tmux send-keys -t setup ' ' ''
tmux send-keys -t setup 'c' ''   # commit — the game screen should replace the setup screen
tmux capture-pane -t setup -pe
tmux kill-session -t setup
```

Checklist:

- Stage 1 shows only panel `[1] MODE`; the banner (or, in a narrower terminal, the plain
  `BATTLETECH` fallback line) and the prompt/help row render above it.
- `c` while unlocked reveals `[2] MAP`, `[3] PLAYER 1`, `[4] PLAYER 2` in one frame and moves
  focus to MAP — nothing else about panel 1 changes (it stays visible, inert to further editing).
- `?` opens HELP maximized over the content area, listing the SETUP section's own chords (`1`-`4`,
  `w/s`/arrows, `a/d`/arrows, space, Enter, `c`) above the shared GLOBAL section.
- `c` before a map and both rosters are non-empty flashes a reason on the prompt row instead of
  committing (try it right after locking the mode, before touching MAP/PLAYER panels).
- A successful commit swaps the setup screen for the game screen in the same frame, no flicker —
  this is `Main.kt`'s `withScreen` sharing one `Terminal`/`ScreenRenderer` across `SetupApp` and
  `TuiApp` (see `docs/architecture.md`).

**Host/join over two tmux sessions** (localhost, no real network needed) exercises the lobby:

```bash
tmux new-session -d -s host -x 220 -y 50
tmux send-keys -t host 'java -jar tui/build/libs/tui.jar' Enter && sleep 2
tmux send-keys -t host 's' ''    # panel [1] MODE starts on hot-seat -- move the checkbox to host
tmux send-keys -t host 'c' ''    # lock it in
tmux capture-pane -t host -pe    # panel 1 now shows Session/Port/join-command/Player 2: waiting…
```

Once locked, panel 1 shows `Session: <id>`,
`Port: <n>`, one `join <addr>:<port> --session <id>` line per address, and `Player 2: waiting…`
— copy that exact `join` line into a second tmux session:

```bash
tmux new-session -d -s joiner -x 220 -y 50
tmux send-keys -t joiner 'java -jar tui/build/libs/tui.jar join <addr>:<port> --session <id>' Enter && sleep 2
tmux capture-pane -t joiner -pe   # the joiner's mirror: same four panels, read-only
```

Checklist for the pair:

- The host's panels 2-4 stay hidden until the joiner connects — `Player 2: waiting…` flips to
  `Player 2: connected` at the same moment MAP/PLAYER 1/PLAYER 2 first appear on the HOST side.
- The joiner sees the identical panel set the instant it connects (no separate "waiting" screen
  of its own) — read-only: `space`/`a`/`d`/`c` do nothing there, but `1`-`4`/arrows still move its
  own focus/scroll (a display preference, not an edit).
- Any selection the host makes (map, a count) appears on the joiner's mirror within about a
  second (`ServerMessage.LobbySelections`, resent on every host change).
- Committing on the HOST session (`c`) flips both sessions into the game screen at once.

Kill both sessions when done (`tmux kill-session -t host`, `tmux kill-session -t joiner`).

## tmux recipe

```bash
tmux new-session -d -s btech -x 220 -y 50
tmux send-keys -t btech 'java -jar tui/build/libs/tui.jar hot-seat --theme dark' Enter && sleep 3
tmux capture-pane -t btech -pe                # inspect output, WITH styling (see below)
tmux send-keys -t btech '<key>' ''            # send keystroke ('Tab','Enter','Escape','Up','c'…)
tmux kill-session -t btech
```

The jar must be built first (`./gradlew :tui:shadowJar`). Kill the session
when done.

Launch with any of the six themes to spot-check it specifically:

```bash
java -jar tui/build/libs/tui.jar hot-seat --theme dark
java -jar tui/build/libs/tui.jar hot-seat --theme light
java -jar tui/build/libs/tui.jar hot-seat --theme dark-256
java -jar tui/build/libs/tui.jar hot-seat --theme light-256
java -jar tui/build/libs/tui.jar hot-seat --theme dark-16
java -jar tui/build/libs/tui.jar hot-seat --theme light-16
```

**Use `capture-pane -pe`, not plain `-p`, when checking a theme.** Plain `-p` strips all ANSI
styling — every theme claim is about which SGR bytes get emitted, so `-p` cannot verify any of
them; `-e` keeps the escape sequences in the captured text.

### Theme visual checklist

- Full-surface painting: no cell shows the terminal's own background instead of the theme's —
  scroll to a screen edge and check the fill goes all the way to it.
- Terrain distinctions (**truecolor themes only** — `dark`/`light`): clear, light woods, heavy
  woods, shallow water, deep water, and rough are six visibly different hex fills. At `*-256` and
  `*-16`, every terrain hex shares the same fill; material reads from the icon glyph and color
  instead — do not expect fill differences there.
- Terrain brightness (**truecolor themes only** — `dark`/`light`): light woods and shallow water
  read visibly brighter than heavy woods and deep water. In `*-256` and `*-16`, terrain fills are
  shared; woods density is carried by glyph/color, while shallow and deep water intentionally share
  the same glyph/color.
- Elevation badges: elevation 1, 2, and 3+ hexes show distinct badge-background tiers at every
  theme, since the badge is the one place elevation tier survives even in the reduced themes.
- Player contrast over water: a Player 1 unit is legible over both shallow and deep water (the
  historical blue-on-blue failure mode this palette was built to avoid).
- Marker visibility: the line-of-sight dot and the selected-target glyph sit at the hex's
  top-center cell, clear of unit glyphs and movement overlays.
