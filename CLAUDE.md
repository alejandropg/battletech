# CLAUDE.md

## Project Overview

BattleTech is a multi-module project implementing BattleTech, hexagonal board tabletop, turn-based, game rules.

## Technology Stack

- Kotlin 2.4
- JVM 25
- Gradle 9.6.1 (Kotlin DSL, convention plugins in `buildSrc/`)
- JUnit Jupiter + MockK + AssertJ for tests
- `explicitApi()` is on repo-wide — declare `public`/`internal` explicitly.

## Essential Commands

```bash
# Build / test
./gradlew build
./gradlew test
./gradlew :<module>:test
./gradlew :tactical:test --tests "battletech.tactical.attack.HitLocationTest"   # single class

# Build and run the TUI (`:tui:run` throws by design — Gradle forks a JVM with no TTY)
./gradlew :tui:shadowJar && java -jar tui/build/libs/tui.jar
```

Visual TUI spot-checks: see `docs/tui-testing.md`. Automated tests are the primary strategy.

## Architecture

### Module Structure

Dependencies flow: `tui` → `tactical` + `network`; `network` → (`api`) `tactical`; `bt` → `strategic` + `tactical`.

- **`tactical/`** — the engine: tactical-level rules (combat, to-hit, movement, heat). Delivery-agnostic — no UI assumptions, no I/O. Every delivery (TUI, `network`, any future web UI) consumes it through the same public surface.
- **`network/`** — client/server layer over `tactical` (`GameServer`, `RemoteGameSession`, wire protocol). No UI. Depends on `tactical` via `api` and reuses its types as wire DTOs rather than redefining them.
- **`tui/`** — terminal UI using [Mordant](https://github.com/ajalt/mordant). Entry point `battletech.tui.MainKt`.
- **`strategic/` + `bt/`** — placeholders. `strategic` holds one stub class (`calculateCampaignMovement(d) = d * 2`); `bt` (`battletech.MainKt`) is a hello-world that prints it. Ignore unless explicitly asked.

`docs/architecture.md` — package layout inside each module, buildSrc convention plugins, invariant rationale. Read when navigating inside a module or touching the build; not needed for everyday context.

### Architecture principles

OOP + SOLID + KISS + DRY + YAGNI

### Architecture invariants

- **Server-authoritative**: one `BattleSession` per match owns state; deliveries (TUI, `network`, future web UI) never mutate `GameState` directly.
- **Command-driven**: state changes flow through `session.submitCommand(GameCommand)`. The session auto-cascades through any phase whose `isComplete` is true (firing each new phase's `onEntry`, emitting `PhaseChanged`), stopping at the next player phase. `session.advance()` is the one-shot kickstart at game start.
- **PhaseHandler strategy**: adding a phase = adding a handler and registering it in `standardHandlers()` (private, in `BattleSession.kt`). Handlers are stateless — all data flows through arguments and `PhaseOutcome`. System phases (Initiative/Heat/End) do their work in `onEntry`, report `isComplete = true`, and rely on the cascade.
- **Coarse commit-on-intent commands + rich per-player queries**: ask `PlayerView` what is legal right now, then submit a single coarse command (`MoveUnit`, `CommitAttackImpulse`). Don't add fine-grained mutation commands; build the next coarse command instead.
- **Per-player projection is the only read path for hidden info**: read state only via `session.stateFor(viewer)` / `session.logFor(viewer)`, never raw `GameState`. Redaction is type-enforced — `ForeignUnit` (`query/ForeignUnit.kt`) has no `gunnerySkill`/`currentHeat`/`internalStructure` field, so a leak is a compile error. Rationale: `docs/architecture.md`.
- **Subscription is canonical**: `session.subscribe(listener)` is the raw, session-wide event feed for remote/web clients — it is not the redaction seam. `CommandResult.Accepted.events` is a courtesy to the submitter; do not rely on it for cross-player notification.
- **Package boundaries in `tactical` are test-enforced**: `attack/` and `movement/` must not import each other; `model/` and `dice/` must not import `movement`/`attack`/`session`/`query`. `ArchitectureTest` (Konsist) fails the build on violation.
- **No raw `Random`**: always go through `DiceRoller`. Seeded tests must match production roll order.

## Tool Preferences

- **Always use the LSP tool** for code intelligence operations: finding references, go-to-definition, hover info, document/workspace symbols, call hierarchy, implementations. Never fall back to Grep/Glob for tasks the LSP can handle.
- **Never use `git -C <path>` when session is already opened in the project root**; breaks permission rules and is redundant.
