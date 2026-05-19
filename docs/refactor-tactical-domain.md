# Tactical Domain Refactor — Implementation Plan

Eight-step strangler-fig refactor that moves all game-lifecycle logic out of
`tui/` into `tactical/`, producing a delivery-agnostic domain API (commands,
events, per-player views, dice abstraction, phase handlers, session
aggregate). Outcome: TUI shrinks to UI-workflow only; web/remote deliveries
can later plug into the same `BattleSession` without re-implementing rules.

Design decisions are fixed (recorded below in **Architecture invariants**).
This document describes only the *execution sequence*.

## How to use this plan

- **Each PR section is self-contained.** A fresh session can be told
  `Read docs/refactor-tactical-domain.md and execute PR N` and find
  everything it needs to do that step in isolation.
- **No real GitHub PRs.** Each "PR" is a local commit on `main` (or a local
  branch — caller's choice). The "PR" framing is for scoping/review hygiene.
- **Validation gate per PR:** `./gradlew build` must pass. If a PR adds
  tests, they must pass too. Behaviour-preserving PRs must keep all
  pre-existing tests green without modification beyond the signature changes
  explicitly listed.
- **Suggested resume pattern after a `/clear`:**
  > "Read `docs/refactor-tactical-domain.md`. PRs 1..N are committed
  > (verify with `git log --oneline`). Execute PR N+1."

## Architecture invariants (do not relitigate)

- Server-authoritative; one `BattleSession` aggregate per match
- Coarse commit-on-intent commands + rich per-player queries
- `PlayerView` from day one (hidden info is a domain concern)
- Subscription is canonical for state changes; sync `CommandResult` is courtesy
- `PhaseHandler` strategy objects (one per phase); session delegates
- One `tactical` module; new sub-packages: `command/`, `event/`, `session/`,
  `view/`, `dice/`
- Typed sealed `RejectionReason` (replaces stringly-typed `UnavailabilityReason`)
- Fat read-model `PlayerView` + redacted `PublicGameState` projection
- `DiceRoller` strategy interface (never raw `Random` in domain)
- Command-driven; no domain `tick()`; auto-advance cascade after each command
- Match-end = `MatchEnded` event + terminal session that rejects commands

## Conventions

- Kotlin style follows the existing module (`public` modifier, sealed hierarchies,
  `data class`/`data object`).
- New packages live under `battletech.tactical.<name>`.
- New tests mirror the source package structure under `src/test/kotlin/`.
- Commit messages: `refactor(tactical): PR<N> <short description>`.
- After each PR, run: `./gradlew build` (full build + tests).

---

## PR 1 — `DiceRoller` seam

**Goal:** Replace raw `kotlin.random.Random` with a `DiceRoller` strategy
interface everywhere randomness is consumed. Behaviour-preserving.

**Files — NEW:**
- `tactical/src/main/kotlin/battletech/tactical/dice/DiceRoller.kt`
- `tactical/src/main/kotlin/battletech/tactical/dice/RandomDiceRoller.kt`
- `tactical/src/main/kotlin/battletech/tactical/dice/ScriptedDiceRoller.kt`
- `tactical/src/test/kotlin/battletech/tactical/dice/DiceRollerTest.kt`

**Files — MOD:**
- `tactical/.../action/Initiative.kt` — signature: `rollInitiative(roller: DiceRoller)`
- `tactical/.../action/attack/AttackResolution.kt` — both `random: Random` params become `roller: DiceRoller`; internal calls become `roller.d6()`
- `tactical/.../test/.../InitiativeTest.kt` — call sites use `DiceRoller.seeded(42)`
- `tactical/.../test/.../attack/AttackResolutionTest.kt` — same
- `tui/.../game/phase/PhaseServices.kt` — field `random: Random` → `roller: DiceRoller = RandomDiceRoller()`
- `tui/.../game/phase/InitiativePhase.kt` — `rollInitiative(svc.roller)`
- `tui/.../game/phase/AttackPhase.kt` — `resolveAttacks(..., svc.roller)`
- `tui/.../test/.../PhaseTickTest.kt` — `roller = DiceRoller.seeded(42)`

**Design specifics:**
- `DiceRoller` interface:
  ```kotlin
  public interface DiceRoller {
      public fun d6(): Int
      public fun d6(count: Int): List<Int> = List(count) { d6() }
      public fun roll2d6(): Int = d6() + d6()
      public companion object {
          public fun seeded(seed: Long): DiceRoller = RandomDiceRoller(kotlin.random.Random(seed))
          public fun deterministic(rolls: List<Int>): DiceRoller = ScriptedDiceRoller(rolls)
      }
  }
  ```
- `RandomDiceRoller(random: Random = Random.Default)` — `d6() = random.nextInt(1, 7)`. **Critical:** must use the *same* `nextInt(1, 7)` call that the current code does so seeded test rolls stay identical.
- `ScriptedDiceRoller(rolls: List<Int>)` — pops one roll per `d6()`; throws on overflow.
- Internal `resolveOneAttack` and `rollInitiative` switch `random.nextInt(1, 7) + random.nextInt(1, 7)` → `roller.roll2d6()` (identical in `RandomDiceRoller`).

**Validation:** `./gradlew build` — all existing tests stay green (seeded sequences identical). New `DiceRollerTest` covers `ScriptedDiceRoller` exhaustion and `roll2d6` ordering.

**Commit:** `refactor(tactical): PR1 introduce DiceRoller seam`

---

## PR 2 — Sealed `RejectionReason` / replace `UnavailabilityReason`

**Goal:** Upgrade `UnavailabilityReason` from `(code: String, description: String)` to a sealed hierarchy. Introduce `RejectionReason` umbrella that will later carry command-level rejections too.

**Files — NEW:**
- `tactical/.../command/RejectionReason.kt` — sealed root + sub-sealeds
- `tactical/.../command/CommandRejection.kt` — sealed (NotYourTurn, WrongPhase, UnitAlreadyActed, UnknownUnit, MatchOver, etc.) — most cases will be added in PR5 when used; for PR2 add only the empty `sealed interface CommandRejection : RejectionReason` to lock the boundary
- `tactical/.../command/RuleRejection.kt` — sealed branch with cases for every existing rule failure code

**Files — MOD/DEL:**
- `tactical/.../action/UnavailabilityReason.kt` — DELETE the old data class; rewrite this file to make `UnavailabilityReason` a `typealias UnavailabilityReason = RuleRejection` (or remove entirely and update imports). Prefer **alias-then-delete** to keep churn small; the alias is removed in this same PR after callers are updated.
- `tactical/.../action/RuleResult.kt` — `Unsatisfied(reason: RuleRejection)` instead of `(reason: UnavailabilityReason)`
- All existing rule classes that build a reason: `AdjacentRule.kt`, `HasAmmoRule.kt`, `HeatPenaltyRule.kt`, `InRangeRule.kt`, `LineOfSightRule.kt`, `WeaponNotDestroyedRule.kt` — replace `UnavailabilityReason(code = "...", description = "...")` with the matching `RuleRejection.<Case>(...)`.
- `tactical/.../action/Warning.kt` — leave as is unless similarly stringly-typed (audit; promote to sealed only if so).
- Any test that asserts on `.code` / `.description` of `UnavailabilityReason` → assert on type/data of `RuleRejection.<Case>`.
- TUI: search for `UnavailabilityReason` and `.code ==`/`.description` — refactor to `when (rejection) { is RuleRejection.OutOfRange -> ... }`. Likely sites: nothing if rules are only consumed in tests; do a `grep -rn UnavailabilityReason` to confirm.

**Steps:**
1. `grep -rn 'UnavailabilityReason\|RuleResult.Unsatisfied' --include='*.kt'` — enumerate every site.
2. Define the full `RuleRejection` sealed hierarchy from the enumerated `code` values seen in step 1. One case per distinct code, with data fields for any contextual info each code's description embeds.
3. Update each rule to construct the typed case.
4. Update each test to assert on type.
5. Delete `UnavailabilityReason` data class.

**Validation:** `./gradlew build` — all tests green. Stringly-typed reason codes are gone (`grep "code = \""` in `tactical/` returns nothing in rule code).

**Commit:** `refactor(tactical): PR2 typed RuleRejection replaces stringly UnavailabilityReason`

---

## PR 3 — Relocate domain state types

**Goal:** Move match-lifecycle data classes out of `tui/game/` into `tactical/session/`. No logic change; only `package` + imports.

**Files — MOVE (from `tui/src/main/kotlin/battletech/tui/game/` to `tactical/src/main/kotlin/battletech/tactical/session/`):**
- `TurnState.kt`
- `ImpulseSequence.kt`
- `ImpulseDeclarations.kt`
- `UnitDeclaration.kt`

**Files — MOVE TESTS (from `tui/src/test/.../game/` to `tactical/src/test/.../session/`):**
- `TurnStateTest.kt` (if it tests only the data class; if it pulls in `AppState`, leave only the data-class portion)

**Steps:**
1. Audit each file: does it import anything from `tui/`? If so, that's a leak that needs untangling before the move. (Spot-check expected: these are pure data classes referencing only `tactical.action.*` types — `PlayerId`, `UnitId`, `HexDirection`, `Initiative`.)
2. Change `package battletech.tui.game` → `package battletech.tactical.session` at the top of each moved file.
3. `git mv` to the new location.
4. Run `./gradlew build` — every TUI file that referenced the moved types will fail to compile. Update imports: `import battletech.tui.game.TurnState` → `import battletech.tactical.session.TurnState`. Repeat until clean.
5. The TUI keeps direct use of these types; no behavioural change yet.

**Validation:** `./gradlew build` green; `grep -rn 'battletech.tui.game.TurnState\|battletech.tui.game.ImpulseSequence' tui/` returns nothing.

**Commit:** `refactor(tactical): PR3 relocate TurnState/Impulse types to tactical.session`

---

## PR 4 — Relocate query helpers + introduce `PlayerView` / `PublicGameState`

**Goal:** Move `targetInfos`, `validTargets`, `fireArc`, `selectableUnits`, `selectableAttackUnits`, `resolveTargetPositions`, etc. out of `tui/game/` into `tactical/view/`. Define `PlayerView` interface + default impl + `PublicGameState`. TUI starts calling `playerView.foo()` instead of free functions.

**Files — NEW:**
- `tactical/.../view/PlayerView.kt` — interface with intent-oriented query methods
- `tactical/.../view/PublicGameState.kt` — data class projection (start as alias/wrapper around `GameState`; redact later)
- `tactical/.../view/DefaultPlayerView.kt` — implementation
- `tactical/.../view/TargetInfo.kt` / `WeaponTargetInfo.kt` — moved from TUI (or keep TUI versions as thin renderer DTOs if they have UI fields; the *legality* data moves into the view)

**Files — MOVE / RELOCATE LOGIC:**
- `tui/.../game/AttackView.kt` — functions like `targetInfos`, `validTargets`, `fireArc`, `losHighlights`, `selectedLosHighlights`, `resolveTargetPositions` move to `tactical/view/AttackQueries.kt` (legality bits) and the rendering-only helpers (`losHighlights`, `selectedLosHighlights` if they produce `HexHighlight`) stay in TUI but call into the view for the legality data.
- `tui/.../game/TargetInfo.kt` / `WeaponTargetInfo.kt` — move data classes to `tactical/view/`. Adjust any TUI-only fields.
- `tui/.../game/TurnState.kt` (already moved in PR3) — `selectableUnits`/`selectableAttackUnits` extension functions on `TurnState` come along.

**Files — MOD (TUI call sites):**
- `tui/.../game/phase/MovementPhase.kt`, `AttackPhase.kt` — switch direct calls to `playerView.legalMovementsFor(...)`, `playerView.targetsFor(...)`, etc.
- Phases need access to a `PlayerView`; for now obtain it from `AppState` (add `playerView: PlayerView` field to `AppState`, constructed from current `gameState` + active player).

**`PlayerView` shape for PR4:**
```kotlin
public interface PlayerView {
    public val playerId: PlayerId
    public val state: PublicGameState
    public fun legalMovementsFor(unitId: UnitId): List<MovementOption>
    public fun legalAttacksFor(attackerId: UnitId, torsoFacing: HexDirection): List<TargetInfo>
    public fun fireArc(attackerId: UnitId, torsoFacing: HexDirection): Set<HexCoordinates>
    public fun selectableMovementUnits(): List<CombatUnit>
    public fun selectableAttackUnits(): List<CombatUnit>
    // ... add as TUI call sites demand
}
```

**Steps:**
1. Inventory `grep -rn '^fun \|^internal fun \|^public fun ' tui/src/main/kotlin/battletech/tui/game/*.kt` → split into "legality query" (move) vs "render helper" (stay).
2. Create `tactical/view/` skeleton (interface, projection type, impl).
3. Move legality functions one file at a time; rerun build between moves.
4. Add `playerView` to `AppState`; rewrite TUI phase code to consume it.
5. The render helpers (`losHighlights`, `pathHighlights`, `reachabilityHighlights`) stay in TUI but call `playerView.fireArc(...)` etc. for inputs instead of building from `GameState` directly.

**Validation:** `./gradlew build` green. TUI source tree has zero direct calls to `gameState.unitAt(...)`, `gameState.units`, etc. *for legality questions*; raw rendering still reads `gameState.map`/`gameState.units` (will be replaced by `publicGameState` in PR8).

**Commit:** `refactor(tactical): PR4 introduce PlayerView and relocate query helpers`

---

## PR 5 — `GameCommand` + `GameEvent` + `BattleSession` skeleton

**Goal:** Introduce commands and events as data, plus a `BattleSession` aggregate that wraps current logic. TUI starts routing mutations through `session.submitCommand(...)` and consuming `events` from the result. No PhaseHandler extraction yet — the session internally still calls `gameState.moveUnit`, `resolveAttacks`, etc. directly in a big `when (cmd)`.

**Files — NEW:**
- `tactical/.../command/GameCommand.kt` — `sealed interface GameCommand` + cases: `MoveUnit`, `CommitAttackImpulse`, `EndPhase`, ... (only ones immediately needed by current TUI flows)
- `tactical/.../command/CommandResult.kt` — `sealed interface CommandResult { Accepted(events), Rejected(reason) }`
- `tactical/.../command/CommandRejection.kt` — populate with concrete cases now (NotYourTurn, WrongPhase, UnitAlreadyActed, UnknownUnit, MatchOver, RuleViolation(wrap RuleRejection))
- `tactical/.../event/GameEvent.kt` — `sealed interface GameEvent` + cases: `UnitMoved`, `AttackResolved`, `PhaseChanged`, `InitiativeRolled`, `TurnEnded`, `MatchEnded`
- `tactical/.../session/BattleSession.kt` — aggregate class with private `state: GameState` + `turn: TurnState`, `submitCommand`, `viewFor`. Internal logic still imperative `when`.
- `tactical/.../session/BattleSessionTest.kt` — golden-path tests for each command type

**Files — MOD:**
- `tui/.../game/AppState.kt` — replace `gameState: GameState` and `turnState: TurnState` with `session: BattleSession`. Keep cursor / phase / flash.
- TUI phase classes — wherever they call `gameState.moveUnit(...)`, build a `MoveUnit` command and submit instead. Wherever they call `resolveAttacks`, the session handles it on `CommitAttackImpulse`.
- TUI render path — read state via `session.viewFor(currentHotSeatPlayer).state` and `session.viewFor(...).turn`.

**Steps:**
1. Define command/event/result hierarchies.
2. Write `BattleSession` with `submitCommand`: a `when (cmd)` that does the same mutations the TUI did, but now centralised. Emit events accordingly.
3. Test the session in isolation (`BattleSessionTest`): give it an initial state, submit commands, assert on events + view.
4. Refactor `AppState` and TUI phases to use the session. Tests in TUI continue to pass.
5. Note: no auto-advance cascade yet; phase transitions are still hand-rolled inside `submitCommand`.

**Validation:** `./gradlew build` green. TUI never calls `gameState.moveUnit`/`resolveAttacks` directly — all via `session.submitCommand`.

**Commit:** `refactor(tactical): PR5 BattleSession + GameCommand/GameEvent skeleton`

---

## PR 6 — Extract `PhaseHandler` interface + implementations + auto-advance

**Goal:** Move phase-progression logic out of `BattleSession`'s `when (cmd)` into one `PhaseHandler` per phase. Session becomes a thin delegator with the auto-advance cascade.

**Files — NEW:**
- `tactical/.../session/PhaseHandler.kt` — interface
- `tactical/.../session/PhaseOutcome.kt` — `data class PhaseOutcome(state, turn, events)`
- `tactical/.../session/InitiativePhaseHandler.kt`
- `tactical/.../session/MovementPhaseHandler.kt`
- `tactical/.../session/WeaponAttackPhaseHandler.kt`
- `tactical/.../session/PhysicalAttackPhaseHandler.kt`
- `tactical/.../session/HeatPhaseHandler.kt`
- `tactical/.../session/EndPhaseHandler.kt`
- Tests for each handler in `tactical/src/test/.../session/`

**Files — MOD:**
- `BattleSession.kt` — constructor takes `handlers: List<PhaseHandler>` in canonical phase order; `submitCommand` becomes:
  1. Find current handler. Reject if `!handler.accepts(cmd, turn)` with appropriate `CommandRejection`.
  2. Call `handler.apply(cmd, state, turn, roller) → PhaseOutcome`.
  3. Emit outcome events.
  4. Run `advanceUntilStable()` cascade: while current handler `isComplete(turn)`, advance index, call new handler's `onEntry(state, turn, roller)` (or however we model "on-entry work"), emit `PhaseChanged`, repeat.

**Handler interface:**
```kotlin
public interface PhaseHandler {
    public val phase: TurnPhase
    public fun activePlayer(turn: TurnState): PlayerId?
    public fun accepts(cmd: GameCommand, turn: TurnState): Boolean
    public fun apply(cmd: GameCommand, state: GameState, turn: TurnState, roller: DiceRoller): PhaseOutcome
    public fun isComplete(turn: TurnState): Boolean
    public fun onEntry(state: GameState, turn: TurnState, roller: DiceRoller): PhaseOutcome =
        PhaseOutcome(state, turn, emptyList()) // default no-op; Initiative overrides
}
```

**Steps:**
1. Build handlers one at a time, starting with the simplest (`HeatPhaseHandler` — just resets heat; `EndPhaseHandler` — resets per-turn state). Each is extracted from the matching block in `BattleSession.submitCommand`.
2. As each handler lands, remove its block from `BattleSession`.
3. Implement `advanceUntilStable` last.
4. Match-end detection: after damage application in `WeaponAttackPhaseHandler` / `PhysicalAttackPhaseHandler`, check victory condition; if met, emit `MatchEnded` and switch session to terminal state.

**Validation:** `./gradlew build`; all session tests + TUI tests green. `BattleSession.submitCommand` is now ~30 lines of dispatch + cascade.

**Commit:** `refactor(tactical): PR6 extract PhaseHandler strategy + auto-advance cascade`

---

## PR 7 — TUI `Phase` classes shrink to pure UI workflow

**Goal:** TUI phases lose every reference to game-progression logic. They become pure input → command builders + cursor/hover/draft holders + render-data producers. Remove `tick()` from the TUI `Phase` interface (or make it always no-op).

**Files — MOD (in `tui/src/main/kotlin/battletech/tui/game/phase/`):**
- `Phase.kt` — drop `tick()`. Drop `selectedUnit`/`pathDestination`/etc. that just delegate to game state; either inline at view layer or have `Phase` produce a `RenderData` from a `PlayerView`.
- `MovementPhase.kt` — `SelectingUnit`, `Browsing`, `SelectingFacing` retain *only* UI state (cursor, hover, mode index). `commitMove(...)` becomes `session.submitCommand(MoveUnit(...))`. No `gameState.moveUnit`, no `TurnState.advanceAfterUnitMoved`, no `attackOrderFor`.
- `AttackPhase.kt` — `SelectingAttacker.tick` → deleted (session auto-advances). `Declaring` keeps cursor + in-progress weapon assignments locally; on commit, builds `CommitAttackImpulse(declarations)` and submits. No `commitAttackImpulse`, no `resolveAttacks`, no `enterDeclaring` direct state edits.
- `InitiativePhase.kt`, `HeatPhase.kt`, `EndPhase.kt` — deleted from TUI; session handles these autonomously. The TUI still needs to render *during* them, but renders just observe `session.viewFor(player).turn.phase`.
- `PhaseServices.kt` — drop `actionQueryService` (queries go through PlayerView) and `roller` (session owns it).
- `TurnPhaseExtensions.kt` — re-evaluate; likely deletable.

**Files — DEL:**
- `tui/.../game/UnitDeclaration.kt` — already moved in PR3
- `tui/.../game/ImpulseDeclarations.kt` — already moved
- `tui/.../game/ImpulseSequence.kt` — already moved
- `tui/.../game/TurnState.kt` — already moved
- `tui/.../game/AttackView.kt` — legality bits moved in PR4; if any free functions remain that aren't UI, move them too

**Test migration:**
- Domain phase tests (`MovementPhaseTest`, `AttackPhaseTest`, `TurnStateTest`, `AttackOrderTest`, `InitiativeTest` if it tests progression rather than just `rollInitiative`) move to `tactical/src/test/.../session/` as handler tests.
- TUI phase tests retain only the UI-workflow assertions (cursor moves correctly on key press, hover updates, draft assignments toggle).

**Steps:**
1. Phase-by-phase: extract any remaining domain logic into the corresponding handler from PR6 (if missed), then strip the TUI phase to UI-only.
2. Delete now-unused TUI files.
3. Migrate tests file-by-file; ensure both target test files (in tactical and tui) pass.
4. Run `./gradlew build` after each phase migration.

**Validation:** `./gradlew build` green; `grep -rn 'gameState.moveUnit\|resolveAttacks\|attackOrderFor\|rollInitiative' tui/` returns nothing.

**Commit:** `refactor(tactical): PR7 shrink TUI phases to pure UI workflow`

---

## PR 8 — Subscription channel + per-player event filtering

**Goal:** Add the cross-client notification seam. This is the "remote/web ready" PR: nothing in the TUI's behaviour changes, but the session now supports multiple subscribers receiving filtered events.

**Files — NEW:**
- `tactical/.../session/Subscription.kt` — handle returned from `subscribe`; `unsubscribe()` removes the listener
- `tactical/.../session/EventVisibility.kt` — function `filterFor(playerId: PlayerId, event: GameEvent): GameEvent?` — returns null to suppress, returns a (possibly redacted) event otherwise. Start permissive: all events visible to all players.
- Tests for subscription mechanics (subscribe/unsubscribe/multiple subscribers/filter)

**Files — MOD:**
- `BattleSession.kt`:
  - Add `private val listeners: MutableMap<PlayerId, MutableList<(GameEvent) -> Unit>>`
  - Add `public fun subscribe(playerId: PlayerId, listener: (GameEvent) -> Unit): Subscription`
  - In the event-emit path, push each event through `EventVisibility.filterFor(playerId, event)` and deliver to each listener.
  - `CommandResult.Accepted.events` continues to be the *full* event list to the submitter (or the submitter's filtered list — pick one and document; recommend submitter-filtered for consistency with subscribers).
- `tui/.../TuiApp.kt` (or wherever the loop lives) — subscribe both hot-seat players to a renderer that switches view per active player. This is mostly plumbing; the existing render code stays.

**Validation:** `./gradlew build`; new subscription tests pass. TUI behaviour unchanged.

**Commit:** `refactor(tactical): PR8 per-player subscription channel`

---

## Final state

After PR 8, the dependency graph is:

```
bt/    → strategic/, tactical/
tui/   → tactical/ (uses BattleSession, PlayerView, GameCommand, GameEvent)
        no direct GameState mutation; no TurnState ownership;
        no rule logic; no dice
tactical/
  model/      immutable data
  action/     rule predicates, action queries (still here; consumed by handlers)
  movement/   reachability calc (still here)
  dice/       DiceRoller (PR1)
  command/    GameCommand, CommandResult, CommandRejection (PR2+PR5)
  event/      GameEvent (PR5)
  session/    BattleSession, TurnState, Impulse*, PhaseHandler impls (PR3+PR5+PR6)
  view/       PlayerView, PublicGameState, query helpers (PR4)
strategic/   unchanged
```

A future `web/` or `remote-server/` module depends on `tactical/` exactly the
same way `tui/` does; the only new code is the network protocol that
serializes commands and events.
