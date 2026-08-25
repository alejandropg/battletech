# CLAUDE.md

## Project Overview

BattleTech is a multi-module project implementing BattleTech, hexagonal board tabletop, turn-based, game rules.

## Docs (read on demand)

Not needed for everyday context — read the row's doc when its trigger applies.

| Doc | Read when |
|---|---|
| `docs/rules/index.md` | Implementing, changing, or reviewing a game rule, or a magic number in code or a test expectation is a rules value. It routes to the one doc owning each rule — trust that doc over a number restated elsewhere, and over general BattleTech knowledge. |
| `docs/architecture.md` | Navigating inside a module or moving content between doc tiers. Package layout, invariant rationale. |
| `docs/build.md` | Touching `buildSrc/`, a `build.gradle.kts`, module dependencies, or `map/`/`theme/`/shadow-jar packaging. |
| `docs/wire-protocol.md` | Adding or changing a wire-crossing `@Serializable sealed` variant, a failing wire-discriminator test, or a non-additive protocol change. |
| `docs/tui-testing.md` | Hand-checking TUI rendering that can't be expressed as a unit test; automated tests are the primary strategy. |
| `docs/color-themes.md` | Touching `theme/*.json`, palette roles, or the theme contrast/distinctness guarantees. |

## Technology Stack

- Kotlin 2.4
- JVM 25
- Gradle 9.7.1 (Kotlin DSL, convention plugins in `buildSrc/`)
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

## Architecture

### Module Structure

Dependencies flow: `tui` → `tactical` + `network` + `tenter`; `network` → (`api`) `tactical`; `bt` → `strategic` + `tactical`.

- **`tactical/`** — the engine: tactical rules (combat, to-hit, movement, heat). Delivery-agnostic: no UI assumptions, no console I/O.
- **`network/`** — client/server layer over `tactical` (`GameServer`, `SocketAcceptor`, `ClientGameSession`, `transport/`, wire protocol). No UI; reuses `tactical`'s types as wire DTOs rather than redefining them.
- **`tenter/`** — BattleTech-free terminal-UI toolkit (`screen`/`view`/`widget`/`panel`/`input`/`terminal`/`text`) over Mordant; meant to be extractable as a library.
- **`tui/`** — the BattleTech terminal UI, built on `tenter`. Entry point `battletech.tui.MainKt`.
- **`strategic/` + `bt/`** — placeholders. Ignore unless explicitly asked.

### Architecture principles

OOP + SOLID + KISS + DRY + YAGNI

### Architecture invariants

- **Server-authoritative**: one `BattleSession` per match owns state; deliveries (TUI, `network`, future web UI) never mutate `GameState` directly.
- **Command-driven**: state changes flow through `session.submitCommand(GameCommand)`. The session auto-cascades through any phase whose `isComplete` is true (firing each new phase's `onEntry`, emitting `PhaseChanged`), stopping at the next player phase. `session.advance()` is the one-shot kickstart at game start.
- **PhaseHandler strategy**: adding a phase = adding a handler and registering it in `standardHandlers()` (private, in `BattleSession.kt`). Handlers are stateless — all data flows through arguments and `PhaseOutcome`. System phases (Initiative/Heat/End) do their work in `onEntry`, report `isComplete = true`, and rely on the cascade.
- **Coarse commit-on-intent commands + rich per-player queries**: ask `PlayerView` what is legal right now, then submit a single coarse command (`MoveUnit`, `CommitAttackImpulse`). Don't add fine-grained mutation commands; build the next coarse command instead.
- **Per-player projection is the only read path for hidden info**: read state only via `session.stateFor(viewer)` / `session.logFor(viewer)`, never raw `GameState`. Redaction is type-enforced — a leak is a compile error, not a discipline problem. Rationale: `docs/architecture.md`.
- **Every player is a client — locality is an adapter, not a branch**: local and remote seats both reach the session through `transport/`'s connection port (`InMemoryConnection` / `JsonLineConnection`). `GameServer` cannot tell them apart and is deliberately NOT a `GameSession`. `main()` is the only place that knows which mode ran — do not add a branch anywhere else asking "is this hot-seat?", and do not give a local player a private path to the session. Rationale: `docs/architecture.md`.
- **Subscription is canonical**: `session.subscribe(listener)` is the raw, session-wide event feed for clients — it is not the redaction seam. `CommandResult.Accepted.events` is a courtesy to the submitter; do not rely on it for cross-player notification.
- **Package boundaries in `tactical` are test-enforced**: an allowed-dependency matrix between `attack/`, `dice/`, `heat/`, `io/`, `model/`, `movement/`, `query/`, `rules/`, `session/`, `unit/` — `model`/`dice`/`io`/`rules` are leaves relative to `session`/`query`. `ArchitectureTest` (Konsist) fails the build on any unlisted import; rationale and the two allowed cycles (`heat ⇄ attack`, `movement`/`attack` → `session`) are in `docs/architecture.md`.
- **Phase handlers live with their rules**: a `PhaseHandler` implementation lives in the package whose rules it drives (e.g. `movement/`, `attack/weapon/`), not in `session/`. System phases with no rules package of their own (`InitiativePhaseHandler`, `EndPhaseHandler`) stay in `session/`. Full registration order: `docs/architecture.md`.
- **Every wire-crossing sealed variant needs a `@SerialName` following the one convention — test-enforced**: `WireJson` uses `classDiscriminator = "type"`, and an unannotated variant's discriminator defaults to its fully-qualified class name, welding the wire format to package layout. The convention: the serial name is the variant's lexical nesting path relative to its package, decapitalized and dot-joined, dropping only as many leading segments as needed to stay unique within its hierarchy (`RuleRejection.NotAdjacent` → `"notAdjacent"`; `GameEvent`'s `UnitStoodUp.Detailed` → `"unitStoodUp.detailed"`, since a bare `"detailed"` would collide with sibling `Detailed`s elsewhere in that hierarchy). This fails the build on a missing, invented, or colliding value; test paths and the golden-file mechanism are in `docs/wire-protocol.md`. Bump `PROTOCOL_VERSION` (`network/wire/Messages.kt`) on any non-additive change to `GameCommand`/`GameEvent`/`CommandRejection`/`RuleRejection`/etc.
- **`tenter` is BattleTech-free — Konsist-enforced**: no file under `tenter/` may import anything starting with `battletech.`, and every import there must resolve to `tenter.*`, `kotlin*`, `java*`, or `com.github.ajalt.*` (an allowlist, so a new third-party dependency also fails the build).
- **`tenter`'s internal layering is test-enforced**: `text`/`input` are leaves; `widget`/`panel` sit at the top — never the reverse. Full matrix: `docs/architecture.md`.
- **No raw `Random`**: always go through `DiceRoller`. Seeded tests must match production roll order.

## Tool Preferences

- **Always use the LSP tool** for code intelligence operations: finding references, go-to-definition, hover info, document/workspace symbols, call hierarchy, implementations. Never fall back to Grep/Glob for tasks the LSP can handle.
- **Never use `git -C <path>` when session is already opened in the project root**; breaks permission rules and is redundant.
