# BattleTech Rules Reference — Index

Topic-scoped reference docs for the standard BattleMech game. Use this index to jump straight to the
document (and section) that owns a rule, instead of scanning every file. Each rule has **one canonical
home**; other docs link to it rather than restating it.

## Documents at a glance

| Document | Owns (canonical) | Key tables / lookups |
|---|---|---|
| [`armor-damage.md`](armor-damage.md) | Damage resolution (armor → IS), **internal structure by tonnage**, hit location, **damage transfer**, **mech destruction** conditions, **location destruction** consequences | Hit Location Table (Front/Rear/Left/Right), Internal Structure by Tonnage |
| [`critical-hits.md`](critical-hits.md) | The critical hit roll, **which slot/component is hit**, the 78-slot grid, **component destruction effects** (engine/gyro/sensors/life support/cockpit/actuators), **ammo explosions** | Critical Hit Table (2d6), critical-slot grid, component quick-reference |
| [`movement.md`](movement.md) | Movement Phase **sequence & alternating activation**, which units are activated vs. excluded, movement modes & MP, **terrain/elevation move cost**, prone & standing up | Terrain movement-cost table, Walk/Run/Jump MP |
| [`heat.md`](heat.md) | Heat generation & dissipation, the heat scale, shutdown, heat-driven penalties; **water heat-sink bonus** | Universal Heat Scale 0–30 (move / to-hit / shutdown / ammo-explosion thresholds) |
| [`to-hit-modifiers.md`](to-hit-modifiers.md) | Weapon to-hit modifiers from movement & range | TMM band table, attacker-movement table, minimum-range penalty formula |
| [`line-of-sight.md`](line-of-sight.md) | LOS tracing, intervening terrain to-hit, LOS blocking, **partial cover** | Light/heavy woods per-hex modifiers, blocking threshold |
| [`cluster-weapons.md`](cluster-weapons.md) | LRM/SRM/cluster damage: missiles-that-hit and grouping | Cluster Hits Table (2d6 × Weapon Size 2–40) |
| [`ammunition.md`](ammunition.md) | Per-shot ammo consumption, per-location ammo bins | — |
| [`physical-attacks.md`](physical-attacks.md) | Punch & kick: per-turn limits, movement/reach restrictions, kick to-hit modifier, physical damage, **kick knockdown** | Punch Location Table (1d6), Kick Location Table (1d6) |
| [`pilot.md`](pilot.md) | MechWarrior hits, **consciousness rolls**, **forced Piloting Skill Rolls**, **falls** | Consciousness target table (rising per hit), forced-PSR trigger list |
| [`water.md`](water.md) | Water depth effects on combat & survival (submersion, drowning) | — |
| [`victory.md`](victory.md) | End-of-game / win conditions, draws | — |

## Find a rule by keyword

| Looking for… | Go to |
|---|---|
| Which location gets hit (2d6 hit location) | [`armor-damage.md`](armor-damage.md) — Hit Location Tables |
| Front vs. rear armor, side-arc limits, natural-2 crit | [`armor-damage.md`](armor-damage.md) — Hit Location Tables |
| Armor → internal structure → blow-through | [`armor-damage.md`](armor-damage.md) §1 |
| Internal structure points per location by tonnage | [`armor-damage.md`](armor-damage.md) §2 |
| Critical hit roll (2d6), when a crit check is triggered | [`critical-hits.md`](critical-hits.md) §1 |
| Which slot/component is hit, roll-again on empty slots, the 78-slot grid | [`critical-hits.md`](critical-hits.md) §2–3 |
| Engine/gyro/sensors/life-support/cockpit/actuator effects, ammo explosion | [`critical-hits.md`](critical-hits.md) §5 |
| Damage transfer between locations (blow-through path) | [`armor-damage.md`](armor-damage.md) §5 |
| What destroys a Mech (head/CT/legs/engine/gyro/ammo/pilot) | [`armor-damage.md`](armor-damage.md) §7 |
| Arm dropping with side torso, leg-loss fall + halved MP | [`armor-damage.md`](armor-damage.md) §8 |
| Alternating activation, who moves first, impulse order, larger-force ratio | [`movement.md`](movement.md) §1 |
| Which units are activated; can a shutdown / immobile / 0-MP unit move | [`movement.md`](movement.md) §2 |
| Walk vs. run vs. jump MP | [`movement.md`](movement.md) §3 |
| Terrain movement cost, elevation cost to enter a hex | [`movement.md`](movement.md) §4 |
| Prone unit, standing up (stand-up PSR) | [`movement.md`](movement.md) §6 |
| Heat per turn from moving/firing; heat sink dissipation | [`heat.md`](heat.md) §1 |
| Heat penalties (movement −MP, +to-hit, shutdown, ammo cook-off) | [`heat.md`](heat.md) §2 |
| Shutdown effects (immobile, −4 to be hit) | [`heat.md`](heat.md) §3 |
| Heat-sink bonus while standing in water | [`heat.md`](heat.md) §1 |
| Target Movement Modifier (target moved/jumped) | [`to-hit-modifiers.md`](to-hit-modifiers.md) §1 |
| Attacker walked/ran/jumped to-hit penalty | [`to-hit-modifiers.md`](to-hit-modifiers.md) §2 |
| LRM/PPC minimum-range penalty | [`to-hit-modifiers.md`](to-hit-modifiers.md) §3 |
| Woods to-hit, when LOS is blocked, partial cover (+3, legs protected) | [`line-of-sight.md`](line-of-sight.md) |
| How many missiles hit; SRM vs LRM grouping | [`cluster-weapons.md`](cluster-weapons.md) |
| Does a weapon run out of ammo; ammo bins per location | [`ammunition.md`](ammunition.md) |
| Pilot hit counts (head/fall/ammo), consciousness roll, knockout | [`pilot.md`](pilot.md) §1–2 |
| Forced PSR triggers (20+ damage, gyro, leg loss, kick) | [`pilot.md`](pilot.md) §3 |
| Fall damage, facing randomisation after a fall | [`pilot.md`](pilot.md) §4 |
| Punch/kick per-turn limits, which limb, destroyed limb | [`physical-attacks.md`](physical-attacks.md) §1 |
| Can I kick after running/jumping; punch after jumping | [`physical-attacks.md`](physical-attacks.md) §2 |
| Punch/kick elevation and water-depth reach | [`physical-attacks.md`](physical-attacks.md) §3 |
| Kick −2 to-hit, punch/kick damage | [`physical-attacks.md`](physical-attacks.md) §4–5 |
| Punch Location Table, Kick Location Table (1d6) | [`physical-attacks.md`](physical-attacks.md) §6–7 |
| Kick knockdown (who rolls on hit vs. miss) | [`physical-attacks.md`](physical-attacks.md) §8 |
| Standing/entering water, deep-water fire restriction, drowning | [`water.md`](water.md) |
| When the game ends / who wins / draws | [`victory.md`](victory.md) |

## Notes

- When a rule appears in more than one doc, the non-owning doc links to the canonical home above —
  follow the link rather than trusting a restated number.
