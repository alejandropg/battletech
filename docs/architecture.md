# Architecture detail

Package-level, build-plugin, and invariant-rationale detail for the structure summarized in `CLAUDE.md`. Read this when navigating within a module or touching `buildSrc/`; not needed for everyday session context.

## Package layout per module

- **`tactical/`** (`battletech.tactical.*`): `attack/` (incl. `physical/`, `weapon/` — attack resolution/declarations/crit tables for melee vs. gunnery), `dice/` (`DiceRoller` abstraction), `heat/` (generation/dissipation/phase resolution), `model/` (incl. `map/` — core `GameState`/`GameMap`/hex coordinates, map file loading/catalog), `movement/` (cost/reachability/phase handler), `query/` (per-player read/projection layer — `PlayerView`, `ForeignUnit`/`OwnUnit` redaction types), `session/` (`BattleSession`, `GameCommand`/`GameEvent`, phase handlers, redaction), `unit/`.
- **`network/`** (`battletech.network.*`): `client/` (`RemoteGameSession`), `server/` (`GameServer`), `wire/` (`Messages`, `SessionId`, `WireJson`). Reuses `tactical.session`/`tactical.query` types directly as wire DTOs (`GameCommand`, `GameEvent`, `PlayerGameState`, `LogEntry`, `TurnState`) rather than redefining them.
- **`tui/`** (`battletech.tui.*`): `game/` (incl. `phase/` — app state, phase-specific UI logic like `AttackPhase`/`MovementPhase`/`WeaponAllocation`), `hex/` (hex-grid rendering/geometry), `input/` (keyboard input mapping per mode), `loop/` (terminal event flows), `screen/` (low-level terminal screen buffer/diffing), `view/` (widget/panel rendering).

## buildSrc convention plugins

Applied via `id("battletech.<name>")`:

- **`battletech.kotlin-common`** — base for every module: applies `kotlin("jvm")`, sets the JVM toolchain (JVM 21 when `CLAUDE_CODE` env var is set — Claude Cloud constraint — otherwise the catalog version), enables `explicitApi()`, configures JUnit Platform + test logging, wires standard test deps (JUnit BOM/bundle, MockK, AssertJ).
- **`battletech.kotlin-library`** — applies `kotlin-common`; used by `strategic`, `tactical`, `network`.
- **`battletech.kotlin-application`** — applies `kotlin-common` + the `application` plugin; used by `bt`, `tui`.
- **`battletech.kotlin-serialization`** — applies the Kotlin serialization plugin; used by `tactical` and `network` (both need kotlinx-serialization for `GameState`/wire types).

`explicitApi()` is on repo-wide: public API surface must be explicitly declared (`public`/`internal`).

## Enforced package boundaries

`tactical/src/test/kotlin/battletech/tactical/ArchitectureTest.kt` uses Konsist to assert import boundaries between packages under `battletech.tactical`. It scans all non-test source files and fails the build if any of these are violated:

| Source package | Must not import                              |
|----------------|----------------------------------------------|
| `attack/`      | `movement/`                                  |
| `movement/`    | `attack/`                                    |
| `model/`       | `movement/`, `attack/`, `session/`, `query/` |
| `dice/`        | `movement/`, `attack/`, `session/`, `query/` |

Intent: `model/` and `dice/` are leaf packages — nothing above them may be imported back into them. `attack/` and `movement/` are parallel verticals that stay mutually ignorant of each other. These are enforced, not aspirational: they fail `./gradlew :tactical:test` (via `ArchitectureTest`) the moment a violating import is added.

## Module dependency edges

- `network → tactical`: `api(project(":tactical"))` in `network/build.gradle.kts` — deliberately transitive (not `implementation`). `network` re-exports `tactical` types (`GameCommand`, `GameEvent`, `PlayerGameState`, `LogEntry`, `TurnState`) directly as wire DTOs instead of redefining them, so consumers of `network` need `tactical`'s types on their compile classpath too.
- `bt → strategic`, `bt → tactical`: both `implementation(project(...))` in `bt/build.gradle.kts`.
- `tui → tactical`, `tui → network`: both `implementation(project(...))` in `tui/build.gradle.kts`.
- `strategic` and `tactical` declare no `project(...)` dependencies on other modules (`strategic/build.gradle.kts`, `tactical/build.gradle.kts`).

## TUI packaging

`tui/build.gradle.kts` applies `alias(libs.plugins.shadow)` — `com.gradleup.shadow`, version `9.4.2` per `gradle/libs.versions.toml`.

`tasks.shadowJar` is configured with `archiveBaseName = "tui"`, `archiveClassifier = ""`, `archiveVersion = ""`, so the fat jar lands at exactly `tui/build/libs/tui.jar` (no `-all`/version suffix). `mergeServiceFiles()` is set to correctly merge `META-INF/services` entries from bundled dependencies.

The `createExecutable` task (group `distribution`) depends on `shadowJar` and prepends a POSIX shell stub — `#!/bin/sh\nexec java -jar "$0" "$@"\n` — to the shadow jar's bytes, writing the result to `build/tui` and marking it executable. A shell script prepended to a zip/jar is still a valid jar (the JVM's zip reader scans from the end of the file), so `build/tui` is simultaneously a runnable shell script and a runnable jar.

## Invariants: rationale

Supporting detail for the architecture invariants stated tersely in `CLAUDE.md`.

**Why the projection seam works**: the game has real hidden information — a player sees only public values for units they don't own. This is enforced at a single projection seam, not by per-render checks. `ForeignUnit` (`tactical/src/main/kotlin/battletech/tactical/query/ForeignUnit.kt`) simply has no `gunnerySkill`/`currentHeat`/`internalStructure` field, so a leak is a compile error rather than a discipline problem. `VisibleUnit.kt` holds the sealed interface both projections implement; `OwnUnit.kt` holds the owning-player variant, which additionally carries the full `CombatUnit`. Match-over reveal is a deliberate `revealAll` flag threaded into the projection (`BattleSession.stateFor`/`logFor`, gated on `_matchOver`), not an accident of a null viewer — a null viewer (spectator) still gets the redacted view unless the match has ended.

**Why session-wide subscription is safe**: `BattleSession.subscribe(listener)` (`tactical/src/main/kotlin/battletech/tactical/session/BattleSession.kt`) delivers every `GameEvent` to every listener, unfiltered and session-wide — but it is not the redaction seam. Per-player enforcement happens once, at `stateFor`/`logFor`. `GameServer` builds every outbound message through those two methods — across all three outbound paths (snapshot, `StatePush` delta, `JoinAccepted` log) — and never constructs a message from a raw subscribed event. Untrusted/modified clients are in scope for this guarantee.

**`standardHandlers()`**: a `private fun` inside a `private companion object` in `BattleSession.kt`, used only as the default value of the `handlers` constructor parameter. That makes it the right place to register a new phase handler, but it is not public API callable from outside `BattleSession`. In registration order it currently lists:

1. `InitiativePhaseHandler`
2. `MovementPhaseHandler`
3. `WeaponAttackPhaseHandler`
4. `PhysicalAttackPhaseHandler`
5. `HeatPhaseHandler`
6. `EndPhaseHandler`
