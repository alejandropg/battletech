# Architecture detail

Package-level and invariant-rationale detail for the structure summarized in `CLAUDE.md`. Read this when navigating within a module or moving content between doc tiers; not needed for everyday session context. Touching `buildSrc/` or a `build.gradle.kts`: `docs/build.md`. Adding or changing a wire-crossing sealed variant: `docs/wire-protocol.md`.

## Where context docs live

Two tiers, distinguished by **how they load** — not by topic:

| Tier | Loads | Holds |
|---|---|---|
| Root `CLAUDE.md` | Always in context | Every rule and prohibition, stated completely but tersely — never its rationale |
| `docs/` (this file, `build.md`, `wire-protocol.md`, `rules/`, `agents/`, `tui-testing.md`, `color-themes.md`) | On demand, when something chooses to read it | Explanation, enumeration, history — what you look up once you know you have a question |

**The ownership test**: does the content *prevent* a decision or *explain* one? Prevent → root `CLAUDE.md`. Explain → `docs/`. A referenced doc is only reached by someone who already suspects it applies, so every trigger has to be in root: the rules warning, the boundary rule, the `DiceRoller` rule. Each compresses to a line, which is why they are stated outright rather than pointed at.

**Never restate across tiers — move ownership instead of copying.** Live example: the `tactical/` boundary rule is stated in full in root `CLAUDE.md` (a prohibition, one line); the `Intent:` paragraph explaining *why* those packages are leaves lives below in this file, and the `standardHandlers()` registration order lives here too (you only need it once you already know you are adding a phase, and it changes whenever one is added).

**No per-module `CLAUDE.md` files.** They were tried and removed: a `<module>/CLAUDE.md` loads whenever *any* file in that module is touched, not when its content is actually needed, and `tactical/` alone is 61% of the source files. That made it near-permanently loaded while restating what root already said. A module file would only pay for itself if the module's conventions could not compress into a line or two of root **and** the module were touched by a minority of sessions — neither holds here.

**No `@path` imports in `CLAUDE.md`** — they expand eagerly at load and cost the same as inlining, which defeats the tiering. Plain backtick path mentions only.

**`docs/agents/` filenames are fixed.** `issue-tracker.md` and `triage-labels.md` look like an obvious merge — 32 lines between them, and the first links to the second for its `Status:` role strings — but both are referenced by path from outside this repo, where a rename cannot follow. Do not merge, rename, or move them, despite their size and their overlap.

**Granularity inside `docs/rules/` is deliberate.** The rules docs cross-link densely, but almost every link points at one of three hubs (`armor-damage.md`, `pilot.md`, `critical-hits.md`) — a conditional jump ("if damage penetrates, go here"), not evidence the files are one document. `index.md` is what makes a small file cheap to route to, so a 34-line `water.md` costs nothing. The one real triangle (`armor-damage ↔ critical-hits ↔ pilot`) is 24KB combined; merging it would make every single lookup pay for the rare full damage chain.

## Package layout per module

- **`tactical/`** (`battletech.tactical.*`) — delivery-agnostic: every delivery (TUI, `network`, any future web UI) consumes it through the same public surface. `attack/` (incl. `physical/`, `weapon/` — attack resolution/declarations/crit tables for melee vs. gunnery, plus their own `PhaseHandler`s — see "Phase handlers live with their rules" below), `dice/` (`DiceRoller` abstraction), `heat/` (generation/dissipation/phase resolution, incl. `HeatPhaseHandler`), `io/` (`ResourceOrFileLoader` — the generic "existing filesystem path, else packaged classpath resource named `<dir>/<spec>.json`, else `<dir>/index.json` names the built-ins" loader shared by `model/map`'s map loading and `tui`'s theme loading; domain-free, no BattleTech types in its own signature), `model/` (incl. `map/` — core `GameState`/`GameMap`/hex coordinates, map loading as a thin `io.ResourceOrFileLoader` adapter), `movement/` (cost/reachability/phase handler), `query/` (per-player read/projection layer — `PlayerView`), `rules/` (`RuleResult`, `Warning`, `RuleRejection` — the shared vocabulary `attack/`'s rules return, extracted from `query/`/`session/` so the two most-imported attack abstractions depend on a leaf instead of the read layer), `session/` (`BattleSession`, `GameCommand`/`GameEvent`, the system-phase handlers with no rules package of their own, redaction), `unit/` (incl. `VisibleUnit`/`ForeignUnit`/`CombatUnit` — the redaction hierarchy; `CombatUnit` implements `VisibleUnit` directly, there is no separate "OwnUnit" type).
- **`network/`** (`battletech.network.*`): `client/` (`ClientGameSession`), `server/` (`GameServer`, `SocketAcceptor`), `transport/` (`ServerConnection`/`ClientConnection` port + the `JsonLineConnection` and `InMemoryConnection` adapters), `wire/` (`Messages`, `SessionId`, `WireJson`). Reuses `tactical.session`/`tactical.query` types directly as wire DTOs (`GameCommand`, `GameEvent`, `PlayerGameState`, `LogEntry`, `TurnState`) rather than redefining them.
- **`tenter/`** (`tenter.*`) — a standalone terminal-UI toolkit over [Mordant](https://github.com/ajalt/mordant); the one module deliberately meant to be lifted out into its own library later. `screen/` (`Canvas`/`ScreenBuffer`/`Cell`/`Insets`, `ColorRole` — the marker interface hosts implement their own roles against, with the toolkit's own `ChromeRole` enum nested in the same file — `PaletteColor` (its `parse(raw, level)` companion owns `#RRGGBB`/xterm-256/ANSI-16 value parsing) and `RolePalette`, the map-backed `MapRolePalette` implementation, the diffing `ScreenRenderer`, the memoizing `StyleTagCache`), `view/` (`View` and its implementations only — layout decorators `Padded`/`Bordered`/`Viewport`/`scrollingPanel`/`ScrollingPanel`, sibling-composition decorators `Columns`/`Stack`, the pure `ScrollGeometry` math, leaf views `HelpView`/`FlashMessage`, and `TextCursor` — the row-cursor over a `Canvas` that `widget/` paints through, including `TextCursor.draw(View)`, the measure-then-place primitive `Stack` itself is built on), `widget/` (reusable fragments painted into a `TextCursor` rather than a raw `Canvas`: `Checkbox`/`CheckState`/`Gauge`/`ValueRow`/`PipTrack` — including `PipTrack.drawAdvancing`, the cursor-advancing sibling of `PipTrack.draw`), `text/` (dependency-free text metrics shared by `screen/` and `view/`: `CellWidth`/`TextWrap`/`TextTruncation`), `panel/` (`PanelId` — now a bare marker interface, no `badge`; `PanelState`, the generic `Panel<K, I>` — takes its own `badge: Char?`, `PanelSet`, `PanelLayout`, `VerticalTitleView`), `input/` (`InputAction`/`KeyBinding`/`HintGroup`/`KeyLayer`/`KeyMap` — the declarative keybinding machinery every delivery's own keymap is built from; a binding's chord is a plain Mordant `KeyboardEvent`, normalized by `KeyboardEvent.normalized()` and validated in `KeyBinding`'s `init`, since `tenter` adds vocabulary on top of Mordant rather than wrapping it — see "`tenter` does not hide Mordant" below; `MouseInput` — the mouse-wheel workaround only, its keyboard mappings absorbed into `KeyMap`; plus `KeyGlyph`/`KeyHint`/`KeySection`/`PanAction`/`ScrollAction`), `terminal/` (`TerminalEvent` + the raw-mode/resize `Flow` producers, `terminalEvents` taking its quit predicate as a parameter rather than importing one). No `battletech.*` imports anywhere in this module — see the invariant in `CLAUDE.md` and `tenter/src/test/kotlin/tenter/ArchitectureTest.kt`. Dependencies run one way — `text`/`input` are leaves; `screen` → `text`; `terminal` → `input`; `view` → `input`/`screen`/`text`; `widget`/`panel` → `view` (+ `screen`) — never the reverse. `tenter/src/test/kotlin/tenter/LayeringTest.kt` (Konsist) enforces the full matrix, not just the general shape.
- **`tui/`** (`battletech.tui.*`) — the BattleTech terminal UI, built on `tenter`. Uses [Clikt](https://github.com/ajalt/clikt) for the CLI (`host`/`join`/`server` subcommands, bare invocation = hot-seat). Entry point `battletech.tui.MainKt`. `game/` (incl. `phase/` — app state, phase-specific UI logic like `AttackPhase`/`MovementPhase`/`WeaponAllocation`; each `Phase` declares a `keyContext: ContextId`, its address into `Keybindings`' `KeyMap`; `GamePanelId` — this app's `tenter.panel.PanelId`, now a bare marker enum with no badge of its own), `hex/` (hex-grid rendering/geometry — a `BoardRole`-flavored consumer of `tenter.screen`), `icon/` (`FontIcons.kt` — the app-wide NerdFont/Unicode glyph vocabulary: log-line markers, pip/ammo/infinity glyphs, dice faces, plus the hex-facing/terrain/movement icons `hex/` itself consumes; not `battletech.tui.hex` because most of its callers are outside the board — log formatting, the record sheet, weapon/pilot tracks), `input/` (`ContextId` — the nine key-layer addresses; `Keybindings` — the domain facade over `tenter.input.KeyMap`, its `DEFAULT` the one declarative table of every binding, plus `badgeFor`/`hints`/`isQuit`; `ChromeAction`/`IdleAction`/`BrowsingAction`/`FacingAction`/`AttackAction` — the per-context `InputAction` families; `BoardClick` — the one mouse-click action, produced by `RunLoop`; `BoardMouse` — the mouse-to-hex mapping), `loop/` (`RunLoop` + `UiEvent`, the headless-testable event/render loop — composes `tenter.terminal`'s flows and `tenter.panel`'s `Panel`/`PanelSet`/`PanelLayout` into the game's own frame; resolves both keyboard and mouse input into `InputAction`s via `Keybindings` before a `Phase` ever sees them), `screen/` (`BoardRole` — the terrain/movement/player color roles — plus `ThemeFile`/`ThemeLoader`/`resolveTheme`, which load the six built-in `RolePalette`s from packaged theme files under `theme/`; `Theme` here is `internal typealias Theme = tenter.screen.MapRolePalette` — the app-specific pieces are the on-disk schema (`ThemeFile`'s `chrome`/`board`/`heatScale` role tables) and the loader, not the palette type or its color-value parsing, both of which live in `tenter` now; see `docs/color-themes.md`), `view/` (the board and every side panel built on `tenter.view`'s decorators and `tenter.panel`'s `Panel<GamePanelId, PanelInputs>`/`PanelSet<GamePanelId, PanelInputs>`, aliased `GamePanel`/`GamePanelSet`; `Workspace` owns the `GamePanelSet` for one run; `view/record/` — the maximized UNIT STATUS panel's graphical record sheet).
- **`strategic/` + `bt/`** — placeholders. `strategic` holds one stub class (`calculateCampaignMovement(d) = d * 2`); `bt` (`battletech.MainKt`) is a hello-world that prints it. Ignore unless explicitly asked.

## Map and theme loading share one loader

Map and theme loading share one implementation, `battletech.tactical.io.ResourceOrFileLoader<T>`: `GameMapLoader` (`tactical`) and `ThemeLoader` (`tui`) are both thin adapters that hand it a `label` (`"Map"`/`"Theme"`), a `build(text, name) -> T`, and an exception constructor, and get "existing path is authoritative, else `<dir>/<spec>.json` from the classpath, else name the built-ins from `<dir>/index.json`" for free — including the built-in-name listing on a not-found error, which both `map/index.json` and `theme/index.json` now supply via the shared `<dir>/index.json` → `{"names": [...]}` schema. `ResourceOrFileLoader` lives in `tactical` (not `tui`) because that is the only module both a map loader and a theme loader can depend on without breaking the one-way dependency graph — `tactical` takes no `project(...)` dependencies of its own, and `tui` already depends on `tactical`. It carries no BattleTech-domain types in its own signature despite living in `tactical`, by the same discipline `tenter` uses for `battletech`-freedom, just not Konsist-enforced here. `ResourceOrFileLoader` only ever gets the file to text and hands it to `build`; the theme file's *own* concerns split three ways — `ThemeLoader` (loading), `ThemeFile` (the on-disk schema and role-table validation), and `tenter.screen.PaletteColor.parse` (the `#RRGGBB`/xterm-256/ANSI-16 color-value syntax, since that's a fact about `PaletteColor` itself, not about theme files) — see the `tenter/` package-layout entry above. Packaging detail (how `map/`/`theme/` land in the shadow jar): `docs/build.md`.

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

**`tenter` does not hide Mordant**: it adds primitives *on top of* Mordant, it is not an
abstraction layer over it. Mordant's own types cross `tenter`'s surface freely and deliberately —
`TerminalEvent.Input` carries an `InputEvent`, `MouseInput.scrollDelta` takes a `MouseEvent`,
`terminalEvents` takes a `(KeyboardEvent) -> Boolean`, and a `KeyBinding`'s chord *is* a
`KeyboardEvent`. Someone building on `tenter` can reach for Mordant directly whenever they want to,
with no conversion at the seam and no parallel vocabulary to learn. So a wrapper type is only worth
introducing where it carries something Mordant's does not: a `KeyChord` holding the same four
fields as `KeyboardEvent` earned nothing, and the one thing it did carry — rejecting a chord that
could never be resolved — belongs on `KeyBinding`, since it is a *binding* that can be silently
dead, not a keystroke. (The Konsist allowlist in `ArchitectureTest` reflects this: `com.github.ajalt.*`
is permitted anywhere in the module, unlike every other third-party dependency.)

**Keybindings are data, not `when` branches**: every keyboard binding in `tui` is a chord-to-action
value in one `KeyMap<ContextId>` (`Keybindings.DEFAULT`, built in `tui/src/main/kotlin/battletech/
tui/input/Keybindings.kt`), not a statement scattered across `RunLoop` and each `Phase.handle`.
Resolution precedence is an ordered list of active contexts — `CHROME` and, when a side panel is
focused, the shadowing `PANEL_SCROLL` layer, always first; the active phase's own context, last,
and omitted entirely once the match has ended — computed fresh every frame in `RunLoop.
activeContexts`, not encoded as `if`/`when` order the way it was before this table existed. A
board click is the one input that does *not* go through the table (the mouse is deliberately not
bindable), so it carries the match-ended gate by hand; both halves live together in
`RunLoop.resolveInput` precisely so they cannot drift apart, and `RunLoopInputResolutionTest`
checks them there rather than through rendered output — once the match ends `Workspace.render`
swaps the status bar for the match-over line and stops drawing flash text, so a blocked input and
a handled one produce the same frame and any assertion on output passes vacuously. A
`Phase` never sees a key or a mouse coordinate, only the resolved `InputAction`; the HELP panel's
sections and every panel's border badge are *derived* from the same table (`Keybindings.hints`/
`badgeFor`) rather than hand-maintained copies that could drift from what a chord actually does.
Four `KeybindingsTest` invariants enforce the shape this depends on: `CHROME`'s chords never
collide with a non-shadowing context's; every declared `ContextId` has a layer; every binding's
hint group is declared and every declared group is credited by a binding (or marked
`bindingless`); and every binding's action matches its context's declared action family — the
property that makes narrowing an `InputAction` back down to (say) `BrowsingAction` inside a
`Phase.handle` safe by construction rather than a runtime hope.

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

**Why every player is a client**: a seat sitting at this terminal reaches the session the same way a seat across the internet does — `GameServer` cannot tell them apart, and nothing outside `main()` can either. The seam is `transport/`'s `ServerConnection`/`ClientConnection` port: remote seats get `JsonLineConnection` (newline-delimited JSON over a socket), local seats get `InMemoryConnection` (two queues passing message objects, no serialization). Hot-seat is therefore a `GameServer` with two `connectLocal()` clients and no listening socket at all; `host` is one `connectLocal()` client plus a `SocketAcceptor`; `server` is an acceptor with no local client; `join` is a lone `ClientGameSession`.

The board (`GameMap`) is a join-time payload, not something a client picks: it arrives once, inside `ServerMessage.JoinAccepted`, and the CLI's `--map` flag exists only on the modes that build a `GameState` (`Local`/`Host`/`Server`), never on `join` — see `docs/wire-protocol.md`. Because every seat is a client through the same `attach` handshake, every seat — a real socket joiner and a `connectLocal()` hot-seat/host seat alike — runs the identical local-map check (`compareWithLocalMap`) against the host's map and logs a `MapIdentified` notice, uniformly, rather than branching on "is this hot-seat?".

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

## Wire protocol

Discriminator convention, its two enforcing tests, and `PROTOCOL_VERSION`: `docs/wire-protocol.md`.
