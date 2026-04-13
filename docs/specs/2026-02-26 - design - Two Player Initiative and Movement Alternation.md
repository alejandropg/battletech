# Two Player Initiative and Movement Alternation

## Problem

The game currently has no concept of players. All units are in a single pool with no ownership, initiative auto-advances silently, and any unit can be selected at any time. This makes it a sandbox rather than a two-player game.

## Design

### Player Model (tactical module)

New `PlayerId` enum and a new `owner` field on `Unit`:

```kotlin
enum class PlayerId { PLAYER_1, PLAYER_2 }
```

```kotlin
data class Unit(
    val id: UnitId,
    val owner: PlayerId,   // new field
    val name: String,
    // ... rest unchanged
)
```

`GameState` gets a helper:

```kotlin
fun unitsOf(player: PlayerId): List<Unit> = units.filter { it.owner == player }
```

### Initiative Resolution (tactical module)

A pure function that takes two dice rolls and determines the loser/winner:

```kotlin
data class InitiativeResult(
    val rolls: Map<PlayerId, Int>,   // each player's 2d6 total
    val loser: PlayerId,             // moves first
    val winner: PlayerId,            // moves second (strategic advantage)
)
```

The rolling:

```kotlin
fun rollInitiative(random: Random = Random): InitiativeResult {
    while (true) {
        val roll1 = random.nextInt(1, 7) + random.nextInt(1, 7)
        val roll2 = random.nextInt(1, 7) + random.nextInt(1, 7)
        if (roll1 != roll2) {
            val (loser, winner) = if (roll1 < roll2) {
                PlayerId.PLAYER_1 to PlayerId.PLAYER_2
            } else {
                PlayerId.PLAYER_2 to PlayerId.PLAYER_1
            }
            return InitiativeResult(
                rolls = mapOf(PlayerId.PLAYER_1 to roll1, PlayerId.PLAYER_2 to roll2),
                loser = loser,
                winner = winner,
            )
        }
    }
}
```

`Random` is injected so tests can use a seeded instance for deterministic results.

The TUI initiative phase shows the dice rolls with a flash message:
`"Initiative: P1 rolled 7, P2 rolled 10 — P1 moves first"`

### Movement Alternation Algorithm (tactical module)

Core scheduling logic. Given unit counts per player, produce the sequence of impulses.

```kotlin
data class MovementImpulse(
    val player: PlayerId,
    val unitCount: Int,
)

fun calculateMovementOrder(
    loser: PlayerId,
    loserUnitCount: Int,
    winner: PlayerId,
    winnerUnitCount: Int,
): List<MovementImpulse>
```

**Algorithm:** The number of rounds equals the smaller side's unit count. The larger side distributes its units across those rounds as evenly as possible, front-loading the bigger groups (revealing plans early = disadvantage).

Example — loser has 3 units, winner has 5:

| Round | Loser moves | Winner moves |
|-------|-------------|--------------|
| 1     | 1           | 2            |
| 2     | 1           | 2            |
| 3     | 1           | 1            |

Result: `[Loser:1, Winner:2, Loser:1, Winner:2, Loser:1, Winner:1]`

The distribution for the larger side (5 across 3 rounds): `5 / 3 = 1` base with `5 % 3 = 2` rounds getting +1. The extra rounds come first.

**Edge cases:**

- Equal unit counts (e.g. 4 vs 4): straight alternation, 1 each, loser first.
- One player has 0 unmoved units: the other player moves all remaining units in one block.

### Turn State Tracking (tui module)

New `TurnState` to track progress within a turn, added to `AppState`:

```kotlin
data class TurnState(
    val initiativeResult: InitiativeResult,
    val movementOrder: List<MovementImpulse>,
    val currentImpulseIndex: Int,
    val movedUnitIds: Set<UnitId>,
    val unitsMovedInCurrentImpulse: Int,
) {
    val currentImpulse: MovementImpulse get() = movementOrder[currentImpulseIndex]
    val activePlayer: PlayerId get() = currentImpulse.player
    val remainingInImpulse: Int get() = currentImpulse.unitCount - unitsMovedInCurrentImpulse
    val allImpulsesComplete: Boolean get() = currentImpulseIndex >= movementOrder.size
}
```

```kotlin
data class AppState(
    val gameState: GameState,
    val currentPhase: TurnPhase,
    val cursor: HexCoordinates,
    val phaseState: PhaseState,
    val turnState: TurnState,       // new field
)
```

**Flow after a unit completes movement:**

1. Add `unitId` to `movedUnitIds`
2. Increment `unitsMovedInCurrentImpulse`
3. If `remainingInImpulse == 0` → advance `currentImpulseIndex`, reset `unitsMovedInCurrentImpulse`
4. If `allImpulsesComplete` → movement phase done, advance to next `TurnPhase`

**`PhaseState.Idle` prompt** is derived from `TurnState` when entering Idle (e.g. `"Player 1: select a unit to move (2 remaining)"`). No need to duplicate fields in `PhaseState.Idle`.

**New turn start:** initiative phase rolls dice → builds `TurnState` with `movementOrder` calculated from each player's unit counts → movement phase begins.

### Enforcement & UI Changes (tui module)

**Unit selection enforcement — only active player's unmoved units are selectable:**

**CycleUnit (Tab):** filter to `activePlayer`'s units not in `movedUnitIds`:

```kotlin
val selectableUnits = appState.gameState.unitsOf(turnState.activePlayer)
    .filter { it.id !in turnState.movedUnitIds }
```

**Confirm on a unit:** reject if the unit's `owner != activePlayer` or `id in movedUnitIds`. Show a flash message like `"Not your unit"` or `"Already moved"`.

**ClickHex:** same validation — only enter movement if the clicked hex has a selectable unit.

**Status bar:** show whose turn it is and how many units remain:
`"Player 1's turn — move 2 of 3 units"`

**Initiative phase display:** no longer a silent auto-advance. Show the dice rolls with a flash message and a brief pause:
`"Initiative: P1 rolled 7, P2 rolled 10 — P1 moves first"`

**Unit colors:** Player 1 and Player 2 units are visually distinct on the board (e.g. blue vs red). `HexRenderer`/`UnitRenderer` uses `unit.owner` to pick the color.

## Testing

### tactical module

- **`rollInitiative`** — with seeded `Random`: returns correct loser/winner based on rolls, re-rolls on ties
- **`calculateMovementOrder`** — key cases:
  - Equal counts (4 vs 4): straight alternation, 1 each
  - Unequal (3 vs 5): correct distribution `[1,2,1,2,1,1]`, front-loaded
  - One side has 1 unit: that side moves 1 once, other side moves all in one block
  - Edge case: one side has 0 units

### tui module

- **Turn state transitions** — after a unit moves: `movedUnitIds` grows, impulse advances when full, phase completes when all impulses done
- **Unit selection enforcement** — only active player's unmoved units are selectable; confirm on opponent's unit or already-moved unit is rejected
- **CycleUnit filtering** — Tab only cycles through selectable units
- **Initiative phase** — produces `TurnState` with correct `movementOrder`
