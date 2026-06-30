# BattleTech Rules Reference — Index

Topic-scoped reference docs for the standard BattleMech game. Use this index to jump straight to the
document (and section) that owns a rule, instead of scanning every file. Each rule has **one canonical
home**; other docs link to it rather than restating it.

## Documents at a glance

| Document | Owns (canonical) | Key tables / lookups |
|---|---|---|
| [`armor-damage.md`](armor-damage.md) | Damage resolution (armor → IS), hit location, **critical hits** & component effects, **damage transfer**, **mech destruction** conditions, **location destruction** consequences | Hit Location Table (Front/Rear/Left/Right), Critical Hit Table (2d6), critical-slot grid, component quick-reference |
| [`heat.md`](heat.md) | Heat generation & dissipation, the heat scale, shutdown, heat-driven penalties; **water heat-sink bonus** | Universal Heat Scale 0–30 (move / to-hit / shutdown / ammo-explosion thresholds) |
| [`to-hit-modifiers.md`](to-hit-modifiers.md) | Weapon to-hit modifiers from movement & range | TMM band table, attacker-movement table, minimum-range penalty formula |
| [`line-of-sight.md`](line-of-sight.md) | LOS tracing, intervening terrain to-hit, LOS blocking, **partial cover** | Light/heavy woods per-hex modifiers, blocking threshold |
| [`cluster-weapons.md`](cluster-weapons.md) | LRM/SRM/cluster damage: missiles-that-hit and grouping | Cluster Hits Table (2d6 × Weapon Size 2–40) |
| [`ammunition.md`](ammunition.md) | Per-shot ammo consumption, per-location ammo bins | — |
| [`pilot.md`](pilot.md) | MechWarrior hits, **consciousness rolls**, **forced Piloting Skill Rolls** | Consciousness target table (rising per hit), forced-PSR trigger list |
| [`water.md`](water.md) | Water depth effects on combat & survival (submersion, drowning) | — |
| [`victory.md`](victory.md) | End-of-game / win conditions, draws | — |

## Find a rule by keyword

| Looking for… | Go to |
|---|---|
| Which location gets hit (2d6 hit location) | [`armor-damage.md`](armor-damage.md) — Hit Location Tables |
| Front vs. rear armor, side-arc limits, natural-2 crit | [`armor-damage.md`](armor-damage.md) — Hit Location Tables |
| Armor → internal structure → blow-through | [`armor-damage.md`](armor-damage.md) §1 |
| Critical hit roll, slot determination, engine/gyro/sensors/life-support/cockpit/actuator/ammo effects | [`armor-damage.md`](armor-damage.md) §3 |
| Damage transfer between locations (blow-through path) | [`armor-damage.md`](armor-damage.md) §5 |
| What destroys a Mech (head/CT/legs/engine/gyro/ammo/pilot) | [`armor-damage.md`](armor-damage.md) §7 |
| Arm dropping with side torso, leg-loss fall + halved MP | [`armor-damage.md`](armor-damage.md) §8 |
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
| Standing/entering water, deep-water fire restriction, drowning | [`water.md`](water.md) |
| When the game ends / who wins / draws | [`victory.md`](victory.md) |

## Notes

- When a rule appears in more than one doc, the non-owning doc links to the canonical home above —
  follow the link rather than trusting a restated number.
