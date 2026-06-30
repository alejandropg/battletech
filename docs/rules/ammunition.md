# BattleTech Tabletop: Ammunition & Consumption

Ballistic (autocannon, machine gun, Gauss) and missile (LRM, SRM) weapons draw from finite ammunition
carried in **ammo bins**. Each bin holds a fixed number of rounds and occupies critical slots in a
specific location — which is what makes ammunition both exhaustible in combat and dangerous when struck
by a critical hit.

---

## 1. Consumption per Shot

A weapon expends **one round (one "shot" / salvo) every time it fires**, whether the attack hits or
misses. When a weapon's ammo bin is empty, that weapon **can no longer fire** for the rest of the
game (unless it draws from another bin of the same type).

- One trigger pull = one round deducted from the feeding bin.
- An empty bin removes the weapon from the list of legal attacks.

---

## 2. Ammo Bins Are Tied to a Location

Each ammo bin lives in the critical slots of a particular body location (e.g. an LRM ammo bin in the
Right Torso). The bin's location and its **live remaining round count** matter for two things:

1. **Critical hits / cook-off** — if a critical hit strikes an ammo bin, the bin detonates — the
   detonation damage and transfer mechanics are covered in [`armor-damage.md`](armor-damage.md)
   (Critical Hit System), and the resulting pilot injury in [`pilot.md`](pilot.md).
2. **Heat-phase explosion** — the heat-driven ammo explosion likewise reads the **live remaining
   round count**, so a nearly-empty bin does far less damage than a full one. See [`heat.md`](heat.md).

---

## 3. Worked Example: AC/20

An AC/20 ammo bin starts with N rounds.

- Each time the AC/20 fires, the bin drops by 1, regardless of hit or miss.
- After N shots the bin is empty and the AC/20 can no longer fire.
- If that bin is struck by a critical hit while it still holds rounds, it cooks off
  (see [`armor-damage.md`](armor-damage.md)) — the more rounds left, the bigger the blast.
