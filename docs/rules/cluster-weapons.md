# BattleTech Tabletop: Cluster-Hit Weapons (LRM / SRM / LB-X / MG Arrays)

Missile and cluster weapons do **not** deliver their full damage to a single location. When such a
weapon hits, the attacker first rolls on the **Cluster Hits Table** to learn how many missiles (or
sub-munitions) actually connect, then applies that damage in **groups**, rolling a separate hit
location for each group.

---

## 1. The Cluster Hits Table

After a launcher scores a hit, roll **2d6** and read the column for the launcher's size to get the
number of missiles that strike:

The column is the **Weapon Size** (the number of missiles / sub-munitions in the full salvo); the row
is the **2d6** roll.

| Roll | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 | 16 | 17 | 18 | 19 | 20 | 21 | 22 | 23 | 24 | 25 | 26 | 27 | 28 | 29 | 30 | 40 |
|:----:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
|  2   | 1 | 1 | 1 | 1 | 2 | 2 | 3 | 3 | 3  | 4  | 4  | 4  | 5  | 5  | 5  | 5  | 6  | 6  | 6  | 7  | 7  | 7  | 8  | 8  | 9  | 9  | 9  | 10 | 10 | 12 |
|  3   | 1 | 1 | 2 | 2 | 2 | 2 | 3 | 3 | 3  | 4  | 4  | 4  | 5  | 5  | 5  | 5  | 6  | 6  | 6  | 7  | 7  | 7  | 8  | 8  | 9  | 9  | 9  | 10 | 10 | 12 |
|  4   | 1 | 1 | 2 | 2 | 3 | 3 | 4 | 4 | 4  | 5  | 5  | 5  | 6  | 6  | 7  | 7  | 8  | 8  | 9  | 9  | 9  | 10 | 10 | 10 | 11 | 11 | 11 | 12 | 12 | 18 |
|  5   | 1 | 2 | 2 | 3 | 3 | 4 | 4 | 5 | 6  | 7  | 8  | 8  | 9  | 9  | 10 | 10 | 11 | 11 | 12 | 13 | 14 | 15 | 16 | 16 | 17 | 17 | 17 | 18 | 18 | 24 |
|  6   | 1 | 2 | 2 | 3 | 4 | 4 | 5 | 5 | 6  | 7  | 8  | 8  | 9  | 9  | 10 | 10 | 11 | 11 | 12 | 13 | 14 | 15 | 16 | 16 | 17 | 17 | 17 | 18 | 18 | 24 |
|  7   | 1 | 2 | 3 | 3 | 4 | 4 | 5 | 5 | 6  | 7  | 8  | 8  | 9  | 9  | 10 | 10 | 11 | 11 | 12 | 13 | 14 | 15 | 16 | 16 | 17 | 17 | 17 | 18 | 18 | 24 |
|  8   | 2 | 2 | 3 | 3 | 4 | 4 | 5 | 5 | 6  | 7  | 8  | 8  | 9  | 9  | 10 | 10 | 11 | 11 | 12 | 13 | 14 | 15 | 16 | 16 | 17 | 17 | 17 | 18 | 18 | 24 |
|  9   | 2 | 2 | 3 | 4 | 5 | 6 | 6 | 7 | 8  | 9  | 10 | 11 | 11 | 12 | 13 | 14 | 14 | 15 | 16 | 17 | 18 | 19 | 20 | 21 | 21 | 22 | 23 | 23 | 24 | 32 |
|  10  | 2 | 3 | 3 | 4 | 5 | 6 | 6 | 7 | 8  | 9  | 10 | 11 | 11 | 12 | 13 | 14 | 14 | 15 | 16 | 17 | 18 | 19 | 20 | 21 | 21 | 22 | 23 | 23 | 24 | 32 |
|  11  | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 | 16 | 17 | 18 | 19 | 20 | 21 | 22 | 23 | 24 | 25 | 26 | 27 | 28 | 29 | 30 | 40 |
|  12  | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 | 16 | 17 | 18 | 19 | 20 | 21 | 22 | 23 | 24 | 25 | 26 | 27 | 28 | 29 | 30 | 40 |

---

## 2. Grouping & Damage

Once you know how many missiles hit, split them into groups and roll a **separate hit location for
each group** (on the standard Hit Location Table — see [`armor-damage.md`](armor-damage.md)):

- **SRM:** 2 points of damage per missile, **1 missile per group** (each missile rolls its own
  location).
- **LRM:** missiles are applied in **5-missile groups**, **5 points of damage per group** (each group
  rolls its own location). A partial final group applies its remaining count × 1 point.

Each group's damage is resolved into armor → internal structure (and transfers) independently, exactly
like a normal weapon hit, and each group that reaches internal structure rolls for its own critical
hit.

---

## 3. Worked Example: LRM-20

An **LRM-20** hits and the attacker rolls **9** on the Cluster Hits Table → **16 missiles hit**.

- 16 missiles ÷ 5 = **four** groups: 5 + 5 + 5 + 1 missiles.
- That is three 5-point clusters and one 1-point cluster, each rolling a separate hit location — e.g.
  5 to the Right Torso, 5 to the Left Arm, 5 to the Center Torso, 1 to the Left Leg.
