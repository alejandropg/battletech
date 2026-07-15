# CLAUDE.md

## Project Overview

BattleTech Rules Engine is a multi-module project implementing BattleTech, hexagonal board tabletop, turn-based, game rules.

## Technology Stack

- **Gradle**: 9.6
  - Kotlin DSL
  - modular architecture and convention plugins for build configuration in `buildSrc/`
- **Kotlin**: 2.4
- **JVM**: 25
- **JUnit**: with Jupiter API/Engine for testing

## Essential Commands

### Build and Test

```bash
# Build entire project
./gradlew build

# Build the app TUI fat JAR (single-file distributable) to deploy and run
./gradlew :tui:shadowJar

# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :<module>:test

# Run a single test class
./gradlew :strategic:test --tests "battletech.strategic.StrategicRulesTest"

# Run the TUI application (build first)
# Visual spot-checks only (automated tests are the primary strategy; use tmux when you need
# to verify TUI rendering that can't be expressed as a unit test)
tmux new-session -d -s btech -x 220 -y 50
tmux send-keys -t btech 'java -jar tui/build/libs/tui.jar' Enter && sleep 3
tmux capture-pane -t btech -p                 # inspect output
tmux send-keys -t btech '<key>' ''            # send keystroke ('Tab','Enter','Escape','Up','c'…)
tmux kill-session -t btech
```

## Architecture

### Module Structure

The project uses a layered module architecture:

- **`strategic/`**
  - Library module for strategic-level game rules (campaign movement, logistics, aerospace, etc.)
  - Not used yet, can be ignored.
- **`tactical/`**
  - Library module for tactical-level game rules (combat, to-hit calculations, etc.)
  - Is delivery-agnostic — no UI assumptions, no I/O. The TUI (and any future `web/` or `remote-server/` module) consumes it through the same public surface.
- **`bt/`**
  - Application entry point is `battletech.MainKt`
- **`tui/`**
  - Terminal UI application using [Mordant](https://github.com/ajalt/mordant)
  - Uses the Shadow plugin (`com.gradleup.shadow`) to produce a fat JAR and self-executing binary
  - Entry point is `battletech.tui.MainKt`

Dependencies flow: `bt` → `strategic` + `tactical`, `tui` → `tactical` (libraries are independent of each other)

### Architecture principles

OOP + SOLID + KISS + DRY + YAGNI

### Architecture invariants

- **Server-authoritative**: one `BattleSession` per match owns state; deliveries (TUI, future web/remote) never mutate `GameState` directly.
- **Command-driven**: state changes flow through `session.submitCommand(GameCommand)`. After applying the handler, the session **auto-cascades** through any phase whose `isComplete` is true (firing each new phase's `onEntry` and emitting `PhaseChanged`), stopping at the next player phase. `session.advance()` is a one-shot kickstart used at game start to fire `InitiativePhaseHandler.onEntry` and cascade to `MOVEMENT`.
- **PhaseHandler strategy**: adding a phase = adding a handler and registering it in `BattleSession.standardHandlers()`. Handlers are stateless — all data flows through arguments and `PhaseOutcome`. System phases (Initiative/Heat/End) do their work in `onEntry`, report `isComplete = true`, and rely on the cascade.
- **Coarse commit-on-intent commands + rich per-player queries**: the TUI asks `PlayerView` "what is legal right now?" then submits a single coarse command (`MoveUnit`, `CommitAttackImpulse`). Don't add fine-grained mutation commands; build the next coarse command instead.
- **Per-player projection is the only read path for hidden info**: the game has real hidden information — a player sees only public values for units they don't own. It's enforced by a single projection seam, not per-render checks: deliveries read state only through `session.stateFor(viewer)` / `session.logFor(viewer)`, never raw `GameState`. Redaction is type-enforced, not a discipline problem — `ForeignUnit` (`query/VisibleUnit.kt`) has no `gunnerySkill`/`currentHeat`/`internalStructure` field, so a leak is a compile error. Match-over reveal is a deliberate `revealAll` inside the projection, not an accident of a null viewer. The wire (`GameServer`) carries only this projection across all three outbound paths (snapshot, `StatePush` delta, `JoinAccepted` log) — untrusted/modified clients are in scope.
- **Subscription is canonical**: `session.subscribe(listener)` is how remote/web clients will receive `GameEvent`s — session-wide and unfiltered, as a raw event feed. That's fine because it isn't the redaction seam: per-player enforcement happens once, at `stateFor`/`logFor` above, and `GameServer` builds every outbound message through them, never from a raw subscribed event. `CommandResult.Accepted.events` is a synchronous courtesy returned to the submitter; do not rely on it for cross-player notification.
- **No raw `Random`**: always go through `DiceRoller`. Seeded tests must match production roll order.

## Tool Preferences

- **Always use the LSP tool** for code intelligence operations: finding references, go-to-definition, hover info, document/workspace symbols, call hierarchy, implementations. Never fall back to Grep/Glob for tasks the LSP can handle.
- **Never use `git -C <path>` when session is already opened in the project root**; breaks permission rules and is redundant.

