# CLAUDE.md

## Project Overview

BattleTech Rules Engine is a multi-module project implementing BattleTech, hexagonal board tabletop, turn-based, game rules.

## Technology Stack

- **Gradle**: 9.3.1
  - Kotlin DSL
  - modular architecture and convention plugins for build configuration in `buildSrc/`
- **Kotlin**: 2.3.0
- **JVM**: 25
- **JUnit**: 6.0.2 with Jupiter API/Engine for testing

## Essential Commands

### Build and Test

```bash
# Build entire project
./gradlew build

# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :strategic:test
./gradlew :tactical:test
./gradlew :bt:test

# Run a single test class
./gradlew :strategic:test --tests "battletech.strategic.StrategicRulesTest"

# Run the application
./gradlew :bt:run

# Build TUI fat JAR (single-file distributable)
./gradlew :tui:createExecutable  # → tui/build/tui (self-executing, Unix/macOS)

# Run the TUI application
# (Gradle always forks a JVM detached from the terminal, so use the JAR directly)
./gradlew :tui:shadowJar && java -jar tui/build/libs/tui.jar
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

### Architecture invariants

These are fixed. Don't relitigate them — see `docs/refactor-tactical-domain.md` for the rationale.

- **Server-authoritative**: one `BattleSession` per match owns state; deliveries (TUI, future web/remote) never mutate `GameState` directly.
- **Command-driven**: state changes flow through `session.submitCommand(GameCommand)`. After applying the handler, the session **auto-cascades** through any phase whose `isComplete` is true (firing each new phase's `onEntry` and emitting `PhaseChanged`), stopping at the next player phase. `session.advance()` is a one-shot kickstart used at game start to fire `InitiativePhaseHandler.onEntry` and cascade to `MOVEMENT`.
- **PhaseHandler strategy**: adding a phase = adding a handler and registering it in `BattleSession.standardHandlers()`. Handlers are stateless — all data flows through arguments and `PhaseOutcome`. System phases (Initiative/Heat/End) do their work in `onEntry`, report `isComplete = true`, and rely on the cascade.
- **Coarse commit-on-intent commands + rich per-player queries**: the TUI asks `PlayerView` "what is legal right now?" then submits a single coarse command (`MoveUnit`, `CommitAttackImpulse`). Don't add fine-grained mutation commands; build the next coarse command instead.
- **Subscription is canonical**: `session.subscribe(playerId, listener)` is how remote/web clients will receive `GameEvent`s. Each event passes through `EventVisibility.filterFor` so hidden-info redaction lands without subscriber-side changes. `CommandResult.Accepted.events` is a synchronous courtesy returned to the submitter; do not rely on it for cross-player notification.
- **No raw `Random`**: always go through `DiceRoller`. Seeded tests must match production roll order.

## Tool Preferences

- **Always use the LSP tool** for code intelligence operations: finding references, go-to-definition, hover info, document/workspace symbols, call hierarchy, implementations. Never fall back to Grep/Glob for tasks the LSP can handle.
