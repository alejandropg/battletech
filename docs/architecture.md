# Architecture detail

Package-level, build-plugin, and invariant-rationale detail for the structure summarized in `CLAUDE.md`. Read this when navigating within a module or touching `buildSrc/`; not needed for everyday session context.

## Where context docs live

Two tiers, distinguished by **how they load** — not by topic:

| Tier | Loads | Holds |
|---|---|---|
| Root `CLAUDE.md` | Always in context | Every rule and prohibition, stated completely but tersely — never its rationale |
| `docs/` (this file, `rules/`, `tui-testing.md`) | On demand, when something chooses to read it | Explanation, enumeration, history — what you look up once you know you have a question |

**The ownership test**: does the content *prevent* a decision or *explain* one? Prevent → root `CLAUDE.md`. Explain → `docs/`. A referenced doc is only reached by someone who already suspects it applies, so every trigger has to be in root: the rules warning, the boundary rule, the `DiceRoller` rule. Each compresses to a line, which is why they are stated outright rather than pointed at.

**Never restate across tiers — move ownership instead of copying.** Live example: the `tactical/` boundary rule is stated in full in root `CLAUDE.md` (a prohibition, one line); the `Intent:` paragraph explaining *why* those packages are leaves lives below in this file, and the `standardHandlers()` registration order lives here too (you only need it once you already know you are adding a phase, and it changes whenever one is added).

**No per-module `CLAUDE.md` files.** They were tried and removed: a `<module>/CLAUDE.md` loads whenever *any* file in that module is touched, not when its content is actually needed, and `tactical/` alone is 61% of the source files. That made it near-permanently loaded while restating what root already said. A module file would only pay for itself if the module's conventions could not compress into a line or two of root **and** the module were touched by a minority of sessions — neither holds here.

**No `@path` imports in `CLAUDE.md`** — they expand eagerly at load and cost the same as inlining, which defeats the tiering. Plain backtick path mentions only.

## Package layout per module

- **`tactical/`** (`battletech.tactical.*`): `attack/` (incl. `physical/`, `weapon/` — attack resolution/declarations/crit tables for melee vs. gunnery), `dice/` (`DiceRoller` abstraction), `heat/` (generation/dissipation/phase resolution), `model/` (incl. `map/` — core `GameState`/`GameMap`/hex coordinates, filesystem/classpath map loading), `movement/` (cost/reachability/phase handler), `query/` (per-player read/projection layer — `PlayerView`, `ForeignUnit`/`OwnUnit` redaction types), `session/` (`BattleSession`, `GameCommand`/`GameEvent`, phase handlers, redaction), `unit/`.
- **`network/`** (`battletech.network.*`): `client/` (`ClientGameSession`), `server/` (`GameServer`, `SocketAcceptor`), `transport/` (`ServerConnection`/`ClientConnection` port + the `JsonLineConnection` and `InMemoryConnection` adapters), `wire/` (`Messages`, `SessionId`, `WireJson`). Reuses `tactical.session`/`tactical.query` types directly as wire DTOs (`GameCommand`, `GameEvent`, `PlayerGameState`, `LogEntry`, `TurnState`) rather than redefining them.
- **`tenter/`** (`tenter.*`): `screen/` (`Canvas`/`ScreenBuffer`/`Cell`/`Insets`/`CellWidth`/`TextWrap`, `ColorRole`/`UiRole`/`PaletteColor`/`RolePalette`, the diffing `ScreenRenderer`), `view/` (`View`, layout decorators `Padded`/`Bordered`/`Scrolled`/`scrollingPanel`, widgets `ContentWriter`/`CollapsedPanelView`/`Checkbox`/`HelpView`/`GaugeBar`/`ValueRow`), `panel/` (`PanelKey`, the generic `Panel<K, I>`, `PanelLayout`, `FlashMessage`), `input/` (`ChromeInput` — quit/panel-chord/scroll/pan mappings — plus `KeyGlyph`/`KeyHint`/`KeySection`/`PanAction`), `terminal/` (`TerminalEvent` + the raw-mode/resize `Flow` producers). No `battletech.*` imports anywhere in this module — see the invariant in `CLAUDE.md` and `tenter/src/test/kotlin/tenter/ArchitectureTest.kt`.
- **`tui/`** (`battletech.tui.*`): `game/` (incl. `phase/` — app state, phase-specific UI logic like `AttackPhase`/`MovementPhase`/`WeaponAllocation`), `hex/` (hex-grid rendering/geometry — a `HexRole`-flavored consumer of `tenter.screen`), `input/` (`InputMapper` — the domain key mappings; `Keymap`'s BattleTech hint strings), `loop/` (`RunLoop` + `UiEvent`, the headless-testable event/render loop — composes `tenter.terminal`'s flows and `tenter.panel`'s `Panel`/`PanelLayout` into the game's own frame), `screen/` (`BoardRole` — the terrain/movement/player color roles — plus `TuiTheme` and the six concrete `RolePalette`s), `view/` (board/panel rendering built on `tenter.view`'s decorators and `tenter.panel`'s `Panel<PanelId, PanelInputs>`, aliased `GamePanel`).

## buildSrc convention plugins

Applied via `id("battletech.<name>")`:

- **`battletech.kotlin-common`** — base for every module: applies `kotlin("jvm")`, sets the JVM toolchain (JVM 21 when `CLAUDE_CODE` env var is set — Claude Cloud constraint — otherwise the catalog version), enables `explicitApi()`, configures JUnit Platform + test logging, wires standard test deps (JUnit BOM/bundle, MockK, AssertJ).
- **`battletech.kotlin-library`** — applies `kotlin-common`; used by `strategic`, `tactical`, `network`, `tenter`.
- **`battletech.kotlin-application`** — applies `kotlin-common` + the `application` plugin; used by `bt`, `tui`.
- **`battletech.kotlin-serialization`** — applies the Kotlin serialization plugin; used by `tactical` and `network` (both need kotlinx-serialization for `GameState`/wire types).

`tenter/build.gradle.kts` additionally applies the stock `java-test-fixtures` plugin (not a `battletech.*` convention plugin) to publish `ViewTestSupport.kt`'s `render`/`renderInPanel`/buffer helpers as `testFixtures(project(":tenter"))`, consumed by `tui`'s own tests — a fixture used across module boundaries belongs in `testFixtures`, not duplicated per consumer.

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
- `tui → tactical`, `tui → network`, `tui → tenter`: all `implementation(project(...))` in `tui/build.gradle.kts`; `tui` additionally takes `testImplementation(testFixtures(project(":tenter")))` for shared rendering test helpers.
- `strategic`, `tactical`, and `tenter` declare no `project(...)` dependencies on other modules (`strategic/build.gradle.kts`, `tactical/build.gradle.kts`, `tenter/build.gradle.kts`). `tenter` depends only on `mordant` and `kotlinx-coroutines-core` (both `api`, since `Terminal`/`InputEvent`/`Flow` types appear in its own public surface) — no BattleTech module may appear on its classpath, enforced per the invariant in `CLAUDE.md`.

## TUI packaging

`tui/build.gradle.kts` applies `alias(libs.plugins.shadow)` — `com.gradleup.shadow`, version `9.4.2` per `gradle/libs.versions.toml`.

Tactical's `processResources` copies the repository's root `map/` directory into the tactical runtime resources under `map/`. Because `tui` depends on `tactical`, Shadow JAR assembly includes those packaged maps transitively; map resources are not configured separately on `shadowJar`.

`tasks.shadowJar` is configured with `archiveBaseName = "tui"`, `archiveClassifier = ""`, `archiveVersion = ""`, so the fat jar lands at exactly `tui/build/libs/tui.jar` (no `-all`/version suffix). `mergeServiceFiles()` is set to correctly merge `META-INF/services` entries from bundled dependencies.

The `createExecutable` task (group `distribution`) depends on `shadowJar` and prepends a POSIX shell stub — `#!/bin/sh\nexec java -jar "$0" "$@"\n` — to the shadow jar's bytes, writing the result to `build/tui` and marking it executable. A shell script prepended to a zip/jar is still a valid jar (the JVM's zip reader scans from the end of the file), so `build/tui` is simultaneously a runnable shell script and a runnable jar.

## Why `RunLoop` and `Workspace` stayed in `tui`

`tenter` took the render core, the view/layout decorators, the panel framework, and the terminal
input/event plumbing — everything that was already generic as written. Two pieces that look
similarly mechanical stayed behind on purpose:

- **`RunLoop`** (`tui/loop/RunLoop.kt`) is BattleTech's own event-dispatch policy — phase
  handling, match-over gating, flash-message lifecycle, session resync — built *from*
  `tenter.terminal`'s event flows and `tenter.panel`'s `Panel`/`PanelLayout`, not a generic loop
  itself. Generalizing it would mean inventing an event-loop abstraction with exactly one client
  today; that's a seam with nothing on the other side of it yet.
- **`Workspace`** (`tui/view/Workspace.kt`) is the frame *composition* — where the board and
  which panels go, in what order, under what title — for this one game. `tenter.panel.PanelLayout`
  already extracted the actual geometry math (`compute`/`slotAt`) that `Workspace` calls into;
  what's left is BattleTech-specific composition, not reusable machinery.

If a second delivery (a future web UI) ever needs the same event-loop shape, that's the signal to
extract it — not before.

## Invariants: rationale

Supporting detail for the architecture invariants stated tersely in `CLAUDE.md`.

**Why the projection seam works**: the game has real hidden information — a player sees only public values for units they don't own. This is enforced at a single projection seam, not by per-render checks. `ForeignUnit` (`tactical/src/main/kotlin/battletech/tactical/query/ForeignUnit.kt`) simply has no `gunnerySkill`/`currentHeat`/`internalStructure` field, so a leak is a compile error rather than a discipline problem. `VisibleUnit.kt` holds the sealed interface both projections implement; `OwnUnit.kt` holds the owning-player variant, which additionally carries the full `CombatUnit`. Match-over reveal is a deliberate `revealAll` flag threaded into the projection (`BattleSession.stateFor`/`logFor`, gated on `_matchOver`), not an accident of a null viewer — a null viewer (spectator) still gets the redacted view unless the match has ended.

**Why session-wide subscription is safe**: `BattleSession.subscribe(listener)` (`tactical/src/main/kotlin/battletech/tactical/session/BattleSession.kt`) delivers every `GameEvent` to every listener, unfiltered and session-wide — but it is not the redaction seam. Per-player enforcement happens once, at `stateFor`/`logFor`. `GameServer` builds every outbound message through those two methods — across all three outbound paths (snapshot, `StatePush` delta, `JoinAccepted` log) — and never constructs a message from a raw subscribed event. Untrusted/modified clients are in scope for this guarantee. A `connectLocal()` seat crosses that same seam (it is a client like any other, see below) and so gets the same redaction, even though an in-process seat is never the adversary the guarantee is written against.

**Why every player is a client**: a seat sitting at this terminal reaches the session the same way a seat across the internet does — `GameServer` cannot tell them apart, and nothing outside `main()` can either. The seam is `transport/`'s `ServerConnection`/`ClientConnection` port: remote seats get `JsonLineConnection` (newline-delimited JSON over a socket), local seats get `InMemoryConnection` (two queues passing message objects, no serialization). Hot-seat is therefore a `GameServer` with two `connectLocal()` clients and no listening socket at all; `host` is one `connectLocal()` client plus a `SocketAcceptor`; `serve` is an acceptor with no local client; `join` is a lone `ClientGameSession`.

This is not uniformity for its own sake. When the local player had a private path (a `submitCommand` override on `GameServer`), the seat check existed twice — derived from the connection's assigned seat on the remote path, a hardcoded `PlayerId.PLAYER_1` on the local one — and the two could disagree. They did: with both seats remote, a `PLAYER_1` command passed both gates despite the class documenting that it "stays frozen". Making the local player a client deletes the second path, so the guarantee ("neither side can act as another seat's") has one place to live. The same collapse happened in the TUI, where `localPlayer: PlayerId?` pinned the viewer *and* gated input, both keyed on null-means-hot-seat; `TuiApp` now takes the seats it drives (`Map<PlayerId, GameSession>`) and hot-seat simply holds both, so the gate never fires because of what the map contains rather than because anything checked.

Consequences worth knowing: `GameServer` is deliberately **not** a `GameSession` (there is no single local seat for it to implement one *for*); its remaining reads are plain non-override members serving the headless console, which has no viewer to project for and legitimately needs `revealAll`. `SocketAcceptor` owns the `ServerSocket`, accept loop and port precisely so a server can exist without any of them. And `close()` on both adapters must kill *both* directions — a socket does this at the fd level, so `InMemoryConnection` poisons both queues to match; `ConnectionPortSymmetryTest` asserts the two adapters are indistinguishable through the port, which is the design claim itself.

**`standardHandlers()`**: a `private fun` inside a `private companion object` in `BattleSession.kt`, used only as the default value of the `handlers` constructor parameter. That makes it the right place to register a new phase handler, but it is not public API callable from outside `BattleSession`. In registration order it currently lists:

1. `InitiativePhaseHandler`
2. `MovementPhaseHandler`
3. `WeaponAttackPhaseHandler`
4. `PhysicalAttackPhaseHandler`
5. `HeatPhaseHandler`
6. `EndPhaseHandler`
