# Weapon Attack Declaration and Resolution

## Overview

Weapon attack phase where players alternately declare attacks for their units, then all attacks resolve simultaneously. Includes torso twist mechanics, firing arc visualization, a target/weapon assignment panel, and full damage resolution with hit location tables.

## Attack Phase Flow

### Alternation Model

- Initiative **loser** declares first (same as movement)
- Uses the same impulse distribution algorithm as movement (`calculateMovementOrder`)
- Each impulse, the active player selects one unit and declares all its attacks
- Tab cycles through the active player's undeclared units; clicking an opponent's or already-declared unit shows a flash message
- Phase ends when all impulses are complete (all units declared or skipped)

### Declaration Flow (per unit)

1. **Unit selected** → enter `Browsing` state: firing arc shown on board, valid targets highlighted in red
2. **Left/right arrows** → twist torso (±1 hex-side from leg facing), arc and targets recalculate
3. **Confirm on enemy hex** → enemy becomes **primary target** → enter `AssigningWeapons`
4. **In AssigningWeapons**: targets panel appears. Player navigates targets, toggles weapons on/off per target. First confirmed target is primary; additional targets are secondary (+1 to-hit penalty)
5. **Confirm** → declaration recorded, unit done, advance to next impulse
6. **Escape** → back to `Browsing` (or cancel unit selection if in `Browsing`)
7. **Skip**: if no valid targets exist, confirm to skip the unit's attack

### Resolution

After all units declare, all attacks resolve simultaneously — no attack result affects another in the same phase.

## Torso Twist

### Rules

- Torso can twist **one hex-side** clockwise or counterclockwise from leg facing (3 possible positions: center, left, right)
- Torso twist is ephemeral — only exists during the attack phase, not persisted on `CombatUnit`
- Tracked in `PhaseState.Attack` as a `HexDirection` value

### Rendering

When torso facing differs from leg facing, render a torso arrow using Nerd Font icons:

| Direction | Unicode  |
|-----------|----------|
| N         | U+F005D  |
| NE        | U+F005C  |
| SE        | U+F0043  |
| S         | U+F0045  |
| SW        | U+F0042  |
| NW        | U+F005B  |

Arrow placement is **twist-relative**: clockwise twist places the arrow to the right of the existing facing icon; counterclockwise places it to the left.

Examples (unit "W" facing North):

Torso twisted NE (clockwise):
```
  _____
 /     \
/  󰧃󰁝   \
\   W   /
 \_____/
```

Torso twisted NW (counterclockwise):
```
  _____
 /     \
/  󰧃    \
\  󰁂W   /
 \_____/
```

## Firing Arc

### Calculation

- **Forward arc** = the 3 contiguous hex-sides in front of the torso facing direction
- `FiringArc.forwardArc(position, torsoFacing, gameMap): Set<HexCoordinates>` returns all map hexes within the arc
- Arc membership is a bearing calculation: for each candidate hex, compute which hex-side of the origin the bearing falls into, check if it's one of the 3 forward hex-sides
- Rear arc is out of scope for this iteration

### Board Visualization

- **Arc hexes**: render `.` in the hex center (using existing `ATTACK_RANGE` highlight)
- **Valid targets**: enemy units in the arc with at least one eligible weapon → render unit letter in **red**, no `.`
- **Non-targetable units**: enemies in arc with no eligible weapons, or friendly units → render normally
- **Torso twist**: left/right arrow recalculates arc, board re-renders immediately

## Attack Targets Panel

### Layout

Shown to the **left** of the existing unit status sidebar. Only visible during the attack phase when the selected unit has at least one valid target.

```
┌─────────────────────┐ ┌──────────────┐ ┌──────────────┐
│                     │ │ TARGETS      │ │ UNIT STATUS  │
│                     │ │              │ │              │
│      Board          │ │ ▶ Atlas [P]  │ │  Atlas AS7-D │
│                     │ │  AC/20  58%  │ │  Weapons:    │
│                     │ │  ML    72%   │ │  ...         │
│                     │ │  ML    72%   │ │  Armor:      │
│                     │ │ [heat +1]    │ │  ...         │
│                     │ │              │ │              │
│                     │ │  Hunch [S]   │ │              │
│                     │ │  LRM15 45%   │ │              │
│                     │ │ [+1 second]  │ │              │
│                     │ │              │ │              │
└─────────────────────┘ └──────────────┘ └──────────────┘
```

### Panel Contents (per target)

- Target name + `[P]` for primary or `[S]` for secondary
- Each eligible weapon: name, success %, toggle state (selected/unselected)
- Modifier annotations: heat penalty, secondary target +1, range band
- Currently focused target highlighted with `▶`

### Interaction

- **Up/down arrows** or number keys: navigate between targets
- **Enter/Space**: toggle a weapon on/off for the focused target
- **Tab**: switch focus between targets
- **Escape**: back to Browsing
- **Confirm**: finalize this unit's declaration

### Panel Width

Fixed width (20-22 chars), compresses the board viewport when visible.

## Damage Resolution

### Pipeline (per declared weapon attack)

1. **To-hit roll**: 2d6 ≥ target number (gunnery skill + range modifier + heat penalty + secondary target +1 if applicable). Hit if roll meets or exceeds target.
2. **Hit location roll**: 2d6 on the standard hit location table (see below)
3. **Apply damage**: reduce armor at hit location. Overflow goes to internal structure. Internal structure reaching 0 destroys the location.

### Hit Location Table

| 2d6 | Location |
|-----|----------|
| 2   | Center Torso (critical) |
| 3   | Right Arm |
| 4   | Right Arm |
| 5   | Right Leg |
| 6   | Right Torso |
| 7   | Center Torso |
| 8   | Left Torso |
| 9   | Left Leg |
| 10  | Left Arm |
| 11  | Left Arm |
| 12  | Head |

### Model Changes

- Add `InternalStructureLayout` to `CombatUnit` (per-location HP values, mirrors `ArmorLayout`)
- `applyDamage(location, amount)` handles armor → internal structure overflow
- `AttackDeclaration`: attacker, target, weapon, isPrimary
- `AttackResult`: hit/miss, location, damage applied
- `resolveAttacks(declarations, random): List<AttackResult>`

### Out of Scope

- Critical hits (engine, ammo explosion, etc.)
- Transfer damage (destroyed side torso → center torso)
- Pilot hits
- Mech destruction / falling

## PhaseState Changes

Expand `PhaseState.Attack` from a flat state to a sealed hierarchy:

```kotlin
PhaseState.Attack
  ├── Browsing(unitId, torsoFacing, arc, targets, prompt)
  ├── AssigningWeapons(unitId, torsoFacing, arc, targets,
  │     selectedTargetIndex, weaponAssignments, primaryTargetId, prompt)
  └── Review(unitId, torsoFacing, weaponAssignments, primaryTargetId, prompt)
```

- `Browsing`: unit selected, viewing firing arc, no target chosen yet
- `AssigningWeapons`: primary target selected, toggling weapons per target
- `Review`: optional confirmation state before finalizing

## TurnState Changes

Add attack impulse tracking (parallel to movement):

- `attackOrder: List<MovementImpulse>` — computed at weapon attack phase start
- `attackedUnitIds: Set<UnitId>` — units that have declared
- `currentAttackImpulseIndex: Int` — current impulse position
- `unitsAttackedInCurrentImpulse: Int` — progress within current impulse

## File Changes

### Tactical Module — New Files

| File | Purpose |
|------|---------|
| `FiringArc.kt` | Arc calculation: `forwardArc(position, torsoFacing, gameMap)` and bearing math |
| `HitLocationTable.kt` | 2d6 → body location lookup |
| `AttackResolution.kt` | `AttackDeclaration`, `AttackResult`, `resolveAttacks()` |
| `InternalStructure.kt` | Per-location internal structure values |

### Tactical Module — Modified Files

| File | Change |
|------|--------|
| `CombatUnit.kt` | Add `internalStructure: InternalStructureLayout` field |
| `FireWeaponActionDefinition.kt` | Add secondary target +1 modifier support |
| `ActionQueryService.kt` | Arc-aware target filtering |

### TUI Module — New Files

| File | Purpose |
|------|---------|
| `TargetsView.kt` | Attack targets panel rendering |

### TUI Module — Modified Files

| File | Change |
|------|--------|
| `PhaseState.kt` | Expand `Attack` into `Browsing` / `AssigningWeapons` / `Review` hierarchy |
| `AttackController.kt` | Full implementation: torso twist, target selection, weapon toggling, declarations |
| `RenderData.kt` | Add `extractRenderData` cases for attack states (arc highlights, red targets) |
| `UnitRenderer.kt` | Torso twist arrow rendering (Nerd Font icons, twist-relative positioning) |
| `BoardView.kt` | Handle `ATTACK_RANGE` highlight (`.` in hex center), red unit rendering |
| `AppState.kt` | Attack impulse tracking in `handlePhaseOutcome` |
| `TurnState.kt` | Add attack impulse fields |
| `Main.kt` | Wire new panel and attack flow into the game loop |

### Test Files (New)

| File | Coverage |
|------|----------|
| `FiringArcTest.kt` | Arc membership for various positions and torso facings |
| `HitLocationTableTest.kt` | All 11 2d6 results map correctly |
| `AttackResolutionTest.kt` | Hit/miss, damage application, armor → internal overflow |
| `TargetsViewTest.kt` | Panel rendering |
| `AttackControllerTest.kt` | Expand existing tests for new state transitions |
