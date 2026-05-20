tactical/
├── movement/
│   ├── (rules math: ReachabilityCalculator, MovementCost, MovementState, etc.)
│   ├── MoveActionDefinition, MovementContext, MovementPreview
│   ├── MovementPhaseHandler
│   ├── MoveUnit (GameCommand branch)
│   ├── UnitMoved (GameEvent branch)
│   └── MovementMode (moved from model/)
│
├── attack/
│   ├── (current action/attack/ contents)
│   ├── WeaponAttackPhaseHandler, PhysicalAttackPhaseHandler, ImpulseDeclarations
│   ├── CommitAttackImpulse (GameCommand branch)
│   ├── AttacksResolved, AttackDeclarationsRecorded, TorsoFacingsApplied (GameEvent branches)
│   ├── RuleRejection branches
│   ├── TargetInfo, WeaponTargetInfo
│   └── Weapon, Weapons, FiringArc, HitLocation, ArmorLayout, InternalStructureLayout, InternalStructureTables (moved from model/)
│
├── session/      ← write side
│   ├── BattleSession, PhaseHandler, PhaseOutcome
│   ├── InitiativePhaseHandler, HeatPhaseHandler, EndPhaseHandler
│   ├── ImpulseSequence, TurnState, UnitDeclaration, Subscription, EventVisibility
│   ├── GameCommand (root), GameEvent (root)
│   ├── CommandResult, RejectionReason, CommandRejection, RuleRejection (root)
│   └── TurnPhase, Impulse, Initiative (session-cycle primitives)
│
├── query/        ← read side
│   ├── PlayerView, DefaultPlayerView, PublicGameState
│   ├── ActionQueryService
│   ├── ActionPreview, AvailableAction, UnavailableAction, ActionOption, ActionId
│   ├── PhaseActionReport, RuleResult, Warning
│   └── (vertical-specific previews live in their verticals and implement ActionPreview from here)
│
├── model/        ← truly shared, leaf
│   ├── Hex, HexCoordinates, HexDirection
│   ├── GameMap, GameState, GameStateFactory, GameStateTransforms
│   ├── MechModel, MechModels, UnitFactory
│   ├── Terrain
│   └── PlayerId, UnitId, CombatUnit
│
└── dice/         ← unchanged, leaf


Inside attack/:
attack/
├── AttackContext.kt              ← shared scaffold
├── AttackDefinition.kt
├── AttackResolution.kt
├── AttackRule.kt
├── AttackDeclaration.kt          ← (currently inside action/attack/)
├── WeaponAttackPhaseHandler.kt
├── PhysicalAttackPhaseHandler.kt
├── ImpulseDeclarations.kt
├── CommitAttackImpulse.kt        ← GameCommand branch
├── AttacksResolved.kt, AttackDeclarationsRecorded.kt, TorsoFacingsApplied.kt
├── RuleRejection branches (NotAdjacent, NoAmmo, OutOfRange, NoLineOfSight, WeaponDestroyed)
├── TargetInfo.kt, WeaponTargetInfo.kt
├── Weapon.kt, Weapons.kt, FiringArc.kt, HitLocation.kt, ArmorLayout.kt, InternalStructureLayout.kt, InternalStructureTables.kt
│
├── weapon/
│   ├── FireWeaponActionDefinition.kt
│   ├── WeaponAttackPreview.kt
│   ├── HasAmmoRule.kt
│   ├── InRangeRule.kt
│   ├── LineOfSightRule.kt
│   ├── WeaponNotDestroyedRule.kt
│   └── HeatPenaltyRule.kt
│
└── physical/
├── PunchActionDefinition.kt
├── PhysicalAttackPreview.kt
└── AdjacentRule.kt


3. (b) is the sweet spot. Konsist is a Kotlin-native architecture testing library — you'd add maybe 30 lines of test code asserting:
    - attack/** doesn't import movement/** and vice versa
    - model/** doesn't import any vertical, session/**, or query/**
    - dice/** doesn't import anything in tactical except via DI seams
    - session/** is allowed to import verticals (it's the conductor)

That makes the structure self-documenting and self-checking, without the build-system overhead of submodules.

Required cleanup before (b) can pass:
- GameState.moveUnit(unitId, destination: ReachableHex) → extension function GameState.moveUnit(...) in movement/. GameState becomes a pure data holder.
- applyTorsoFacings, resetTorsoFacings → extension functions in attack/.
- applyHeatDissipation → extension function in session/ (or wherever the heat handler lives).
- GameStateTransforms.kt likely deletes; per-vertical files take over.
