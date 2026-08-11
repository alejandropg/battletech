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

## tmux recipe

```bash
tmux new-session -d -s btech -x 220 -y 50
tmux send-keys -t btech 'java -jar tui/build/libs/tui.jar --theme dark' Enter && sleep 3
tmux capture-pane -t btech -pe                # inspect output, WITH styling (see below)
tmux send-keys -t btech '<key>' ''            # send keystroke ('Tab','Enter','Escape','Up','c'…)
tmux kill-session -t btech
```

The jar must be built first (`./gradlew :tui:shadowJar`). Kill the session
when done.

Launch with any of the six themes to spot-check it specifically:

```bash
java -jar tui/build/libs/tui.jar --theme dark
java -jar tui/build/libs/tui.jar --theme light
java -jar tui/build/libs/tui.jar --theme dark-256
java -jar tui/build/libs/tui.jar --theme light-256
java -jar tui/build/libs/tui.jar --theme dark-16
java -jar tui/build/libs/tui.jar --theme light-16
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
- Light woods vs. heavy woods, and shallow vs. deep water: light/shallow reads visibly brighter
  than heavy/deep (truecolor and `*-256`; glyph-only at `*-16`).
- Elevation badges: elevation 1, 2, and 3+ hexes show distinct badge-background tiers at every
  theme, since the badge is the one place elevation tier survives even in the reduced themes.
- Player contrast over water: a Player 1 unit is legible over both shallow and deep water (the
  historical blue-on-blue failure mode this palette was built to avoid).
- Marker visibility: the line-of-sight dot and the selected-target glyph sit at the hex's
  top-center cell, clear of unit glyphs and movement overlays.
