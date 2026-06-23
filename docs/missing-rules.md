# Missing Rules — Standard BattleMech Game

This document catalogues the BattleTech rules that are **not yet implemented** and that are required (or strongly expected) before the engine can play a complete standard game of *BattleMechs on a board*, from first shot to a decided winner. Scope is **standard-level BattleMech-vs-BattleMech** combat only — no infantry, vehicles, aerospace, or campaign/strategic layer, and no advanced/optional rules (charging, Death From Above, called shots, etc. are deliberately out of scope here).

Each feature below is a top-level section: a rules summary (with the actual BattleTech numbers so it can be implemented without a rulebook on hand), the **current state** in the codebase, and a concrete **implementation task list** referencing the files/classes to extend.

## Already implemented (context — do **not** re-implement)

For reference, the engine already has: the hex board, terrain and elevation movement costs, walk/run/jump movement with Dijkstra reachability and facing/turn cost, initiative and the full phase cascade (`Initiative → Movement → Weapon → Physical → Heat → End`), weapon to-hit (gunnery + range bracket + heat + secondary + prone/immobile modifiers), punch/kick physical attacks with knockdown PSR and falling, firing arcs / torso twist, armor + internal-structure damage with armor→IS overflow **within a single location**, the **complete heat system** (generation, dissipation, shutdown/restart, ammo explosion), and a working TUI for movement and both attack phases.

Key code anchors referenced repeatedly below:

- `tactical/src/main/kotlin/battletech/tactical/unit/CombatUnit.kt` — the unit aggregate.
- `tactical/src/main/kotlin/battletech/tactical/attack/AttackResolution.kt` — weapon to-hit + `applyDamage`.
- `tactical/src/main/kotlin/battletech/tactical/attack/HitLocation.kt` — `HitLocation` enum + `HitLocationTable`.
- `tactical/src/main/kotlin/battletech/tactical/unit/Weapon.kt` — `Weapon` data class.
- `tactical/src/main/kotlin/battletech/tactical/attack/Falling.kt` — reusable `fall(unit, roller)`.
- `tactical/src/main/kotlin/battletech/tactical/unit/PilotingSkillRoll.kt` — reusable `pilotingSkillRoll(unit, roller, modifier)`.
- `tactical/src/main/kotlin/battletech/tactical/session/GameEvent.kt` — event hierarchy.
- `tactical/src/main/kotlin/battletech/tactical/session/BattleSession.kt` — `standardHandlers()` + cascade.

---

## Damage Transfer Between Locations

When a hit location's internal structure is reduced to 0, that location is destroyed and **excess damage transfers inward** to the next location toward the center — it is not lost. Transfer order: arm → adjacent side torso → center torso; leg → adjacent side torso → center torso; side torso → center torso. Head and center torso do not transfer (overflow there destroys the unit). This is fundamental — without it, a 20-point AC/20 hit to an arm with 4 armor + 6 IS simply wastes 10 damage instead of tearing into the torso.

**Current state.** `applyDamage` (`AttackResolution.kt:64`) clamps internal-structure overflow with `coerceAtLeast(0)` — damage beyond a location's IS is silently discarded, and the location is never marked destroyed.

**Implementation tasks.**
- Add a transfer map keyed by `HitLocation`: `LEFT_ARM → LEFT_TORSO → CENTER_TORSO`, `RIGHT_ARM → RIGHT_TORSO → CENTER_TORSO`, `LEFT_LEG → LEFT_TORSO → CENTER_TORSO`, `RIGHT_LEG → RIGHT_TORSO → CENTER_TORSO`, `LEFT_TORSO/RIGHT_TORSO → CENTER_TORSO`, `HEAD`/`CENTER_TORSO → none`.
- Rework `applyDamage` in `AttackResolution.kt` to be recursive/iterative: subtract from armor, then IS; if IS would drop below 0, set it to 0, mark the location destroyed, and recurse the remaining damage into the next transfer location (transferred damage applies to the **next location's armor first**, then its IS).
- Transferred damage uses the transfer target's *normal* (front) armor regardless of the original `useRearArmor`.
- Track destroyed locations on the unit (see *Location Destruction Consequences*); a hit rolled against an already-destroyed location transfers immediately.
- Tests: arm overflow into side torso into center torso; leg overflow; ensure head/CT overflow flags unit destruction rather than transferring.

## Mech Destruction & Unit Elimination

A BattleMech is destroyed (removed from play) when any of these occurs: **head** internal structure destroyed, **center torso** internal structure destroyed, **both legs** destroyed, the **engine** takes 3 critical hits, the **gyro** is destroyed and the mech cannot stand, an **ammo explosion** breaches the center torso, or the **pilot** dies (see *Pilot Hits & Consciousness*). Today nothing ever leaves play, so a game can never be won.

**Current state.** `CombatUnit` (`CombatUnit.kt:7`) has no `isDestroyed`/elimination state. There is no destruction check after damage, no `UnitDestroyed` event, and the cascade never ends.

**Implementation tasks.**
- Add an `isDestroyed: Boolean` (and likely a `destructionReason`) to `CombatUnit`, defaulting false.
- After every damage application (weapon resolution in `AttackResolution.kt`, fall damage in `Falling.kt`, ammo explosion in `HeatPhaseHandler`, criticals), evaluate destruction conditions and set `isDestroyed`.
- Add a `UnitDestroyed(unitId, reason)` event to `GameEvent.kt`; emit it from wherever destruction is detected.
- Exclude destroyed units from all impulse/target queries: movement order, `PlayerView.validTargets`, physical attack options, heat phase. (Mirror the existing `isShutdown` exclusions.)
- Decide whether a destroyed unit's hex becomes a wreck (blocks/are passable) — for standard play, leave the wreck in the hex as terrain-neutral but non-targetable; document the choice.
- Tests: head IS → 0 destroys; CT IS → 0 destroys; both legs destroyed destroys; destroyed units drop out of `activeUnitsOf`.

## Victory / End-of-Game Conditions

The standard game ends when only one side has surviving (non-destroyed, non-fled) units, or by a scenario objective. The session must detect this, emit a terminal event, and stop cascading phases.

**Current state.** The phase cascade in `BattleSession.kt` loops forever (`advanceIndex` wraps modulo `handlers.size`); there is no game-over concept.

**Implementation tasks.**
- After the End Phase (or after each destruction), check whether ≤1 player still owns active units via `GameState.activeUnitsOf`.
- Add a `GameOver(winner: PlayerId?)` event (null winner = mutual destruction/draw) to `GameEvent.kt`.
- Add a terminal state to the session (e.g. `isOver`/`winner`) so `submitCommand` rejects further commands once over, and the cascade halts instead of re-entering Initiative.
- TUI: surface the game-over banner; stop prompting for input.
- Tests: eliminate all of one player's units → `GameOver` with the surviving player; mutual elimination in the same resolution → draw.

## [x] Per-Location Weapon Mounting

In BattleTech every weapon occupies critical slots in a **specific location** (e.g. an AC/20 in the right torso, a medium laser in the left arm). Location matters for: which weapons are lost when a limb/torso is destroyed, critical-hit weapon destruction, and firing-arc nuances. This is a prerequisite for *Critical Hits*, *Location Destruction Consequences*, and correct *Ammo* handling.

**Current state.** `CombatUnit.weapons` is a flat `List<Weapon>` with no location; `Weapon` (`Weapon.kt`) has no mount location. `MechModels.kt` builds loadouts without placement.

**Implementation tasks.**
- Add `location: HitLocation` (or a richer `MechLocation` that also covers arms/legs/head/torsos consistently with `HitLocation`) to `Weapon`, or introduce a `WeaponMount(weapon, location, slotIndices)` wrapper.
- Update the hardcoded designs in `unit/MechModels.kt` / `unit/Weapons.kt` to place each weapon in its canonical location for the 12 existing chassis.
- When a location is destroyed, mark all weapons mounted there `destroyed = true` (ties into *Location Destruction Consequences*).
- Keep `PlayerView` weapon/target queries working; expose mount location where the TUI needs it.
- Tests: destroying the right arm disables only right-arm weapons; firing checks still pass for surviving weapons.

## Critical Hit System

Whenever a hit penetrates to **internal structure** (any damage to IS, not just destruction), roll **2d6 for a possible critical hit** against that location:

- 2–7: no critical.
- 8–9: 1 critical slot hit.
- 10–11: 2 critical slots hit.
- 12: 3 critical slots hit (head or limb: the location is blown off entirely).

For each critical, roll to determine **which slot** is struck (1d6 per the location's 6/12-slot layout) and apply the component effect:

- **Engine hit**: +5 heat per turn per hit; 3 engine hits destroy the mech.
- **Gyro hit**: +3 to all Piloting Skill Rolls; 2 hits = gyro destroyed → mech falls and cannot stand.
- **Cockpit / Life Support / Sensors hit**: cockpit hit kills the pilot (mech destroyed); sensors +2 to-hit; life-support causes pilot heat damage.
- **Actuator hit** (shoulder/upper-arm/lower-arm/hand, hip/upper-leg/lower-leg/foot): degrades physical attacks and adds piloting/firing modifiers; hip hit halves leg MP.
- **Weapon/Heat-Sink/Ammo slot hit**: weapon destroyed; heat sink lost (−1 dissipation); **ammo slot hit explodes** (damage = remaining rounds × per-shot damage to that location, then transfers — usually fatal in the CT).

**Current state.** No critical-hit roll, no critical-slot model. `Weapon.destroyed` exists but is never set by combat. `applyDamage` ignores criticals entirely.

**Implementation tasks.**
- Model critical slots per location on `CombatUnit`: e.g. `CriticalSlots(location → List<CriticalSlot>)` where a slot is `Engine`, `Gyro`, `Cockpit`, `Sensors`, `LifeSupport`, `Actuator(kind)`, `HeatSink`, `WeaponSlot(weaponRef)`, `AmmoBin(weaponRef, rounds)`, or `Empty`. Populate from `MechModels.kt`.
- Add a `CriticalHitTable` (2d6 → 0/1/2/3 crits; 12 on limb = location blown off) and a per-location slot-roll resolver.
- Hook critical resolution into `applyDamage`/`resolveAttacks`: when a location takes IS damage, roll for crits and apply each effect (mutating heat-per-turn, gyro modifier, actuator state, destroying weapons/heat sinks, exploding ammo bins).
- Feed engine-hit heat into `HeatGeneration`/heat phase; feed gyro/actuator modifiers into `PilotingSkillRoll` and to-hit calculations.
- Add events: `CriticalHitResolved(unitId, location, slots)`; reuse `AmmoExploded` for ammo-bin cook-off.
- Tests: 8 → 1 crit, 12 on arm → blown off, engine-hit heat accumulation, ammo-bin crit explosion + transfer.

## Location Destruction Consequences

Destroying a location has cascading effects beyond losing its armor/IS: a destroyed **arm or leg** removes all its weapons/actuators (and a destroyed leg forces an immediate fall and halves remaining movement / forces piloting penalties); a destroyed **side torso** also destroys the **arm attached to it** and any weapons/ammo there; a destroyed **leg** means the mech can only "hobble" (1 hex) and must make piloting rolls. These follow from *Damage Transfer*, *Per-Location Weapon Mounting*, and *Critical Hits*.

**Current state.** Only a partial check exists: `PhysicalAttackLimits.kt` blocks physical attacks from a limb whose IS ≤ 0. Nothing disables weapons, drops arms with side torsos, or forces falls on leg loss.

**Implementation tasks.**
- On location destruction (detected in `applyDamage`), mark weapons mounted there `destroyed = true` and remove the location's actuators/heat sinks from play.
- Side-torso destruction → also destroy the same-side arm (and its mounts).
- Leg destruction → trigger a fall via `fall(...)` and apply movement penalties (single-leg: half MP + jumping disabled + piloting modifier; both legs: mech destroyed, handled in *Mech Destruction*).
- Reflect destroyed locations in `PlayerView` so the TUI greys them out and excludes their weapons from `validTargets`/firing.
- Tests: side-torso loss drops the arm; one leg destroyed forces a fall and caps MP; verify weapons in destroyed locations can no longer fire.

## Cluster-Hit Weapons (LRM / SRM / LB-X / Machine-Gun arrays)

Missile and cluster weapons do **not** deal their full damage to one location. On a hit, roll **2d6 on the Cluster Hits Table** for the launcher size to learn how many missiles connect, then apply damage in groups: **SRM = 2 points per missile, each rolled to a separate location**; **LRM = 5-missile groups, 5 points per group, each group to a separate location**. Example: an LRM-20 that rolls "16 missiles hit" deals four 5-point clusters, each on its own hit-location roll.

Cluster Hits Table (2d6 → missiles that hit, columns for common sizes):

| Roll | 2 | 4 | 5 | 6 | 10 | 15 | 20 |
|------|---|---|---|---|----|----|----|
| 2 | 1 | 1 | 1 | 2 | 3 | 5 | 6 |
| 3 | 1 | 2 | 2 | 2 | 3 | 5 | 6 |
| 4 | 1 | 2 | 2 | 3 | 4 | 6 | 9 |
| 5 | 1 | 2 | 3 | 3 | 6 | 9 | 12 |
| 6 | 1 | 2 | 3 | 4 | 6 | 9 | 12 |
| 7 | 1 | 3 | 3 | 4 | 6 | 9 | 12 |
| 8 | 2 | 3 | 3 | 4 | 6 | 9 | 12 |
| 9 | 2 | 3 | 4 | 5 | 8 | 12 | 16 |
| 10 | 2 | 3 | 4 | 5 | 8 | 12 | 16 |
| 11 | 2 | 4 | 5 | 6 | 10 | 15 | 20 |
| 12 | 2 | 4 | 5 | 6 | 10 | 15 | 20 |

**Current state.** `Weapons.kt` models SRM/LRM launchers as single flat-damage weapons (e.g. "SRM 6 = 12 damage" applied to one location). `resolveOneAttack` applies `weapon.damage` to a single `HitLocationTable.roll`.

**Implementation tasks.**
- Extend `Weapon` with a cluster descriptor: `clusterSize: Int?` (missiles per salvo) and `damagePerMissile`/`missilesPerGroup` (SRM: 2 dmg, 1 per group; LRM: 1 dmg, 5 per group). Energy/ballistic weapons keep `clusterSize = null`.
- Add a `ClusterHitsTable` lookup (the table above) keyed by launcher size and 2d6.
- In `resolveOneAttack`/`resolveAttacks`, branch on cluster weapons: roll the cluster table once for the count, split into groups, and roll `HitLocationTable` **per group**, producing multiple `AttackResult`-equivalent location hits for a single declaration.
- Extend `AttackResult` (or add a per-attack list of location hits) so the TUI can show "LRM-20: 12 missiles, 5+5+2 to RT/LA/CT". Keep simultaneous-resolution semantics (roll against original state, apply after).
- Tests: deterministic seeded rolls for SRM-6 and LRM-20 producing the right group counts and per-group locations.

## Target Movement Modifier (TMM) for Weapon Attacks

A target that moved is harder to hit. The attacker adds a to-hit modifier based on **hexes the target moved** this turn: 0–2 hexes = +0, 3–4 = +1, 5–6 = +2, 7–9 = +3, 10–17 = +4, 18–24 = +5, 25+ = +6. A target that **jumped** adds an additional **+1**. This is one of the most impactful core modifiers and is currently absent from gunnery.

**Current state.** Weapon to-hit (`AttackResolution.kt:96-108`) applies only range + heat + secondary + prone/immobile. TMM logic already exists for **physical** attacks (`attack/physical/PhysicalToHit.kt`) and can be reused. `MovementThisTurn` already records mode + `hexesMoved`.

**Implementation tasks.**
- Extract the TMM band lookup from `PhysicalToHit.kt` into a shared function (e.g. `TargetMovementModifier.of(movementThisTurn)`) so weapon and physical paths share one table.
- Add the +1 jumped bonus to the shared function.
- Include the target's TMM in `resolveOneAttack`'s `targetNumber`, and surface it in `AttackResult` (new field) and in `PlayerView.targetInfos` so the TUI shows it pre-commit.
- Tests: target that ran 5 hexes → +2; jumped 4 hexes → +1 +1 = +2; stationary → +0.

## Attacker Movement & Jump To-Hit Modifiers for Weapons

The attacker's own movement adds a to-hit penalty to weapon fire: **stationary +0, walked +1, ran +2, jumped +3**. (Note: this is separate from the heat those movements generate.) Currently weapons ignore attacker movement entirely.

**Current state.** Attacker-movement penalties are applied for **physical** attacks only (`PhysicalToHit.kt`). Weapon to-hit has no attacker-movement term. `attacker.movementThisTurn.mode` is available.

**Implementation tasks.**
- Add an attacker-movement modifier (`+0/+1/+2/+3` for stationary/walk/run/jump) to `resolveOneAttack`'s `targetNumber`, reading `attacker.movementThisTurn.mode`.
- Surface it as a field on `AttackResult` and in `PlayerView.targetInfos`.
- Reuse the existing physical mapping if convenient, but keep weapon values (jump = +3) distinct from any physical-attack values.
- Tests: attacker that ran → +2; jumped → +3; stationary → +0; combined with TMM and range.

## Minimum-Range Penalty

Weapons with a **minimum range** (notably LRMs and the PPC) suffer a to-hit penalty when firing at targets **inside** that minimum range: penalty = `(minimumRange − distance + 1)`. E.g. an LRM (min range 6) firing at a target 2 hexes away takes `6 − 2 + 1 = +5`.

**Current state.** `Weapon.minimumRange` exists (`Weapon.kt:7`) but is never read in to-hit; `InRangeRule` only checks the long-range maximum.

**Implementation tasks.**
- In `resolveOneAttack`, when `distance < weapon.minimumRange`, add `(weapon.minimumRange - distance + 1)` to `targetNumber`.
- Surface as a field on `AttackResult` / `PlayerView.targetInfos`.
- Tests: LRM at distance 2 → +5; PPC (min 3) at distance 1 → +3; at/over min range → +0.

## Terrain & Intervening-Terrain To-Hit and Line of Sight

Woods (and other terrain) affect both **whether** a shot can be drawn and its **to-hit**. Each hex of **intervening light woods** adds **+1**, each **heavy woods +2**; woods in the **target's own hex** add the same. Line of sight is **blocked** when intervening terrain accumulates too much: **2 hexes of intervening woods (any mix where total ≥ 3 "levels", or specifically 2 light / 1 heavy depending on edition)** block LOS entirely. Elevation: higher intervening terrain/units block LOS to lower targets. Partial cover (target's lower body masked by terrain) gives **+3 to-hit** and protects the legs.

**Current state.** `attack/weapon/LineOfSightRule.kt` only checks whether the **target hex itself** is heavy woods (blocks) — it ignores all intervening hexes, intervening light woods, woods to-hit modifiers, elevation, and partial cover. `HexCoordinates.lineTo` already traces the hex line, and `Hex` carries `terrain`/`elevation`.

**Implementation tasks.**
- Build a real LOS/terrain-modifier routine that walks `attacker.position.lineTo(target.position)` (excluding both endpoints), reading each `Hex`'s terrain/elevation from `GameMap`.
- Accumulate woods to-hit modifiers (+1 light, +2 heavy per intervening hex) plus the target hex's own woods; block LOS when accumulated woods reach the blocking threshold or when intervening elevation exceeds both endpoints.
- Add **partial cover** detection (target one level below an intervening obstacle) → +3 to-hit and redirect leg hits to "no effect"/torso per the cover rule.
- Replace `LineOfSightRule`'s simplified check; feed the terrain modifier into `resolveOneAttack`'s `targetNumber` and into `PlayerView.targetInfos`.
- Tests: 1 intervening heavy woods → +2 and still LOS; 2 intervening heavy woods → blocked; partial cover → +3 and legs protected.

## Pilot / MechWarrior Hits & Consciousness

The pilot is a damageable component. The MechWarrior can take **up to 6 hits (6 = dead)**, from: a **head hit** that penetrates to IS (1 hit), **falling** (1 hit per fall), **ammo explosion** (2 hits), **life-support critical** while overheated, etc. After each new hit the pilot must pass a **Consciousness roll** (2d6 vs a target that rises with hits taken: 3+, 5+, 7+, 10+, 11+ for hits 1–5); failure knocks the pilot unconscious (the mech becomes immobile/auto-target until a later consciousness roll succeeds). A dead pilot destroys the mech.

**Current state.** Only `pilotingSkill`/`gunnerySkill` stats exist on `CombatUnit`. There is no pilot-hit count, no consciousness state, no consciousness roll, and head hits / falls / ammo do not injure the pilot.

**Implementation tasks.**
- Add pilot state to `CombatUnit` (or a nested `Pilot`): `hits: Int`, `isConscious: Boolean`, `isDead: Boolean`.
- Add a `consciousnessRoll(pilotHits)` helper (2d6 vs the rising target-number table above) reusing `DiceRoller`.
- Apply pilot hits at the trigger points: head-IS penetration in `AttackResolution.kt`, every `fall(...)` in `Falling.kt`, ammo cook-off in `HeatPhaseHandler`; after each, run the consciousness roll.
- Unconscious pilot → unit cannot move/fire and counts as immobile (reuse the `isShutdown`/immobile target handling, −4 to be hit); attempt to regain consciousness in a later phase.
- 6 hits → pilot dead → mark unit destroyed (ties into *Mech Destruction*).
- Add events `PilotHit(unitId, hits)`, `PilotKnockedOut(unitId)`, `PilotKilled(unitId)`.
- Tests: head IS hit → 1 pilot hit + consciousness roll; two ammo explosions kill the pilot; unconscious unit drops out of impulses.

## Expanded Forced Piloting Skill Rolls (Avoiding Falls)

Beyond kick knockdowns, standard rules force a Piloting Skill Roll (fail → the mech falls) in several situations: taking **20+ total damage in a single phase** (+1 PSR, cumulative per 20), a **leg/foot actuator or hip critical**, a **gyro hit** (+3), losing a **leg**, being **kicked/pushed**, **standing up** in difficult terrain, and **entering certain terrain** at speed. Falls then deal fall damage and may injure the pilot.

**Current state.** PSR exists generically (`pilotingSkill.kt`) and `fall(...)` is reusable, but the **only** trigger wired in is the kick-knockdown path in the physical attack phase. The 20-damage rule, critical-induced PSRs, and leg-loss falls are absent.

**Implementation tasks.**
- Track damage taken per unit per phase (accumulate in `resolveAttacks`); after the weapon/physical phase, for each unit hit for ≥20, roll a PSR with modifier `+1` per full 20 damage; fail → `fall(...)`.
- Wire critical-hit results (gyro +3, actuator/hip, leg destruction) to forced PSRs via the same path.
- Centralise a `forcePsrOrFall(unit, modifier, roller)` helper so all triggers share one implementation and emit `UnitFell`.
- Feed resulting falls into pilot-hit logic (*Pilot Hits*).
- Tests: 20-damage volley forces a PSR; 40 damage → +2; gyro hit forces +3 PSR; pass → no fall, fail → fall + pilot hit.

## Ammunition Consumption per Shot

Ballistic and missile weapons expend **one round (one ton's shot) per fire**; when a weapon's ammo bin is empty it can no longer fire. The remaining ammo per bin also feeds the heat-phase ammo-explosion damage and ammo-criticals.

**Current state.** `Weapon.ammo` is a single nullable `Int` and `HasAmmoRule` checks `> 0`, but **resolution never decrements it** — ammo weapons can fire indefinitely. There is no per-location ammo bin.

**Implementation tasks.**
- Decrement `weapon.ammo` by 1 (or by missiles fired, per edition — standard track is 1 salvo per round) when a weapon successfully fires in `WeaponAttackPhaseHandler`/`resolveAttacks`, regardless of hit/miss.
- Represent ammo as **bins tied to a location** (overlaps with *Per-Location Weapon Mounting* and *Critical Hits*) so ammo criticals and cook-off damage use the correct remaining count and location.
- Ensure the heat-phase explosion (`HeatPhaseHandler`) reads the live remaining rounds.
- Tests: firing an AC/20 N times empties the bin and then `HasAmmoRule` blocks it; remaining count drives explosion damage.

## Standing In / Entering Water & Depth Effects

Water depth affects movement, combat, and heat. **Depth-1 water**: the mech is partially submerged → legs are underwater (partial cover, +3 to be hit to the body, legs protected), and submerged heat sinks dissipate extra heat. **Depth-2+**: the mech is fully submerged → most weapons cannot fire (only those rated for underwater), the pilot risks shutdown/drowning if the cockpit floods, and entering deep water forces a PSR. Falling/destruction in deep water can drown the pilot.

**Current state.** `Hex` carries `waterDepth` and `MovementCost` charges WATER as cost 2, and physical reach rules reference submersion depth — but water has **no** combat, heat, or partial-cover effect, and there is no drowning/underwater-fire restriction.

**Implementation tasks.**
- In the terrain/LOS routine, treat a target standing in depth-1 water as having **partial cover** (+3, legs protected); reuse the partial-cover handling from the LOS section.
- Restrict weapon fire for fully-submerged (depth-2) units to underwater-capable weapons (add an `underwaterCapable`/`weaponClass` flag to `Weapon`; for the standard set, most are surface-only).
- Add submerged heat-sink bonus to dissipation in the heat phase.
- Add drowning: a destroyed/fallen unit in depth-2 water, or a flooded cockpit, kills the pilot over time (ties into *Pilot Hits*).
- Tests: target in depth-1 water gets +3 partial cover; submerged unit cannot fire surface weapons; submerged heat sinks dissipate more.

---

## Suggested implementation order

The features are interdependent; a sensible build order that keeps the game playable at each step:

1. **Damage Transfer Between Locations** — corrects core damage math; small, self-contained.
2. **Mech Destruction & Unit Elimination** + **Victory / End-of-Game Conditions** — makes a game *finishable* (the minimum for a complete game).
3. **Target Movement Modifier**, **Attacker Movement Modifier**, **Minimum-Range Penalty** — cheap, high-impact to-hit corrections sharing the existing physical-attack patterns.
4. **Per-Location Weapon Mounting** → **Critical Hits** → **Location Destruction Consequences** — the big structural block; build the slot model first.
5. **Ammo Consumption**, **Pilot Hits & Consciousness**, **Expanded Forced PSRs** — layer on top of criticals/destruction.
6. **Cluster-Hit Weapons** and **Terrain / Intervening LOS** — independent, can slot in any time after step 3.
7. **Water & Depth Effects** — last; depends on partial-cover LOS and pilot-hit plumbing.
