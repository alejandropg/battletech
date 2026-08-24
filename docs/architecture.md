# Architecture detail

Package-level, build-plugin, and invariant-rationale detail for the structure summarized in `CLAUDE.md`. Read this when navigating within a module or touching `buildSrc/`; not needed for everyday session context.

## Where context docs live

Two tiers, distinguished by **how they load** — not by topic:

| Tier | Loads | Holds |
|---|---|---|
| Root `CLAUDE.md` | Always in context | Every rule and prohibition, stated completely but tersely — never its rationale |
| `docs/` (this file, `rules/`, `agents/`, `tui-testing.md`, `color-themes.md`) | On demand, when something chooses to read it | Explanation, enumeration, history — what you look up once you know you have a question |

**The ownership test**: does the content *prevent* a decision or *explain* one? Prevent → root `CLAUDE.md`. Explain → `docs/`. A referenced doc is only reached by someone who already suspects it applies, so every trigger has to be in root: the rules warning, the boundary rule, the `DiceRoller` rule. Each compresses to a line, which is why they are stated outright rather than pointed at.

**Never restate across tiers — move ownership instead of copying.** Live example: the `tactical/` boundary rule is stated in full in root `CLAUDE.md` (a prohibition, one line); the `Intent:` paragraph explaining *why* those packages are leaves lives below in this file, and the `standardHandlers()` registration order lives here too (you only need it once you already know you are adding a phase, and it changes whenever one is added).

**No per-module `CLAUDE.md` files.** They were tried and removed: a `<module>/CLAUDE.md` loads whenever *any* file in that module is touched, not when its content is actually needed, and `tactical/` alone is 61% of the source files. That made it near-permanently loaded while restating what root already said. A module file would only pay for itself if the module's conventions could not compress into a line or two of root **and** the module were touched by a minority of sessions — neither holds here.

**No `@path` imports in `CLAUDE.md`** — they expand eagerly at load and cost the same as inlining, which defeats the tiering. Plain backtick path mentions only.

## Package layout per module

- **`tactical/`** (`battletech.tactical.*`): `attack/` (incl. `physical/`, `weapon/` — attack resolution/declarations/crit tables for melee vs. gunnery, plus their own `PhaseHandler`s — see "Phase handlers live with their rules" below), `dice/` (`DiceRoller` abstraction), `heat/` (generation/dissipation/phase resolution, incl. `HeatPhaseHandler`), `io/` (`ResourceOrFileLoader` — the generic "existing filesystem path, else packaged classpath resource named `<dir>/<spec>.json`, else `<dir>/index.json` names the built-ins" loader shared by `model/map`'s map loading and `tui`'s theme loading; domain-free, no BattleTech types in its own signature), `model/` (incl. `map/` — core `GameState`/`GameMap`/hex coordinates, map loading as a thin `io.ResourceOrFileLoader` adapter), `movement/` (cost/reachability/phase handler), `query/` (per-player read/projection layer — `PlayerView`), `rules/` (`RuleResult`, `Warning`, `RuleRejection` — the shared vocabulary `attack/`'s rules return, extracted from `query/`/`session/` so the two most-imported attack abstractions depend on a leaf instead of the read layer), `session/` (`BattleSession`, `GameCommand`/`GameEvent`, the system-phase handlers with no rules package of their own, redaction), `unit/` (incl. `VisibleUnit`/`ForeignUnit`/`CombatUnit` — the redaction hierarchy; `CombatUnit` implements `VisibleUnit` directly, there is no separate "OwnUnit" type).
- **`network/`** (`battletech.network.*`): `client/` (`ClientGameSession`), `server/` (`GameServer`, `SocketAcceptor`), `transport/` (`ServerConnection`/`ClientConnection` port + the `JsonLineConnection` and `InMemoryConnection` adapters), `wire/` (`Messages`, `SessionId`, `WireJson`). Reuses `tactical.session`/`tactical.query` types directly as wire DTOs (`GameCommand`, `GameEvent`, `PlayerGameState`, `LogEntry`, `TurnState`) rather than redefining them.
- **`tenter/`** (`tenter.*`): `screen/` (`Canvas`/`ScreenBuffer`/`Cell`/`Insets`, `ColorRole` — the marker interface hosts implement their own roles against, with the toolkit's own `ChromeRole` enum nested in the same file — `PaletteColor` (its `parse(raw, level)` companion owns `#RRGGBB`/xterm-256/ANSI-16 value parsing) and `RolePalette`, the map-backed `MapRolePalette` implementation, the diffing `ScreenRenderer`, the memoizing `StyleTagCache`), `view/` (`View` and its implementations only — layout decorators `Padded`/`Bordered`/`Viewport`/`scrollingPanel`/`ScrollingPanel`, sibling-composition decorators `Columns`/`Stack`, the pure `ScrollGeometry` math, leaf views `HelpView`/`FlashMessage`, and `TextCursor` — the row-cursor over a `Canvas` that `widget/` paints through, including `TextCursor.draw(View)`, the measure-then-place primitive `Stack` itself is built on), `widget/` (reusable fragments painted into a `TextCursor` rather than a raw `Canvas`: `Checkbox`/`CheckState`/`Gauge`/`ValueRow`/`PipTrack` — including `PipTrack.drawAdvancing`, the cursor-advancing sibling of `PipTrack.draw`), `text/` (dependency-free text metrics shared by `screen/` and `view/`: `CellWidth`/`TextWrap`/`TextTruncation`), `panel/` (`PanelId`, `PanelState`, the generic `Panel<K, I>`, `PanelSet`, `PanelLayout`, `VerticalTitleView`), `input/` (`ChromeInput` — quit/panel-chord/state-cycle/scroll/pan mappings — plus `KeyGlyph`/`KeyHint`/`KeySection`/`PanAction`/`ScrollAction`), `terminal/` (`TerminalEvent` + the raw-mode/resize `Flow` producers). No `battletech.*` imports anywhere in this module — see the invariant in `CLAUDE.md` and `tenter/src/test/kotlin/tenter/ArchitectureTest.kt`. Dependencies run one way, `text`/`input` (leaves) → `screen`/`terminal` → `view` → `widget`/`panel` (never the reverse) — `tenter/src/test/kotlin/tenter/LayeringTest.kt` (Konsist) enforces the full matrix, not just the general shape.
- **`tui/`** (`battletech.tui.*`): `game/` (incl. `phase/` — app state, phase-specific UI logic like `AttackPhase`/`MovementPhase`/`WeaponAllocation`; `GamePanelId` — this app's `tenter.panel.PanelId`), `hex/` (hex-grid rendering/geometry — a `BoardRole`-flavored consumer of `tenter.screen`), `icon/` (`FontIcons.kt` — the app-wide NerdFont/Unicode glyph vocabulary: log-line markers, pip/ammo/infinity glyphs, dice faces, plus the hex-facing/terrain/movement icons `hex/` itself consumes; not `battletech.tui.hex` because most of its callers are outside the board — log formatting, the record sheet, weapon/pilot tracks), `input/` (`InputMapper` — the domain key mappings; `Keymap`'s BattleTech hint strings), `loop/` (`RunLoop` + `UiEvent`, the headless-testable event/render loop — composes `tenter.terminal`'s flows and `tenter.panel`'s `Panel`/`PanelSet`/`PanelLayout` into the game's own frame), `screen/` (`BoardRole` — the terrain/movement/player color roles — plus `ThemeFile`/`ThemeLoader`/`resolveTheme`, which load the six built-in `RolePalette`s from packaged theme files under `theme/`; `Theme` here is `internal typealias Theme = tenter.screen.MapRolePalette` — the app-specific pieces are the on-disk schema (`ThemeFile`'s `chrome`/`board`/`heatScale` role tables) and the loader, not the palette type or its color-value parsing, both of which live in `tenter` now; see `docs/color-themes.md`), `view/` (the board and every side panel built on `tenter.view`'s decorators and `tenter.panel`'s `Panel<GamePanelId, PanelInputs>`/`PanelSet<GamePanelId, PanelInputs>`, aliased `GamePanel`/`GamePanelSet`; `Workspace` owns the `GamePanelSet` for one run; `view/record/` — the maximized UNIT STATUS panel's graphical record sheet).

## buildSrc convention plugins

Applied via `id("battletech.<name>")`:

- **`battletech.kotlin-common`** — base for every module: applies `kotlin("jvm")`, sets the JVM toolchain (JVM 21 when `CLAUDE_CODE` env var is set — Claude Cloud constraint — otherwise the catalog version), enables `explicitApi()`, configures JUnit Platform + test logging, wires standard test deps (JUnit BOM/bundle, MockK, AssertJ).
- **`battletech.kotlin-library`** — applies `kotlin-common`; used by `strategic`, `tactical`, `network`, `tenter`.
- **`battletech.kotlin-application`** — applies `kotlin-common` + the `application` plugin; used by `bt`, `tui`.
- **`battletech.kotlin-serialization`** — applies the Kotlin serialization plugin; used by `tactical` and `network` (both need kotlinx-serialization for `GameState`/wire types).

`tenter/build.gradle.kts` additionally applies the stock `java-test-fixtures` plugin (not a `battletech.*` convention plugin) to publish `ViewTestSupport.kt`'s `render`/`renderInPanel`/buffer helpers as `testFixtures(project(":tenter"))`, consumed by `tui`'s own tests — a fixture used across module boundaries belongs in `testFixtures`, not duplicated per consumer.

## Enforced package boundaries

`tactical/src/test/kotlin/battletech/tactical/ArchitectureTest.kt` uses Konsist to assert an
**allowed-dependency matrix** between the direct child packages of `battletech.tactical` (a file
under `attack/physical/`, `attack/weapon/`, or `model/map/` counts as its parent). Every edge not
listed is a violation — this replaced four narrower prohibition tests (attack ⊥ movement,
model/dice as leaves) that verified only those specific edges and left the rest of the graph,
including a gratuitous `attack ⇄ query` cycle since closed by extracting `rules/`, unchecked:

```
dice, io  -> (nothing in tactical)
rules     -> model, unit
model     -> io, unit
unit      -> dice, model
heat      -> attack, dice, model, rules, session, unit
movement  -> dice, heat, model, rules, session, unit
attack    -> dice, heat, model, rules, session, unit
query     -> attack, dice, model, movement, rules, session, unit
session   -> attack, dice, heat, model, movement, query, rules, unit
```

`model/`, `dice/`, `io/`, and `rules/` are leaves relative to `session/`/`query/` — nothing above
them may be imported back into them. Two edges above are real cycles, not oversights:

- **`heat ⇄ attack`**: weapon fire generates heat (`attack → heat`); heat-phase resolution causes
  ammo cook-off and pilot hits (`heat → attack`).
- **`movement/attack → session`**: the `PhaseHandler` strategy pattern — `MovementPhaseHandler`,
  `WeaponAttackPhaseHandler`, and `PhysicalAttackPhaseHandler` live with the rules they drive,
  `BattleSession` registers and drives them (see "Phase handlers live with their rules" below).
  `PhaseOutcome` carries `GameEvent`/`TurnState`, so the SPI can't be extracted from `session/`
  without pulling those types with it.

This is enforced, not aspirational: it fails `./gradlew :tactical:test` (via `ArchitectureTest`)
the moment an unlisted import is added, and a second test asserts every package above has at
least one real file — the four prohibition tests this replaced passed vacuously if their package
filter matched zero files, so a rename could silently disable a rule.

`tui/src/test/kotlin/battletech/tui/ArchitectureTest.kt` enforces the *locality-is-an-adapter*
invariant (`CLAUDE.md`) mechanically: only `battletech/tui/Main.kt` may import
`battletech.network.*`, and `tui` may not import `battletech.strategic.*`.
`tenter/src/test/kotlin/tenter/LayeringTest.kt` enforces the internal-layering matrix named in
`tenter/`'s package-layout entry above — `tenter/src/test/kotlin/tenter/ArchitectureTest.kt`
enforces the module's *external* seam (no `battletech.*`, nothing outside the third-party
allowlist) but nothing previously checked the seams between `tenter`'s own packages.

## Module dependency edges

- `network → tactical`: `api(project(":tactical"))` in `network/build.gradle.kts` — deliberately transitive (not `implementation`). `network` re-exports `tactical` types (`GameCommand`, `GameEvent`, `PlayerGameState`, `LogEntry`, `TurnState`) directly as wire DTOs instead of redefining them, so consumers of `network` need `tactical`'s types on their compile classpath too.
- `bt → strategic`, `bt → tactical`: both `implementation(project(...))` in `bt/build.gradle.kts`.
- `tui → tactical`, `tui → network`, `tui → tenter`: all `implementation(project(...))` in `tui/build.gradle.kts`; `tui` additionally takes `testImplementation(testFixtures(project(":tenter")))` for shared rendering test helpers.
- `strategic`, `tactical`, and `tenter` declare no `project(...)` dependencies on other modules (`strategic/build.gradle.kts`, `tactical/build.gradle.kts`, `tenter/build.gradle.kts`). `tenter` depends only on `mordant` and `kotlinx-coroutines-core` (both `api`, since `Terminal`/`InputEvent`/`Flow` types appear in its own public surface) — no BattleTech module may appear on its classpath, enforced per the invariant in `CLAUDE.md`.

## TUI packaging

`tui/build.gradle.kts` applies `alias(libs.plugins.shadow)` — `com.gradleup.shadow`, version `9.4.2` per `gradle/libs.versions.toml`.

Tactical's `processResources` copies the repository's root `map/` directory into the tactical runtime resources under `map/`. Because `tui` depends on `tactical`, Shadow JAR assembly includes those packaged maps transitively; map resources are not configured separately on `shadowJar`.

Themes are packaged the same way but one module closer to the jar: `tui/build.gradle.kts` configures its OWN `processResources` (tactical's block cannot reach it) to copy the repository's root `theme/` directory into `tui`'s runtime resources under `theme/`, so the six built-in `theme/*.json` files and `theme/index.json` land in the shadow jar directly rather than transitively. See `docs/color-themes.md` for the file format.

Map and theme loading share one implementation, `battletech.tactical.io.ResourceOrFileLoader<T>`: `GameMapLoader` (`tactical`) and `ThemeLoader` (`tui`) are both thin adapters that hand it a `label` (`"Map"`/`"Theme"`), a `build(text, name) -> T`, and an exception constructor, and get "existing path is authoritative, else `<dir>/<spec>.json` from the classpath, else name the built-ins from `<dir>/index.json`" for free — including the built-in-name listing on a not-found error, which both `map/index.json` and `theme/index.json` now supply via the shared `<dir>/index.json` → `{"names": [...]}` schema. `ResourceOrFileLoader` lives in `tactical` (not `tui`) because that is the only module both a map loader and a theme loader can depend on without breaking the one-way dependency graph — `tactical` takes no `project(...)` dependencies of its own, and `tui` already depends on `tactical`. It carries no BattleTech-domain types in its own signature despite living in `tactical`, by the same discipline `tenter` uses for `battletech`-freedom, just not Konsist-enforced here. `ResourceOrFileLoader` only ever gets the file to text and hands it to `build`; the theme file's *own* concerns split three ways — `ThemeLoader` (loading), `ThemeFile` (the on-disk schema and role-table validation), and `tenter.screen.PaletteColor.parse` (the `#RRGGBB`/xterm-256/ANSI-16 color-value syntax, since that's a fact about `PaletteColor` itself, not about theme files) — see the `tenter/` package-layout entry above.

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

**Why the projection seam works**: the game has real hidden information — a player sees only public values for units they don't own. This is enforced at a single projection seam, not by per-render checks. `ForeignUnit` (`tactical/src/main/kotlin/battletech/tactical/unit/ForeignUnit.kt`) simply has no `gunnerySkill`/`currentHeat`/`internalStructure` field, so a leak is a compile error rather than a discipline problem. `VisibleUnit.kt` (same package) holds the sealed interface both projections implement; there is no separate "OwnUnit" type — `CombatUnit` (also `unit/`) implements `VisibleUnit` directly and carries the full record-sheet data itself. Match-over reveal is a deliberate `revealAll` flag threaded into the projection (`BattleSession.stateFor`/`logFor`, gated on `_matchOver`), not an accident of a null viewer — a null viewer (spectator) still gets the redacted view unless the match has ended.

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

## Phase handlers live with their rules

A `PhaseHandler` implementation lives in the package whose rules it drives, not in `session/`:
`MovementPhaseHandler` is in `movement/`, `WeaponAttackPhaseHandler` in `attack/weapon/`,
`PhysicalAttackPhaseHandler` in `attack/physical/`, `HeatPhaseHandler` in `heat/`. System phases
with no rules package of their own — `InitiativePhaseHandler`, `EndPhaseHandler` — stay in
`session/`, since there is nowhere else for them to live. `HeatPhaseHandler` moved from
`session/` to `heat/` to make this convention actually hold; before that move it was the one
handler that didn't follow it, for no stated reason.

This is *why* `movement/` and `attack/` are allowed to import `session/` in the dependency
matrix above: the handler needs `PhaseOutcome`/`GameEvent`/`TurnState`/`GameCommand` from
`session/`, and `BattleSession.standardHandlers()` needs the handler class from the rules
package — the SPI (`PhaseHandler`, `PhaseOutcome`) can't be extracted to break the cycle without
also moving the session types `PhaseOutcome` carries.

## Wire discriminators and `PROTOCOL_VERSION`

None of the `@Serializable` sealed hierarchies under `battletech.tactical.session`
(`RejectionReason`/`CommandRejection`, `GameEvent`, `GameCommand`) carry `@SerialName` on their
existing variants — `network/wire/WireJson.kt` sets `classDiscriminator = "type"`, and with no
`@SerialName`, kotlinx.serialization falls back to the variant's **fully-qualified class name**
as that discriminator string. This means moving or renaming one of those variants' *package* —
not just adding/removing a variant — silently changes the wire format: old and new builds would
disagree about what a message even is, and `network`'s round-trip tests can't catch it, since
they encode and decode with the same code on both sides of the assertion.

`battletech.tactical.rules.RuleRejection` and `battletech.tactical.session.CommandRejection` do
carry `@SerialName` on every variant (added when `RuleRejection` moved `session/` → `rules/`,
`PROTOCOL_VERSION` bumped `1` → `2` in the same change) — new variants anywhere in this wire
surface should follow that precedent rather than leave the FQN fallback in place, and a change
that isn't purely additive should bump `PROTOCOL_VERSION` (`network/wire/Messages.kt`), which a
mismatched client already rejects cleanly via `JoinRejectionReason.INCOMPATIBLE_PROTOCOL`.
`network/src/test/kotlin/battletech/network/wire/WireFormatRoundTripTest.kt` pins the literal
JSON for one `RuleRejection` and one `GameEvent` variant specifically to catch a future
discriminator change that round-tripping alone would miss.
