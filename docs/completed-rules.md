# Completed Rules — Implemented in the Engine

This document catalogues the BattleTech rules that **are already implemented and tested** in the engine, as a companion to [`missing-rules.md`](./missing-rules.md) (which lists what is still needed for a complete standard game). Scope is the same: standard-level **BattleMech-vs-BattleMech** tactical combat.

Each section is a rule area: a description of what the rule does (with the actual numbers as coded) and an **Implemented in** pointer to the concrete file(s)/class(es). Where the implementation is a deliberate **simplification** of the full rule, it is flagged so this document does not overstate completeness — the gaps are detailed in `missing-rules.md`.

All paths are relative to `tactical/src/main/kotlin/battletech/tactical/` unless noted. The areas below are covered by an extensive test suite (≈50 tactical test files plus ~35 TUI tests).

## Hex Board & Coordinate Geometry

The battlefield is a hex grid using an **odd-q offset** coordinate system (column/row). The model computes exact **hex distance** (via cube-coordinate conversion), traces the **line of hexes** between two points (for line of sight), and resolves each hex's six **neighbours**. The six facings (N, NE, SE, S, SW, NW) support clockwise/counter-clockwise rotation and a turn-cost calculation between any two facings.

**Implemented in:** `model/HexCoordinates.kt` (`distanceTo`, `lineTo`, `neighbor`/`neighbors`), `model/HexDirection.kt` (`rotateClockwise`/`rotateCounterClockwise`, `turnCostTo`), `model/GameMap.kt`, `model/Hex.kt`.

## Terrain & Elevation

Each hex carries a terrain type, an integer elevation level, and a water depth. Terrain types are **Clear, Light Woods, Heavy Woods, and Water**, each with a movement-point cost (Clear 1, Light Woods 2, Heavy Woods 3, Water 2). Climbing to a higher elevation adds the level difference to the entry cost; descending is free of an elevation surcharge.

**Implemented in:** `model/Terrain.kt`, `model/Hex.kt` (terrain, elevation, water depth), `movement/MovementCost.kt`. *Note:* Rough and Building terrain are not modeled, and elevation is not yet used for line-of-sight blocking (see `missing-rules.md`).

## Facing & Turning

A 'Mech tracks both a **leg facing** and an independent **torso facing** (for torso twist). Changing facing during ground movement costs **1 MP per hexside turned**, integrated into pathfinding. At the End Phase, torso facings are reset back to the leg facing.

**Implemented in:** `unit/CombatUnit.kt` (`facing`, `torsoFacing`), `model/HexDirection.kt` (`turnCostTo`), `movement/ReachabilityCalculator.kt` (turn cost in path search), `session/EndPhaseHandler.kt` (torso reset).

## Movement — Walk, Run, Jump

Units have separate Walking, Running, and Jumping MP. **Ground movement** (walk/run) uses a facing-aware **Dijkstra reachability search** that accounts for terrain cost, elevation cost, and per-hexside turn cost, producing every reachable destination hex with its path and MP spent. **Jump movement** reaches any hex within jump MP regardless of intervening facing/turn cost. Current heat reduces effective movement (see *Heat*). Movement this turn (mode + hexes moved) is recorded for later to-hit modifiers.

**Implemented in:** `movement/ReachabilityCalculator.kt`, `movement/MovementCost.kt`, `model/MovementMode.kt`, `movement/MovementPhaseHandler.kt`, `movement/ReachableHex.kt`, `unit/MovementThisTurn.kt`.

## Stacking

Units occupy hexes, and movement is blocked from entering a hex already occupied by another unit. Occupancy is queried from game state during reachability and target selection.

**Implemented in:** `model/GameState.kt` (`unitAt`), `movement/ReachabilityCalculator.kt`.

## Standing Up & Piloting Skill Rolls

A prone 'Mech can attempt to **stand up**, which requires a **Piloting Skill Roll** (2d6 versus piloting skill + modifier). The PSR mechanic is a reusable helper used by stand-up attempts, kick knockdowns, and fall avoidance. Prone units cannot otherwise move or make attacks.

**Implemented in:** `unit/PilotingSkillRoll.kt` (`pilotingSkillRoll(unit, roller, modifier)`), `movement/MovementPhaseHandler.kt` (`StandUp` command), `unit/CombatUnit.kt` (`isProne`).

## Initiative & Turn Sequence

Each turn opens with **Initiative**: each side rolls 2d6, re-rolling ties; the **loser acts first** so the winner reacts. The turn then runs through a fixed phase sequence — **Initiative → Movement → Weapon Attack → Physical Attack → Heat → End** — driven by an auto-cascading state machine: after a command is applied, the session advances through every phase whose work is complete (firing each phase's entry logic) and stops at the next phase that needs player input. Movement and attack phases are **impulse-based**, alternating between players (loser first). The End Phase increments the turn number.

**Implemented in:** `session/InitiativePhaseHandler.kt`, `session/Initiative.kt`, `model/TurnPhase.kt`, `session/BattleSession.kt` (`standardHandlers()`, `advance`, cascade), `session/EndPhaseHandler.kt`, `session/TurnState.kt`.

## Firing Arcs & Torso Twist

A unit's weapons fire into a **60° forward arc** — the three hexsides centered on its torso facing. Torso twist lets the torso facing differ from the leg facing, shifting the arc. The model computes which hexes fall in the forward arc and the bearing (which hexside) of a target relative to the attacker.

**Implemented in:** `attack/FiringArc.kt` (`forwardArc`, `bearingDirection`), surfaced through `session/PlayerView.kt` (`fireArc`, `validTargets`).

## Weapon To-Hit Resolution

Weapon attacks resolve on **2d6 versus a target number**. The target number is the attacker's **gunnery skill** plus modifiers: **range bracket** (Short +0, Medium +2, Long +4, beyond Long = out of range), the attacker's **heat to-hit penalty** (see *Heat*), a **secondary-target penalty** (+1 for any target beyond the primary), a **prone-target modifier** (−2 if adjacent, +1 if at range), and an **immobile-target modifier** (−4 versus a shut-down target). All attacks declared in a phase resolve **simultaneously** — every roll is made against the original pre-damage state, then damage is applied.

**Implemented in:** `attack/AttackResolution.kt` (`resolveAttacks`, `resolveOneAttack`), `attack/ProneModifiers.kt`, `attack/weapon/WeaponAttackPhaseHandler.kt`. *Note:* target-movement, attacker-movement, minimum-range, and terrain to-hit modifiers for **weapons** are not yet applied (see `missing-rules.md`).

## Line of Sight, Range & Weapon Legality

Each weapon has minimum/short/medium/long ranges, and range to the target is compared against them to pick the bracket. A set of legality rules gate each shot: the weapon must be **in range**, **not destroyed**, **have ammo** (if ammo-using), and have **line of sight** to the target. A heat-penalty rule surfaces the overheating modifier as a warning.

**Implemented in:** `attack/weapon/InRangeRule.kt`, `WeaponNotDestroyedRule.kt`, `HasAmmoRule.kt`, `LineOfSightRule.kt`, `HeatPenaltyRule.kt`, `FireWeaponActionDefinition.kt`. *Note:* LOS is **simplified** — it blocks only when the **target's own hex** is heavy woods; intervening terrain, woods accumulation, and elevation are not yet considered (see `missing-rules.md`).

## Hit Location & Front/Rear Damage

On a hit, the location is rolled on the standard **2d6 hit-location table** (e.g. 2 = Center Torso, 12 = Head, 3–4 = Right Arm, 10–11 = Left Arm, etc.). Damage can be directed to **rear armor** on the torsos based on the attack's direction (front vs. rear).

**Implemented in:** `attack/HitLocation.kt` (`HitLocation` enum + `HitLocationTable.roll`), `attack/AttackResolution.kt` (front/rear armor selection).

## Armor & Internal Structure Damage

Every 'Mech tracks **per-location armor** (front for all eight locations, plus separate rear armor for the three torsos) and **per-location internal structure**. Damage depletes armor first; any **overflow past armor penetrates into internal structure** at the same location. Internal-structure values are derived from tonnage via the standard structure tables.

**Implemented in:** `attack/AttackResolution.kt` (`applyDamage`), `unit/ArmorLayout.kt`, `unit/InternalStructureLayout.kt`, `unit/InternalStructureTables.kt`. *Note:* damage that exceeds a location's internal structure is currently **discarded** — there is no inward transfer, location destruction, or critical-hit roll yet (see `missing-rules.md`).

## Physical Attacks — Punch & Kick

Adjacent 'Mechs can **punch** (per arm) or **kick** (per leg). The to-hit target number is the attacker's piloting skill plus the **attacker-movement modifier** (walk +1, run +2, jump +3), the **target-movement modifier** (by hexes moved: 0–2 +0, 3–4 +1, 5–6 +2, 7–9 +3, 10–17 +4, 18–24 +5, 25+ +6, plus +1 if the target jumped), the **terrain modifier** (light woods +1, heavy woods +2), the prone and heat modifiers, and the **attack-kind modifier** (kick −2, i.e. easier). Damage is **⌈tonnage/10⌉ for a punch** and **⌈tonnage/5⌉ for a kick**, applied to a location from punch/kick-specific direction tables. Per-turn limits are enforced: a unit cannot both punch and kick, cannot reuse the same limb, cannot punch after jumping, cannot kick after running or jumping, and a prone unit cannot make physical attacks.

**Implemented in:** `attack/physical/PhysicalToHit.kt`, `PhysicalDamage.kt`, `PunchLocationTable.kt`, `KickLocationTable.kt`, `PunchActionDefinition.kt`/`KickActionDefinition.kt`, `PhysicalAttackLimits.kt`, `PhysicalAttackPhaseHandler.kt`.

## Knockdown & Falling

A **kick** triggers a knockdown check: on a hit the **target** rolls a PSR to stay standing; on a miss the **attacker** rolls one. A failed PSR makes the unit **fall** — it takes **⌈tonnage/10⌉** fall damage to a location rolled on the hit-location table, its facing is randomized by a 1d6 roll, and it ends up prone. The `fall` routine is reusable by any future fall trigger.

**Implemented in:** `attack/Falling.kt` (`fall`, `FallResult`), `attack/physical/PhysicalAttackPhaseHandler.kt` (knockdown wiring), `unit/PilotingSkillRoll.kt`.

## Heat — Generation, Dissipation, Scale, Shutdown & Ammo Explosion

The full heat system is implemented and documented separately in [`docs/rules/heat.md`](./rules/heat.md). **Generation:** movement adds heat (walk +1, run +2, jump = hexes moved, minimum 3) and each fired weapon adds its heat value, tracked as labeled sources. **Dissipation:** heat sinks remove heat each Heat Phase (single 1/sink, double 2/sink). **Scale (absolute heat):** movement penalty −1 MP per 5 heat (capped −5); to-hit penalty +1 at 8, +2 at 13, +3 at 18, +4 at 24; **shutdown** avoidance rolls at 14/17/22/26 (target 4/6/8/10) with **automatic shutdown at 30**; shut-down units roll to restart as heat falls; **ammo explosion** avoidance rolls at 15/19/23/28 (target 4/6/8/10), and a failed roll cooks off the highest-ammo weapon into the center torso.

**Implemented in:** `heat/HeatGeneration.kt`, `heat/HeatScale.kt`, `unit/HeatSink.kt`, `session/HeatPhaseHandler.kt`, `session/GameStateHeatTransform.kt`.

## BattleMech Unit Model & Catalog

A `CombatUnit` aggregates everything needed for tactical play: id/owner/name, tonnage, gunnery and piloting skills, weapons, position/facing/torso facing, walk/run/jump MP, current heat, heat sink, per-location armor and internal structure, movement-this-turn, accumulated heat sources, and prone/shutdown flags. A catalog of **12 canonical chassis** (Locust, Stinger, Wasp, Phoenix Hawk, Hunchback, Wolverine, Griffin, Shadow Hawk, Warhammer, Archer, Marauder, Atlas) is defined with their tonnage, movement, armor, structure, and weapon loadouts, alongside a library of **11 weapon types** (lasers, autocannons, SRM/LRM launchers, PPC, machine gun) with damage, heat, range, and ammo data.

**Implemented in:** `unit/CombatUnit.kt`, `unit/ArmorLayout.kt`, `unit/InternalStructureLayout.kt`, `unit/InternalStructureTables.kt`, `unit/Weapon.kt`, `unit/Weapons.kt`, `unit/MechModels.kt`. *Note:* weapons are a flat list with no per-location mounting or critical slots yet (see `missing-rules.md`).

## Session, Commands, Events & Subscription

The engine is **server-authoritative**: one `BattleSession` owns the `GameState`, and all state changes flow through `submitCommand(GameCommand)`. The coarse commands are `MoveUnit`, `StandUp`, `CommitAttackImpulse`, and `CommitPhysicalAttackImpulse`. Outcomes are reported as immutable **events** (`UnitMoved`, `AttacksResolved`, `PhysicalAttacksResolved`, `UnitFell`, `UnitStoodUp`, `InitiativeRolled`, `HeatDissipated`, `UnitShutdown`, `UnitRestarted`, `AmmoExploded`, `PhaseChanged`, `TurnEnded`, …). Clients **subscribe** per player to receive events, which pass through a visibility filter (the hook for hidden-information redaction). A rich **PlayerView** answers "what is legal right now?" — legal movements, firing arcs, valid targets with to-hit breakdowns, and physical-attack options.

**Implemented in:** `session/BattleSession.kt`, `session/GameCommand.kt`, `session/GameEvent.kt`, `session/PlayerView.kt`, `session/Subscription.kt`, `session/EventVisibility.kt`, `session/GameLog.kt`, `model/GameState.kt`.

## Deterministic Dice

All randomness goes through a single seedable `DiceRoller` (2d6, 1d6), so seeded tests replay production roll order exactly. No raw `Random` is used anywhere in the rules.

**Implemented in:** `dice/DiceRoller.kt`, `dice/DiceRoll.kt`.

## Playable TUI Surface

A terminal UI (Mordant) wires the engine end-to-end for two players: selecting units and committing **moves** (with reachability highlighting), declaring **weapon attacks** (target selection within arc, per-weapon assignment, torso twist), and declaring **physical attacks** (punch/kick). It renders the board, unit/target status with penalties, declared targets, attack results, the game log, and a heat bar.

**Implemented in:** `tui/src/main/kotlin/battletech/tui/` (entry point `tui/.../Main.kt`, game phase controllers and views under `tui/.../game/` and `tui/.../view/`).
