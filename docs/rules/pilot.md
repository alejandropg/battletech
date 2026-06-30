# BattleTech Tabletop: MechWarrior Hits, Consciousness & Forced Piloting Rolls

The pilot is a damageable component of the BattleMech. The MechWarrior can absorb a limited number of
**hits** before dying, and after each new hit must stay conscious to keep fighting. Separately, a range
of battlefield events force a **Piloting Skill Roll (PSR)** that, if failed, drops the Mech to the
ground (and often injures the pilot in turn).

---

## 1. Pilot Hits

A MechWarrior can take up to **6 hits — the 6th is fatal**. Hits come from:

- A **head hit** that penetrates to Internal Structure → **1 hit** (see [`armor-damage.md`](armor-damage.md)).
- A **fall** → **1 hit** per fall.
- An **ammo explosion** → **2 hits**.
- A **Life-Support critical** while the Mech is overheated → 1 hit (thresholds in [`armor-damage.md`](armor-damage.md) §3).
- A **Cockpit critical** → the pilot is killed outright (see [`armor-damage.md`](armor-damage.md) §3).

A **dead pilot destroys the Mech** (see *Mech Destruction* in [`armor-damage.md`](armor-damage.md)).

---

## 2. Consciousness Rolls

After each **new** pilot hit, the MechWarrior must make a **Consciousness roll**: roll **2d6** against
a target number that rises with the number of hits already taken:

| Pilot hit # | Consciousness target (2d6) |
|:-----------:|:--------------------------:|
|      1      |             3+             |
|      2      |             5+             |
|      3      |             7+             |
|      4      |            10+             |
|      5      |            11+             |
|      6      |           *dead*           |

**Failure** knocks the pilot unconscious. An unconscious pilot's Mech cannot move or fire and counts
as **immobile** (−4 to be hit, like a shutdown Mech). The pilot may attempt to **regain
consciousness** with a later roll; success returns the Mech to action.

---

## 3. Forced Piloting Skill Rolls (Avoiding Falls)

Beyond kick knockdowns, standard rules force a PSR — fail and the Mech **falls** — in several
situations. A fall deals fall damage and inflicts 1 pilot hit.

- **20+ total damage in a single phase** — make a PSR with a **+1** modifier, **cumulative per full
  20 damage** (40 damage → +2, 60 → +3, …).
- **Gyro critical hit** — **+3** PSR (see [`armor-damage.md`](armor-damage.md) §3 for the ongoing gyro penalties).
- **Leg / foot actuator or hip critical** — forces a PSR.
- **Losing a leg** — forces a fall outright (movement consequences in [`armor-damage.md`](armor-damage.md) §8).
- **Being kicked, pushed, standing up in difficult terrain, or entering certain terrain at speed.**

All of these modifiers combine; resolve the PSR with the summed modifier, and on failure apply the
fall (fall damage → pilot hit → consciousness roll).

**Examples.**
- A 20-damage volley → +1 PSR; a 40-damage volley → +2.
- A gyro hit → +3 PSR; pass = no fall, fail = fall + 1 pilot hit + consciousness roll.
